#!/usr/bin/env python3
# Fails (exit 1) if a screenshot does not match the expected theme: median brightness (Rec. 709 luma) of all
# pixels below the status bar (top 5 % left out) above THRESHOLD for light, below it for dark (plan UCH-81
# section 5). Always prints the measured value. Exit 2 for a file that is not a readable 8-bit PNG or a wrong
# argument. Usage: python3 -I png-theme.py <file.png> <light|dark>
# Only the standard library; handles what screencap writes (8 bit, grey/RGB/RGBA, not interlaced).
import struct
import sys
import zlib

THRESHOLD = 0.5
CHANNELS = {0: 1, 2: 3, 4: 2, 6: 4}


def fail(code, message):
    print(f"png-theme: {sys.argv[1]}: {message}", file=sys.stderr)
    sys.exit(code)


def rows(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        fail(2, "not a PNG")
    pos, idat, header = 8, b"", None
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    if header is None:
        fail(2, "no IHDR")
    width, height, depth, colour, _, _, interlace = header
    if depth != 8 or colour not in CHANNELS or interlace != 0:
        fail(2, f"unsupported PNG (depth {depth}, colour type {colour}, interlace {interlace})")
    bpp = CHANNELS[colour]
    stride = width * bpp
    raw = zlib.decompress(idat)
    previous = bytearray(stride)
    for row in range(height):
        start = row * (stride + 1)
        kind, line = raw[start], bytearray(raw[start + 1:start + 1 + stride])
        if kind == 2:
            line = bytearray((x + y) & 0xFF for x, y in zip(line, previous))
        elif kind != 0:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = previous[i]
                if kind == 1:
                    line[i] = (line[i] + a) & 0xFF
                elif kind == 3:
                    line[i] = (line[i] + (a + b) // 2) & 0xFF
                elif kind == 4:
                    c = previous[i - bpp] if i >= bpp else 0
                    p = a + b - c
                    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                    line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
                else:
                    fail(2, f"unknown filter {kind}")
        yield width, height, bpp, row, line
        previous = line


def main():
    if len(sys.argv) != 3 or sys.argv[2] not in ("light", "dark"):
        print("usage: png-theme.py <file.png> <light|dark>", file=sys.stderr)
        sys.exit(2)
    histogram = [0] * 256
    for width, height, bpp, row, line in rows(open(sys.argv[1], "rb").read()):
        if row < height * 5 // 100:
            continue
        if bpp >= 3:
            for i in range(0, width * bpp, bpp):
                histogram[(2126 * line[i] + 7152 * line[i + 1] + 722 * line[i + 2]) // 10000] += 1
        else:
            for i in range(0, width * bpp, bpp):
                histogram[line[i]] += 1
    total, seen, median = sum(histogram), 0, 0
    if total == 0:
        fail(2, "no pixels below the status bar")
    for value, count in enumerate(histogram):
        seen += count
        if seen * 2 >= total:
            median = value
            break
    brightness = median / 255
    expected = sys.argv[2]
    ok = brightness > THRESHOLD if expected == "light" else brightness < THRESHOLD
    message = f"median brightness {brightness:.3f} (threshold {THRESHOLD}, expected {expected})"
    if not ok:
        fail(1, f"wrong theme: {message}")
    print(f"png-theme: {sys.argv[1]}: OK, {message}")


main()
