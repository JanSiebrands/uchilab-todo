#!/usr/bin/env bash
# S4 screenshots (UCH-65): png-not-blank.py, used by emulator-tasks.sh for every screenshot, must fail for a
# single-coloured PNG (the black frame of a display that is off) and pass for a PNG with content. Both
# PNGs are generated here, in the filter types screencap may use. No emulator needed.
source "$(dirname "$0")/lib.sh"
check="$root/tools/checks/png-not-blank.py"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
# write_png <file> <filter> <pixel of the last row, hex RGBA>: 4x3 RGBA, all black except the last row.
write_png() {
  python3 -I - "$@" <<'PY'
import struct, sys, zlib
path, filt, last = sys.argv[1], int(sys.argv[2]), bytes.fromhex(sys.argv[3])
w, h = 4, 3
rows = [b"\x00\x00\x00\xff" * w] * (h - 1) + [last * w]
raw, prev = b"", bytes(w * 4)
for row in rows:
    if filt == 2:  # Up: difference to the row above
        enc = bytes((x - y) & 0xFF for x, y in zip(row, prev))
    else:
        enc = row
    raw += bytes([filt]) + enc
    prev = row
def chunk(kind, body):
    return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
open(path, "wb").write(png)
PY
}
write_png "$work/black-none.png" 0 000000ff
write_png "$work/black-up.png" 2 000000ff
write_png "$work/content-up.png" 2 ffffffff
expect_red "black screenshot (filter None)" "blank screenshot" python3 -I "$check" "$work/black-none.png"
expect_red "black screenshot (filter Up)" "blank screenshot" python3 -I "$check" "$work/black-up.png"
expect_green "screenshot with content (filter Up)" python3 -I "$check" "$work/content-up.png"
