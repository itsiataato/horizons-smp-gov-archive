#!/usr/bin/env bash
# Gov M10a acceptance harness.
#
# One command, one isolated dedicated server, one transcript:
#   - builds the jar with Gradle;
#   - starts a dedicated server in run/build/gov-acceptance/server (never run/world);
#   - waits for "Done", checks the startup contract and the dispatcher counts;
#   - runs the full non-GUI command matrix over RCON;
#   - checks expected substrings, asserts no ERROR/FATAL, stops cleanly;
#   - exits 0 only if every gate passes.
#
# Safe to rerun. It never commits a live season reset, never deletes a world or an export,
# and never writes outside the acceptance directory (except Gradle's own build outputs).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${GOV_ACCEPTANCE_DIR:-$ROOT/run/build/gov-acceptance}"
# Resolve the scratch directory before anything is created. `..` or an absolute path in
# GOV_ACCEPTANCE_DIR must not be able to point the scratch server at the live run directory.
# GOV_ACCEPTANCE_ALLOW_OUTSIDE=1 is the explicit escape hatch for a CI scratch disk.
OUT="$(python3 - "$ROOT" "$OUT" "${GOV_ACCEPTANCE_ALLOW_OUTSIDE:-0}" <<'PY'
import os, sys
root, out, allow = os.path.realpath(sys.argv[1]), os.path.realpath(sys.argv[2]), sys.argv[3]
allowed = os.path.join(root, "run", "build")
if not out.startswith(allowed + os.sep) and allow != "1":
    print("GOV_ACCEPTANCE_DIR must be inside %s (got %s); set "
          "GOV_ACCEPTANCE_ALLOW_OUTSIDE=1 to override" % (allowed, out), file=sys.stderr)
    raise SystemExit(1)
print(out)
PY
)" || exit 1
SERVER_DIR="$OUT/server"
# Even with the escape hatch, a server rooted in run/ outside run/build/ could open the live
# world. Refuse that combination outright; there is no legitimate reason for it.
python3 - "$ROOT" "$SERVER_DIR" <<'PY'
import os, sys
root, server = os.path.realpath(sys.argv[1]), os.path.realpath(sys.argv[2])
live = os.path.join(root, "run")
build = os.path.join(live, "build")
if server.startswith(live + os.sep) and not server.startswith(build + os.sep):
    print("refusing acceptance server inside the live run directory: %s" % server, file=sys.stderr)
    raise SystemExit(1)
PY
STAMP="$(date -u +%Y%m%d-%H%M%S)"
TRANSCRIPT="$OUT/transcript-$STAMP.txt"
BUILD_LOG="$OUT/build-$STAMP.log"
SERVER_LOG="$OUT/server-$STAMP.log"
GAME_PORT="${GOV_ACCEPTANCE_GAME_PORT:-25595}"
RCON_PORT="${GOV_ACCEPTANCE_RCON_PORT:-25596}"
RCON_PASSWORD="${GOV_ACCEPTANCE_RCON_PASSWORD:-changeme-localhost-only}"
JAR="$ROOT/build/libs/gov-1.0.1.jar"
MIN_ASSERTIONS=3005
GRADLE_PID=""
SERVER_STARTED=0

mkdir -p "$OUT" "$SERVER_DIR"
# Everything the script prints is also the transcript a human can attach to a report.
exec > >(tee -a "$TRANSCRIPT") 2>&1

say() { printf '\n== %s ==\n' "$*"; }
fail() { printf '\nFAIL: %s\n' "$*" >&2; exit 1; }

cleanup() {
    # The stop path is the normal one. This fallback only handles an aborted run; it sends RCON
    # stop if the server is up, then waits briefly and terminates our own Gradle process.
    if [ "$SERVER_STARTED" = "1" ] && [ -n "$GRADLE_PID" ]; then
        python3 - "$RCON_PORT" "$RCON_PASSWORD" <<'PY' >/dev/null 2>&1 || true
import socket, struct, sys
port, password = int(sys.argv[1]), sys.argv[2]
def pkt(i, t, body):
    b = body.encode() + b"\x00\x00"
    return struct.pack("<ii", len(b) + 8, i) + struct.pack("<i", t) + b
try:
    s = socket.create_connection(("127.0.0.1", port), 3)
    s.settimeout(3)
    s.sendall(pkt(1, 3, password))
    s.recv(4096)
    s.sendall(pkt(2, 2, "stop"))
    s.recv(4096)
    s.close()
except Exception:
    pass
PY
        for _ in $(seq 1 30); do
            kill -0 "$GRADLE_PID" 2>/dev/null || break
            sleep 1
        done
        if kill -0 "$GRADLE_PID" 2>/dev/null; then
            kill "$GRADLE_PID" 2>/dev/null || true
        fi
    fi
}
trap cleanup EXIT

