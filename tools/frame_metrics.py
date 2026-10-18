#!/usr/bin/env python3
"""Minimal PNG reader + crude visual-anomaly metrics (no third-party deps).

Used to spot frames where a client module blows up the world render (giant
triangles, full-screen fills, black seam lines) while a stress run toggles
modules one at a time.
"""
import sys, zlib, struct, os, json, glob


def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a png"
    pos, idat, width = 8, b"", None
    while pos < len(data):
        length, ctype = struct.unpack_from(">I4s", data, pos)
        pos += 8
        chunk = data[pos:pos + length]
        pos += length + 4
        if ctype == b"IHDR":
            width, height, depth, color, _c, _f, interlace = struct.unpack(">IIBBBBB", chunk)
            assert depth == 8 and interlace == 0, f"unsupported png depth={depth} interlace={interlace}"
        elif ctype == b"IDAT":
            idat += chunk
        elif ctype == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    raw = zlib.decompress(idat)
    stride = width * channels
    out = bytearray(width * height * channels)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if f == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return width, height, channels, out


def metrics(path, step=4):
    width, height, ch, px = read_png(path)
    dark = 0
    total = 0
    buckets = {}
    for y in range(0, height, step):
        row = y * width * ch
        for x in range(0, width, step):
            i = row + x * ch
            r, g, b = px[i], px[i + 1], px[i + 2]
            total += 1
            if r < 40 and g < 40 and b < 40:
                dark += 1
            key = (r >> 4) << 8 | (g >> 4) << 4 | (b >> 4)
            buckets[key] = buckets.get(key, 0) + 1
    top = max(buckets.values()) / total
    return {
        "file": os.path.basename(path),
        "w": width, "h": height,
        "dark": round(dark / total, 4),
        "top": round(top, 4),
        "colors": len(buckets),
    }


def main():
    rows = [metrics(p) for p in sorted(glob.glob(sys.argv[1] if len(sys.argv) > 1 else "/tmp/frames/*.png"))]
    for row in rows:
        print(f"{row['file']:>22}  dark={row['dark']:.4f}  top={row['top']:.4f}  colors={row['colors']}")
    if rows:
        with open("/tmp/frames/metrics.json", "w") as fh:
            json.dump(rows, fh)


if __name__ == "__main__":
    main()
