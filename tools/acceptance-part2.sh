
say "Gov acceptance: start dedicated server"
SERVER_STARTED=1
gradle runServer -PgovRunDir="$SERVER_DIR" --console=plain >"$SERVER_LOG" 2>&1 &
GRADLE_PID=$!
printf 'gradle pid: %s\nserver log: %s\n' "$GRADLE_PID" "$SERVER_LOG"

# Wait for the server's own readiness line. The RCON connect loop below is the second gate: a
# server that logs Done but never opens RCON cannot run this matrix.
READY=0
for _ in $(seq 1 240); do
    if grep -q 'Done (' "$SERVER_LOG" 2>/dev/null; then
        READY=1
        break
    fi
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        tail -80 "$SERVER_LOG"
        fail "server process exited before Done"
    fi
    sleep 1
done
[ "$READY" = "1" ] || { tail -80 "$SERVER_LOG"; fail "server did not reach Done within 240s"; }
grep -E 'Done \(|RCON running|startup contract check|dispatcher ready' "$SERVER_LOG" | tail -8

# RCON command matrix. The framing is exact, not a quiet-window read:
#   size field = body + id + type + 2 NULs (does NOT count the 4-byte size field)
#   read 4 bytes, then read exactly size bytes; body = raw[8:size-2] of that packet.
# See ../tips/t58-rcon-body-slice-off-by-four.md.
say "Gov acceptance: RCON matrix"
python3 - "$RCON_PORT" "$RCON_PASSWORD" <<'PY'
import socket, struct, sys, time

port, password = int(sys.argv[1]), sys.argv[2]

def packet(i, t, body):
    payload = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<ii", len(payload) + 8, i) + struct.pack("<i", t) + payload

def recv_exact(sock, count):
    out = b""
    while len(out) < count:
        chunk = sock.recv(count - len(out))
        if not chunk:
            raise EOFError("RCON connection closed")
        out += chunk
    return out

def read_packet(sock):
    size = struct.unpack("<i", recv_exact(sock, 4))[0]
    if size < 10:
        raise ValueError("invalid RCON packet size " + str(size))
    raw = recv_exact(sock, size)
    request_id, packet_type = struct.unpack("<ii", raw[:8])
    body = raw[8:size - 2].decode("utf-8", "replace")
    return request_id, packet_type, body

def connect():
    deadline = time.time() + 240
    last = None
    while time.time() < deadline:
        try:
            sock = socket.create_connection(("127.0.0.1", port), 2)
            sock.settimeout(15)
            sock.sendall(packet(1, 3, password))
            request_id, _, _ = read_packet(sock)
            if request_id == -1:
                raise RuntimeError("RCON authentication failed")
            return sock
        except Exception as exc:
            last = exc
            time.sleep(1)
    raise SystemExit("RCON not ready after 240s: %s" % last)

def run(sock, index, command):
    sock.sendall(packet(index, 2, command))
    request_id, packet_type, body = read_packet(sock)
    if request_id != index or packet_type != 0:
        raise SystemExit("bad RCON reply for %s: id=%s type=%s" % (command, request_id, packet_type))
    return body

# Each case is (label, command, groups); every group must match at least one alternative.
cases = [
    ("root usage", "/gov", [["Usage: /gov law"]]),
    ("law console refusal", "/gov law", [["/gov law can only be used by a player."]]),
    ("dashboard console refusal", "/gov dashboard",
     [["/gov dashboard can only be used by a player."]]),
    ("self card console refusal", "/gov me", [["/gov me can only be used by a player."]]),
    ("M2 job roster", "/gov job list",
     [["Government jobs:"], ["[built-in] citizen"], ["[template] govworker"]]),
    ("M2 job detail", "/gov job info citizen",
     [["Job: Citizen (citizen)"], ["Capabilities ("]]),
    ("M2 job caps", "/gov job caps citizen", [["Capabilities of 'citizen'"]]),
    ("M3 person lookup", "/gov person Steve",
     [["Government record: Steve", "There is no person 'Steve'."]]),
    ("M3 note list", "/gov note list Steve",
     [["Notes on Steve", "There is no person 'Steve'."]]),
    ("M4 fine list", "/gov fine list",
     [["Government fines (all fines):"], ["(none)", "UNPAID", "PAID", "WAIVED"]]),
    ("M5 vehicle list", "/gov vehicle list", [["Vehicle registry (all vehicles):"]]),
    ("M5 console plate scan", "/gov vehicle scan ABC-123", [["Plate scan ABC123:"]]),
    ("M6 weapon list", "/gov weapon list", [["Weapon registry (all weapons):"]]),
    ("M6 classifier", "/gov weapon classify minecraft:tnt",
     [["Classification of minecraft:tnt: BLASTING"], ["Source: json"]]),
    ("M6 permit list", "/gov weapon permit list Steve",
     [["Permits for Steve:", "There is no person 'Steve'."]]),
    ("M6 area registry", "/gov area permit list", [["Explosion-area permits:"]]),
    ("M7 business list", "/gov business list", [["Business registry (all businesses):"]]),
    ("M8 detention list", "/gov jail list", [["Detentions:"], ["active"]]),
    ("M9 audit list", "/gov audit list", [["Audit log:"], ["retained"]]),
    ("M9 audit export", "/gov audit export",
     [["Export written:"], ["Section: audit"], ["sha256:"]]),
    ("M9 audit detail", "/gov audit info 1", [["Audit entry #"]]),
    ("M9 audit action filter", "/gov audit list action AUDIT_EXPORT", [["AUDIT_EXPORT"]]),
    ("M9 bogus section", "/gov admin export bogus",
     [["Unknown export section 'bogus'"]]),
    ("M9 season status", "/gov admin season status",
     [["Season reset plan"], ["Clear:"], ["Keep:"], ["Directories: gov-exports/"]]),
    ("M9 bogus token", "/gov admin season reset commit not-a-token",
     [["That token is unknown, already used or expired."]]),
    ("M9 season begin", "/gov admin season reset begin",
     [["Season reset begun."], ["Token: "], ["Sections: licenses,vehicles,weapons"]]),
    ("M9 bogus token after begin", "/gov admin season reset commit not-a-token",
     [["That token is unknown, already used or expired."]]),
]

