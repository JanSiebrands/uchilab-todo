#!/usr/bin/env python3
# Fails (exit 1) if a PNG screenshot shows one single colour, e.g. the black frames screencap returns when the
# display is off or nothing is rendered. Exit 2 for a file that is not a readable 8-bit PNG.
# Usage: python3 -I png-not-blank.py <file.png>
# Only the standard library; handles what screencap writes (8 bit, grey/RGB/RGBA, not interlaced).
import struct
import sys
import zlib

CHANNELS = {0: 1, 2: 3, 4: 2, 6: 4}


def fail(code, message):
    print(f"png-not-blank: {sys.argv[1]}: {message}", file=sys.stderr)
    sys.exit(code)


def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def main():
    data = open(sys.argv[1], "rb").read()
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
    first = None
    for row in range(height):
        start = row * (stride + 1)
        kind, line = raw[start], bytearray(raw[start + 1:start + 1 + stride])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = previous[i]
            c = previous[i - bpp] if i >= bpp else 0
            if kind == 1:
                line[i] = (line[i] + a) & 0xFF
            elif kind == 2:
                line[i] = (line[i] + b) & 0xFF
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif kind == 4:
                line[i] = (line[i] + paeth(a, b, c)) & 0xFF
            elif kind != 0:
                fail(2, f"unknown filter {kind}")
        if first is None:
            first = bytes(line[:bpp])
        if line != bytearray(first * width):
            print(f"png-not-blank: {sys.argv[1]}: OK ({width}x{height}, more than one colour)")
            return
        previous = line
    fail(1, f"blank screenshot: all {width}x{height} pixels have the colour {first.hex()}")


main()
