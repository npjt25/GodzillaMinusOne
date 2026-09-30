"""Builds the atomic blast's sprite sheet, textures/effect/nuclear_cloud.png, from Alex's Caves' mushroom cloud
textures (tools/reference/alexs_caves/mushroom_cloud.png and mushroom_cloud_glow.png, by AlexModGuy, GNU LGPL).

    python3 tools/make_cloud_sprites.py [preview.png]

client/render/NuclearCloud.java builds the cloud out of camera-facing puffs of smoke, each one of these sprites, and
draws it in two passes, as Alex's Caves draws its cloud in layers: the smoke (the normal atlas's billows, made grey so
each puff can be tinted: charcoal, grey, dirty brown, lit from above, dark underneath) and, added on top where it's
still hot, the fire inside it (the glow atlas's billows, cut to the same shape).

A 256 x 256 sheet of 64 x 64 cells, in rows of four (the cell numbers are NuclearCloud's sprite indices):

     0- 3  smoke billows: clumps of round billows, shaded like the source's (lit from above, darker creases where
           one billow overlaps another), textured with its packed billows (the upper cap's top face)
     4- 7  the fire glowing inside each of them: the glow atlas's billows over the same shapes, brightest low down
     8     dust: a low, wide clump, for the ground-hugging dust and haze
     9     a wisp: a thin, broken clump, for the cloud's last scattered smoke
    10     a taller clump of billows, for the rising column
    11     ...and the fire inside it
    12     the fireball: the glow atlas's white-hot burst (the cap's underside), cut round and ragged
    13     the flash: a soft white glare, yellowing towards its edge
    14     the fireball's cooler outer fire (the low cloud's underside, from the normal atlas)
    15     (unused)

Everything keeps the source's look: its pixels are copied at their own size (64 px across a cell) and the shading is
stepped into a few flat shades, as it's drawn.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(ROOT, "tools", "reference", "alexs_caves")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures", "effect", "nuclear_cloud.png")
CELL = 64

# Regions of the source's 512 x 512 atlases (MushroomCloudModel's texture offsets and box sizes).
UPPER_CLOUD1_TOP = (64, 220, 128, 284)     # upperCloud1 (64 x 15 x 64 at 0,220): its top face, packed round billows
LOWER_CLOUD_TOP = (100, 0, 200, 100)       # lowerCloud (100 x 16 x 100 at 0,0): its top face, billows round a hub
UPPER_CLOUD_BOTTOM = (160, 116, 240, 196)  # upperCloud (80 x 24 x 80 at 0,116): its underside, a burst
LOWER_CLOUD_BOTTOM = (200, 0, 300, 100)    # lowerCloud's underside


def load(name):
    return np.array(Image.open(os.path.join(REF, name)).convert("RGBA")).astype(np.float64) / 255.0


def region(img, box, size=None):
    x0, y0, x1, y1 = box
    r = img[y0:y1, x0:x1]
    if size is not None and r.shape[:2] != (size, size):
        r = np.array(Image.fromarray((r * 255).astype(np.uint8)).resize((size, size), Image.NEAREST)).astype(np.float64) / 255.0
    return r


def luminance(rgb):
    return rgb[..., 0] * 0.299 + rgb[..., 1] * 0.587 + rgb[..., 2] * 0.114


def normalized(values, mask=None):
    v = values if mask is None else values[mask]
    lo, hi = np.percentile(v, 3), np.percentile(v, 97)
    return np.clip((values - lo) / max(hi - lo, 1e-6), 0.0, 1.0)


def quantize(x, levels):
    return np.round(np.clip(x, 0.0, 1.0) * (levels - 1)) / (levels - 1)


YY, XX = np.mgrid[0:CELL, 0:CELL].astype(np.float64) + 0.5


def billows(circles, detail, light=(-0.45, -0.75, 0.5)):
    """A clump of round billows: circles (x, y, r, z) in pixels, later ones in front of earlier ones.

    Returns (coverage 0-1, grey shade 0-1, which billow is in front, how far down the clump each pixel is)."""
    lx, ly, lz = np.array(light) / np.linalg.norm(light)
    best = np.full((CELL, CELL), -1e9)
    front = np.full((CELL, CELL), -1)
    nx = np.zeros((CELL, CELL))
    ny = np.zeros((CELL, CELL))
    nz = np.zeros((CELL, CELL))
    for i, (cx, cy, r, z) in enumerate(circles):
        d = np.hypot(XX - cx, YY - cy)
        inside = d < r
        h = np.sqrt(np.clip(r * r - d * d, 0.0, None)) + z
        take = inside & (h > best)
        best[take] = h[take]
        front[take] = i
        nx[take] = ((XX - cx) / r)[take]
        ny[take] = ((YY - cy) / r)[take]
        nz[take] = (np.sqrt(np.clip(1.0 - (d / r) ** 2, 0.0, None)))[take]
    covered = front >= 0
    # how far inside the clump's outline each pixel is: a soft pixel along the edge
    inside_dist = np.full((CELL, CELL), 0.0)
    for cx, cy, r, z in circles:
        inside_dist = np.maximum(inside_dist, r - np.hypot(XX - cx, YY - cy))
    coverage = np.clip(inside_dist / 1.6, 0.0, 1.0)
    diffuse = np.clip(nx * lx + ny * ly + nz * lz, 0.0, 1.0)
    shade = 0.5 + 0.5 * diffuse
    # the creases: where a billow in front meets the edge of its own circle over another one behind it
    crease = np.zeros((CELL, CELL))
    for i, (cx, cy, r, z) in enumerate(circles):
        rim = (front == i) & (np.hypot(XX - cx, YY - cy) > r - 1.6)
        others = np.zeros((CELL, CELL), bool)
        for j, (ox, oy, orr, oz) in enumerate(circles):
            if j != i:
                others |= np.hypot(XX - ox, YY - oy) < orr + 0.5
        crease[rim & others] = 1.0
    shade = shade * (1.0 - 0.28 * crease)
    shade = shade * (0.86 + 0.28 * (detail - 0.5))
    lowness = np.clip((YY - min(c[1] - c[2] for c in circles)) / max(1.0, (max(c[1] + c[2] for c in circles) - min(c[1] - c[2] for c in circles))), 0, 1)
    return np.where(covered, coverage, 0.0), np.where(covered, shade, 0.0), lowness


def clump(rng, spread=(1.0, 1.0), count=(6, 9), radius=(9.0, 15.0), core=19.0, centre=(32.0, 33.0), bias_up=0.35):
    """A random clump of billows: one big one in the middle, smaller ones round it, more of them on top."""
    circles = [(centre[0] + rng.uniform(-1.5, 1.5), centre[1] + rng.uniform(-1.0, 2.0), core, 0.0)]
    n = rng.integers(count[0], count[1] + 1)
    for k in range(n):
        a = (k + rng.uniform(-0.35, 0.35)) / n * 2 * math.pi
        up = -math.sin(a)          # screen y points down: up > 0 on the top half
        dist = rng.uniform(10.0, 16.5) * (1.0 + bias_up * max(up, 0.0) * 0.3)
        r = rng.uniform(*radius)
        x = centre[0] + math.cos(a) * dist * spread[0]
        y = centre[1] - up * dist * spread[1]
        # keep every billow inside the cell, with a pixel to spare
        r = min(r, x - 1.5, CELL - 1.5 - x, y - 1.5, CELL - 1.5 - y)
        if r > 4.0:
            circles.append((x, y, r, rng.uniform(-3.0, 4.0)))
    return circles


def smoke_cell(circles, detail, levels=7):
    coverage, shade, lowness = billows(circles, detail)
    grey = quantize(0.42 + 0.58 * shade, levels)
    alpha = np.where(coverage >= 0.999, 1.0, np.where(coverage > 0.0, 0.6, 0.0))
    return np.dstack([grey, grey, grey, alpha]), coverage, lowness


def glow_cell(coverage, lowness, fire, circles):
    """The fire inside a clump: the glow atlas's billows over its shape, strongest low down and in the middle."""
    cx = sum(c[0] for c in circles) / len(circles)
    cy = sum(c[1] for c in circles) / len(circles)
    rho = np.clip(np.hypot(XX - cx, YY - cy) / 30.0, 0.0, 1.0)
    strength = (0.35 + 0.65 * (1.0 - rho * rho)) * (0.55 + 0.45 * lowness)
    strength = quantize(strength, 6)
    rgb = fire[..., :3] * strength[..., None]
    alpha = np.where(coverage > 0.0, np.clip(0.45 + 0.55 * strength, 0.0, 1.0), 0.0)
    alpha = np.where(coverage >= 0.999, alpha, alpha * 0.6)
    return np.dstack([rgb, alpha])