sections = ["audit", "players", "jobs", "fines", "notes", "businesses",
            "vehicles", "weapons", "permits", "area-permits", "detentions"]
for section in sections:
    cases.append(("M9 admin export " + section, "/gov admin export " + section,
                  [["Export written:"], ["Section: " + section], ["sha256:"]]))

failures = []
sock = connect()
print("RCON connected: %s" % port)
for index, (label, command, groups) in enumerate(cases, start=2):
    body = run(sock, index, command)
    print("\n--- %s: %s" % (label, command))
    print(body.rstrip())
    for alternatives in groups:
        if not any(needle in body for needle in alternatives):
            failures.append("%s: expected one of %r" % (label, alternatives))

# Stop last, after every assertion. A failed case must still stop the server cleanly.
stop_index = len(cases) + 10
try:
    print("\n--- stop")
    print(run(sock, stop_index, "stop").rstrip())
except Exception as exc:
    print("stop reply: %s" % exc)
sock.close()

if failures:
    print("\nFAILURES:")
    for failure in failures:
        print(" - " + failure)
    raise SystemExit(1)
print("\nALL COMMAND ASSERTIONS PASSED: %d cases" % len(cases))
PY

# Wait for the Gradle task and for the server's own shutdown lines. No destructive signal is
# needed on the normal path: "stop" is the same path an operator uses.
say "Gov acceptance: clean shutdown"
SHUTDOWN=0
for _ in $(seq 1 120); do
    if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
        SHUTDOWN=1
        break
    fi
    sleep 1
done
if [ "$SHUTDOWN" = "0" ]; then
    fail "server did not exit within 120s of stop"
fi
wait "$GRADLE_PID" || fail "gradle runServer exited non-zero"
SERVER_STARTED=0

# Log gates. The startup contract line is authoritative for the assertion count; the dispatcher
# line is authoritative for the opcode/handler coverage.
ASSERTIONS="$(sed -n 's/.*startup contract check passed: \([0-9][0-9]*\) assertions.*/\1/p' "$SERVER_LOG" | tail -1)"
[ -n "$ASSERTIONS" ] || fail "startup contract check did not report a passing assertion count"
[ "$ASSERTIONS" -ge "$MIN_ASSERTIONS" ] || fail "startup contract has $ASSERTIONS assertions, need >= $MIN_ASSERTIONS"
grep -q "dispatcher ready: 122 action codes mapped to capabilities, 118 handlers registered" "$SERVER_LOG" \
    || fail "opcode/handler coverage line is not 122/118"
grep -q 'Done (' "$SERVER_LOG" || fail "server never reached Done"
grep -q 'All dimensions are saved' "$SERVER_LOG" || fail "world did not save on shutdown"
grep -q 'Thread RCON Listener stopped' "$SERVER_LOG" || fail "RCON listener did not stop"
grep -q 'BUILD SUCCESSFUL' "$SERVER_LOG" || fail "Gradle runServer did not finish successfully"

if grep -E '/(ERROR|FATAL)\]' "$SERVER_LOG" >/dev/null; then
    echo "ERROR/FATAL lines found:"
    grep -E '/(ERROR|FATAL)\]' "$SERVER_LOG" | tail -20
    fail "server log contains ERROR or FATAL"
fi

DAT="$SERVER_DIR/world/data/gov.dat"
[ -f "$DAT" ] || fail "gov.dat was not created in the isolated world"
printf '\ngov.dat: %s (%s bytes)\n' "$DAT" "$(stat -c %s "$DAT")"

say "PASS"
printf 'assertions: %s\n' "$ASSERTIONS"
printf 'jar: %s (%s bytes, sha256 %s)\n' "$JAR" "$JAR_BYTES" "$JAR_SHA"
printf 'transcript: %s\n' "$TRANSCRIPT"
printf 'server log: %s\n' "$SERVER_LOG"
exit 0
