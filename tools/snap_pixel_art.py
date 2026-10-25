"""Snap upscaled (often AI-generated, slightly blurry) pixel art back to its real pixel grid.

Usage: python -I tools/snap_pixel_art.py <input.png> <grid> <output.png> [max_colors] [x0 y0 x1 y1]

<grid> is how many art pixels span the full image width (e.g. 32 for a 1024px image of 32px cells).
Near-white background becomes transparent, the art is cropped to its content and padded to a square,
and colours are reduced to <max_colors> (default 8) so blur between cells disappears.
An optional crop box (in grid cells, end exclusive) replaces the content crop, so a set of images drawn on the
same grid (e.g. a clover growing from 1 to 4 leaves) keeps the same size and alignment.
"""
import sys

import numpy as np
from PIL import Image

src, grid, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
max_colors = int(sys.argv[4]) if len(sys.argv) > 4 else 8

rgb = np.asarray(Image.open(src).convert("RGB")).astype(np.float64)
h, w, _ = rgb.shape
cw, ch = w / grid, h / grid
cells = np.zeros((grid, grid, 4), dtype=np.uint8)
for gy in range(grid):
    for gx in range(grid):
        # Median of the cell's inner half ignores blurred cell edges.
        x0, x1 = int(gx * cw + cw / 4), int(gx * cw + 3 * cw / 4)
        y0, y1 = int(gy * ch + ch / 4), int(gy * ch + 3 * ch / 4)
        c = np.median(rgb[y0:y1, x0:x1].reshape(-1, 3), axis=0)
        cells[gy, gx] = (*c.astype(np.uint8), 0 if c.min() > 225 else 255)

img = Image.fromarray(cells, "RGBA")
alpha = img.getchannel("A")
box = tuple(int(v) for v in sys.argv[5:9]) if len(sys.argv) > 8 else alpha.getbbox()
img = img.crop(box)
side = max(img.size)
square = Image.new("RGBA", (side, side))
square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))

# Reduce colours on the opaque pixels only, then restore the alpha mask.
mask = square.getchannel("A")
flat = square.convert("RGB").quantize(colors=max_colors, method=Image.Quantize.MEDIANCUT).convert("RGBA")
flat.putalpha(mask)
flat.save(out)
print(f"{out}: {side}x{side}, {len(flat.getcolors(1 << 16) or [])} colours")
