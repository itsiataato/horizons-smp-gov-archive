#!/usr/bin/env python3
"""Static gate for the GUI render contract.

Scans every render* and draw* method in the client package for the constructs the GUI
specification bans from a render path: allocation (`new`), wrapping, translation,
clock reads and case conversion. The source is stripped of comments and literals first,
so a banned word inside a comment or a UI label does not count.

Exit 0 when clean, 1 when a hit is found. Usage:

    python3 tools/gui-scan.py [--root src/main/java/com/horizonssmp/gov/client]
"""
import argparse
import os
import re
import sys

BANNED = [
    ("new", re.compile(r"\bnew\b")),
    ("font.split", re.compile(r"\.split\s*\(")),
    ("Component.translatable", re.compile(r"\bComponent\s*\.\s*translatable\b")),
    ("Instant", re.compile(r"\bInstant\b")),
    ("toLowerCase", re.compile(r"\.toLowerCase\s*\(")),
    ("toUpperCase", re.compile(r"\.toUpperCase\s*\(")),
    ("String.format", re.compile(r"\bString\s*\.\s*format\b")),
    ("wrap", re.compile(r"\.wrap\s*\(")),
    ("layout", re.compile(r"\blayout\s*\(")),
]
CALL = re.compile(r"(?<![\w$])(render\w*|draw\w*)\s*\(")


def strip_java(text):
    """Blank comments and string/char literals, preserving line count and offsets."""
    out = []
    i = 0
    n = len(text)
    state = None  # None, 'line', 'block', 'str', 'char', 'textblock'
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state is None:
            if ch == "/" and nxt == "/":
                state = "line"
                out.append("  ")
                i += 2
                continue
            if ch == "/" and nxt == "*":
                state = "block"
                out.append("  ")
                i += 2
                continue
            if ch == '"' and text.startswith('"""', i):
                state = "textblock"
                out.append("   ")
                i += 3
                continue
            if ch == '"':
                state = "str"
                out.append(" ")
                i += 1
                continue
            if ch == "'":
                state = "char"
                out.append(" ")
                i += 1
                continue
            out.append(ch)
            i += 1
            continue
        if state == "line":
            if ch == "\n":
                state = None
                out.append("\n")
            else:
                out.append(" ")
            i += 1
            continue
        if state == "block":
            if ch == "*" and nxt == "/":
                state = None
                out.append("  ")
                i += 2
                continue
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue
        if state == "textblock":
            if ch == '"' and text.startswith('"""', i):
                state = None
                out.append("   ")
                i += 3
                continue
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue
        if state == "str":
            if ch == "\\" and nxt:
                out.append("  ")
                i += 2
                continue
            if ch == '"':
                state = None
                out.append(" ")
                i += 1
                continue
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue
        if state == "char":
            if ch == "\\" and nxt:
                out.append("  ")
                i += 2
                continue
            if ch == "'":
                state = None
                out.append(" ")
                i += 1
                continue
            out.append("\n" if ch == "\n" else " ")
            i += 1
            continue
    return "".join(out)


def matching(text, start, opener, closer):
    depth = 0
    i = start
    while i < len(text):
        if text[i] == opener:
            depth += 1
        elif text[i] == closer:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def method_bodies(text):
    """Yield (name, name offset, body) for method declarations named render*/draw*.

    A declaration is a render/draw identifier, a balanced argument list, then an opening
    brace. Calls are followed by `;` or an operator, so they are skipped without needing a
    full Java parser.
    """
    seen = set()
    for match in CALL.finditer(text):
        open_paren = match.end() - 1
        close_paren = matching(text, open_paren, "(", ")")
        if close_paren < 0:
            continue
        cursor = close_paren + 1
        while cursor < len(text) and text[cursor] in " \t\r\n":
            cursor += 1
        if cursor >= len(text) or text[cursor] != "{":
            continue
        if (cursor + 1) in seen:
            continue
        seen.add(cursor + 1)
        end = matching(text, cursor, "{", "}")
        if end < 0:
            continue
        yield match.group(1), match.start(), cursor + 1, text[cursor + 1:end]


def line_of(text, offset):
    return text.count("\n", 0, offset) + 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default="src/main/java/com/horizonssmp/gov/client")
    args = parser.parse_args()
    files = sorted(
        os.path.join(args.root, name)
        for name in os.listdir(args.root)
        if name.endswith(".java")
    )
    hits = 0
    methods = 0
    for path in files:
        with open(path, encoding="utf-8") as handle:
            raw = handle.read()
        stripped = strip_java(raw)
        for name, offset, body_offset, body in method_bodies(stripped):
            methods += 1
            for label, pattern in BANNED:
                for found in pattern.finditer(body):
                    line = line_of(stripped, body_offset + found.start())
                    print("%s:%d: %s in %s: %s" % (
                        path, line, label, name,
                        body[found.start():found.start() + 60].replace("\n", " ")))
                    hits += 1
    print("scanned %d files, %d render methods, %d hits" % (len(files), methods, hits))
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main())