say "Gov acceptance: build"
cd "$ROOT"
if ! gradle build --console=plain >"$BUILD_LOG" 2>&1; then
    tail -80 "$BUILD_LOG"
    fail "gradle build failed (see $BUILD_LOG)"
fi
tail -6 "$BUILD_LOG"
[ -f "$JAR" ] || fail "expected jar not found: $JAR"
JAR_BYTES="$(stat -c %s "$JAR")"
JAR_SHA="$(sha256sum "$JAR" | awk '{print $1}')"
printf 'jar: %s\nbytes: %s\nsha256: %s\n' "$JAR" "$JAR_BYTES" "$JAR_SHA"

# Classpath isolation gate: no common/server class may name a client class, directly or through
# its constant pool. The client package may reference the server side, but never the reverse: a
# server install must be able to load every non-client class without the client distribution.
say "Gov acceptance: bytecode isolation gate"
python3 - "$JAR" <<'PY'
import sys, zipfile

def utf8_constants(blob):
    if blob[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    count = int.from_bytes(blob[8:10], "big")
    i, index, out = 10, 1, []
    while index < count:
        tag = blob[i]
        i += 1
        if tag == 1:
            length = int.from_bytes(blob[i:i + 2], "big")
            i += 2
            out.append(blob[i:i + length])
            i += length
        elif tag in (7, 8, 16, 19, 20):
            i += 2
        elif tag == 15:
            i += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            i += 4
        elif tag in (5, 6):
            i += 8
            index += 1
        else:
            raise ValueError("unknown constant-pool tag %d" % tag)
        index += 1
    return out

forbidden = (b"com/horizonssmp/gov/client/", b"net/minecraft/client/")
scanned = 0
violations = []
with zipfile.ZipFile(sys.argv[1]) as jar:
    for name in jar.namelist():
        if not name.endswith(".class") or name.startswith("com/horizonssmp/gov/client/"):
            continue
        scanned += 1
        for value in utf8_constants(jar.read(name)):
            for needle in forbidden:
                if needle in value:
                    violations.append("%s -> %s" % (name, value.decode("utf-8", "replace")))
if scanned == 0:
    raise SystemExit("bytecode gate scanned no classes")
if violations:
    print("client references from common/server classes:")
    for line in violations[:20]:
        print(" - " + line)
    raise SystemExit(1)
print("bytecode gate: %d common classes, 0 client references" % scanned)
PY

say "Gov acceptance: isolated server directory"
printf 'server: %s\n' "$SERVER_DIR"
printf 'game port: %s\nrcon port: %s\n' "$GAME_PORT" "$RCON_PORT"

# Refuse to start if another listener already owns either port. This is a preflight check, not a
# destructive probe; it connects and closes.
if ! python3 - "$GAME_PORT" "$RCON_PORT" <<'PY'
import socket, sys
for raw in sys.argv[1:]:
    port = int(raw)
    s = socket.socket()
    s.settimeout(0.5)
    if s.connect_ex(("127.0.0.1", port)) == 0:
        print("port already in use:", port)
        s.close()
        raise SystemExit(1)
    s.close()
PY
then
    fail "a required port is already in use"
fi

if [ ! -f "$SERVER_DIR/eula.txt" ]; then
    printf 'eula=true\n' >"$SERVER_DIR/eula.txt"
fi

# Fixed properties, rewritten every run so the harness is self-contained. These are scratch
# server files under run/build, never the developer's run/server.properties.
cat >"$SERVER_DIR/server.properties" <<EOF
accepts-transfers=false
allow-flight=false
allow-nether=true
broadcast-console-to-ops=true
broadcast-rcon-to-ops=false
difficulty=easy
enable-command-block=false
enable-query=false
enable-rcon=true
enable-status=true
enforce-secure-profile=false
enforce-whitelist=false
force-gamemode=false
function-permission-level=2
gamemode=survival
generate-structures=false
generator-settings={}
hardcore=false
initial-enabled-packs=vanilla
level-name=world
level-type=minecraft\:normal
max-players=5
motd=Gov acceptance
network-compression-threshold=256
online-mode=false
op-permission-level=4
player-idle-timeout=0
pvp=true
rate-limit=0
rcon.password=$RCON_PASSWORD
rcon.port=$RCON_PORT
require-resource-pack=false
server-port=$GAME_PORT
simulation-distance=4
spawn-animals=true
spawn-monsters=true
spawn-npcs=true
spawn-protection=0
sync-chunk-writes=true
use-native-transport=true
view-distance=4
white-list=false
EOF
