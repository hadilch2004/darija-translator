#!/usr/bin/env python3
"""Generates the extension icons (red rounded square + green five-pointed star, the Moroccan colours).

Pure standard library, so it runs anywhere:  python3 scripts/make_icons.py
"""
import math
import struct
import sys
import zlib
from pathlib import Path

RED = (193, 39, 45)
GREEN = (0, 98, 51)
SAMPLES = 4  # SAMPLES x SAMPLES supersampling per pixel for smooth edges


def star_polygon(size):
    cx = cy = size / 2
    outer = size * 0.34
    inner = outer * 0.382
    points = []
    for i in range(10):
        radius = outer if i % 2 == 0 else inner
        angle = -math.pi / 2 + i * math.pi / 5
        points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return points


def in_polygon(x, y, poly):
    inside = False
    j = len(poly) - 1
    for i, (xi, yi) in enumerate(poly):
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def in_rounded_square(x, y, size):
    radius = size * 0.22
    nx = min(max(x, radius), size - radius)
    ny = min(max(y, radius), size - radius)
    return (x - nx) ** 2 + (y - ny) ** 2 <= radius ** 2


def render(size):
    star = star_polygon(size)
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            r = g = b = a = 0
            for sy in range(SAMPLES):
                for sx in range(SAMPLES):
                    x = px + (sx + 0.5) / SAMPLES
                    y = py + (sy + 0.5) / SAMPLES
                    if not in_rounded_square(x, y, size):
                        continue
                    colour = GREEN if in_polygon(x, y, star) else RED
                    r += colour[0]
                    g += colour[1]
                    b += colour[2]
                    a += 255
            n = SAMPLES * SAMPLES
            covered = a // 255
            if covered:
                row += bytes((r // covered, g // covered, b // covered, a // n))
            else:
                row += bytes((0, 0, 0, 0))
        rows.append(bytes(row))
    return rows


def write_png(path, size):
    raw = b"".join(b"\x00" + row for row in render(size))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    Path(path).write_bytes(png)


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "chrome-extension" / "icons")
    out.mkdir(parents=True, exist_ok=True)
    for s in (16, 48, 128):
        write_png(out / f"icon{s}.png", s)
        print(f"wrote {out / f'icon{s}.png'}")