def main():
    normal = load("mushroom_cloud.png")
    glow = load("mushroom_cloud_glow.png")
    rng = np.random.default_rng(1945)
    sheet = np.zeros((CELL * 4, CELL * 4, 4))

    def put(index, cell):
        r, c = divmod(index, 4)
        sheet[r * CELL:(r + 1) * CELL, c * CELL:(c + 1) * CELL] = cell

    packed = region(normal, UPPER_CLOUD1_TOP)
    detail = normalized(luminance(packed[..., :3]))
    fire = region(glow, UPPER_CLOUD1_TOP)
    for k in range(4):
        circles = clump(rng)
        cell, coverage, lowness = smoke_cell(circles, np.roll(detail, (k * 17, k * 29), (0, 1)))
        put(k, cell)
        put(4 + k, glow_cell(coverage, lowness, np.roll(fire, (k * 17, k * 29), (0, 1)), circles))

    # dust: low and wide, flat underneath
    circles = [(32.0, 42.0, 13.0, 0.0)]
    for x in (11.0, 20.0, 44.0, 53.0):
        circles.append((x + rng.uniform(-1.5, 1.5), 45.0 + rng.uniform(-2.0, 1.0), rng.uniform(8.5, 10.5), rng.uniform(-2.0, 2.0)))
    for x in (22.0, 33.0, 43.0):
        circles.append((x + rng.uniform(-2.0, 2.0), 33.0 + rng.uniform(-2.0, 2.0), rng.uniform(8.0, 10.0), rng.uniform(0.0, 3.0)))
    circles = [(x, y, min(r, CELL - 1.5 - y), z) for x, y, r, z in circles]
    dust_detail = normalized(luminance(region(normal, LOWER_CLOUD_TOP, CELL)[..., :3]))
    cell, _, _ = smoke_cell(circles, dust_detail, levels=6)
    cell[..., 3] *= np.where(YY > 52.0, 0.0, 1.0)  # a flat underside, sitting on the ground
    put(8, cell)

    # a wisp: small billows strung out in a ragged arc, the gaps between them showing through
    circles = []
    for k in range(9):
        a = math.pi * (0.1 + 0.8 * k / 8)
        circles.append((32.0 - 22.0 * math.cos(a) + rng.uniform(-2, 2), 38.0 - 12.0 * math.sin(a) + rng.uniform(-3, 3),
                        rng.uniform(5.0, 8.5), rng.uniform(-2, 2)))
    cell, _, _ = smoke_cell(circles, detail, levels=6)
    cell[..., 3] *= 0.8
    put(9, cell)

    # a taller clump, for the rising column, and its fire
    circles = clump(rng, spread=(0.72, 1.3), count=(7, 9), radius=(8.0, 12.5), core=15.0)
    cell, coverage, lowness = smoke_cell(circles, np.roll(detail, (40, 11), (0, 1)))
    put(10, cell)
    put(11, glow_cell(coverage, lowness, np.roll(fire, (40, 11), (0, 1)), circles))

    # the fireball: the white-hot burst, cut into a ragged ball
    burst = region(glow, UPPER_CLOUD_BOTTOM, CELL)
    circles = [(32.0, 32.0, 25.0, 0.0)] + [(32.0 + 17.0 * math.cos(a), 32.0 + 17.0 * math.sin(a), rng.uniform(10.0, 13.0), 0.0)
                                           for a in np.linspace(0, 2 * math.pi, 9, endpoint=False) + rng.uniform(-0.2, 0.2, 9)]
    coverage, _, _ = billows(circles, np.full((CELL, CELL), 0.5))
    cell = burst.copy()
    cell[..., 3] = np.where(coverage > 0.0, np.where(coverage >= 0.999, 1.0, 0.6), 0.0)
    put(12, cell)

    # the flash: a soft glare, white in the middle, pale yellow further out
    rho = np.hypot(XX - 32.0, YY - 32.0) / 31.5
    a = np.clip(1.0 - rho, 0.0, 1.0) ** 1.4
    a = quantize(a, 16)
    rgb = np.dstack([np.ones_like(rho), 1.0 - 0.06 * rho, 1.0 - 0.3 * rho])
    put(13, np.dstack([np.clip(rgb, 0, 1), a]))

    # the fireball's cooler outer fire
    outer = region(normal, LOWER_CLOUD_BOTTOM, CELL)
    cell = outer.copy()
    cell[..., 3] = np.where(coverage > 0.0, np.where(coverage >= 0.999, 1.0, 0.6), 0.0)
    put(14, cell)

    img = Image.fromarray((np.clip(sheet, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
    img.save(OUT)
    print("wrote", os.path.relpath(OUT, ROOT))
    if len(sys.argv) > 1:
        bg = Image.new("RGBA", img.size, (96, 128, 160, 255))
        Image.alpha_composite(bg, img).resize((img.width * 3, img.height * 3), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
