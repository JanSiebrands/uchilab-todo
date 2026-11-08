#!/usr/bin/env python3
# Test helper of the counterprobes 16o and 16p: writes an invented 40x40 RGBA PNG, light (near white with a
# dark stripe) or dark (near black with a light stripe), so the picture has content and a clear theme.
# dark-keyboard: dark app above a light keyboard on the lower 60 % (like the landscape picture in dark mode).
# light-scrim: light app under the grey scrim of a dialog (brightness 0.388 as measured) with a light dialog.
# Usage: python3 -I png-fixture.py <file.png> <light|dark|dark-keyboard|light-scrim>
import struct
import sys
import zlib

path, theme = sys.argv[1], sys.argv[2]
w = h = 40
bg, fg = (b"\xf5\xf5\xf5\xff", b"\x20\x20\x20\xff") if theme == "light" else (b"\x12\x12\x12\xff", b"\xe0\xe0\xe0\xff")
if theme in ("light", "dark"):
    raw = b"".join(b"\x00" + (fg * w if y in (20, 21) else bg * w) for y in range(h))
elif theme == "dark-keyboard":
    raw = b"".join(b"\x00" + (b"\x12\x12\x12\xff" * w if y < 16 else b"\xe6\xe6\xe6\xff" * w) for y in range(h))
elif theme == "light-scrim":
    raw = b"".join(b"\x00" + (b"\xec\xe6\xf0\xff" * w if 22 <= y < 30 else b"\x63\x63\x63\xff" * w) for y in range(h))
else:
    sys.exit(f"png-fixture: unknown theme {theme}")


def chunk(kind, body):
    return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))


png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
open(path, "wb").write(png + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))
