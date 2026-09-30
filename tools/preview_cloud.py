"""Offline preview of the atomic blast's mushroom cloud (mirrors client/render/NuclearCloud.java: GojiDraw's five
smoke layers). Perspective view from a distance, over a ground grid of 20-block squares.

    python3 tools/preview_cloud.py <out.png> <t1> [t2 ...]   (seconds after the blast; one frame per time)
    options: --distance=600 --height=40 --fov=70 --size=640 --seed=1
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRITE = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures", "effect", "mushroom_smoke.png")
# y, radius, rim only, billboard half size, up acceleration, seconds emitting, tint gradient (as in the Java code)
LAYERS = [
    (0, 20, False, 15, 100, 25, [(0.0, 0xFF2E2D2D), (0.54, 0x59484848), (1.0, 0x00848484)]),
    (55, 200, False, 50, 100, 26, [(0.0, 0xFF6E6E6E), (0.99, 0x8C595959)]),
    (70, 200, True, 60, 100, 25, [(0.0, 0xFF4B4B4B), (0.5, 0xFF646464), (0.9, 0x6E202020)]),
    (0, 20, False, 10, 200, 25, [(0.0, 0xFF2B2B2B), (0.4, 0xFF2E2E2E), (0.58, 0xFF272727), (1.0, 0x00242222)]),
    (80, 150, False, 50, 100, 25, [(0.0, 0xFF5A5047), (0.24, 0xFF423D2E), (0.57, 0xFF382A18), (1.0, 0x52261608)]),
]
RATE, LIFE = 200, 1.0


def argb(c):
    return np.array([(c >> 24) & 255, (c >> 16) & 255, (c >> 8) & 255, c & 255], float)


def tint(stops, f):
    if f <= stops[0][0]:
        return argb(stops[0][1])
    for (f0, c0), (f1, c1) in zip(stops, stops[1:]):
        if f <= f1:
            return np.round(argb(c0) + (argb(c1) - argb(c0)) * (f - f0) / (f1 - f0))
    return argb(stops[-1][1])


def particles(t, rng):
    """The sprites alive t seconds after the blast: (x, y, z, half size, frame (always 0), rgb)."""
    out = []
    now = t * 20.0
    for y0, radius, rim, size, acc, seconds, stops in LAYERS:
        # the ticks whose sprites are still alive
        first = max(1, int(math.floor(now - LIFE * 20)))
        for tick in range(first, int(math.floor(now)) + 1):
            if tick > seconds * 20:
                break
            for k in range(RATE // 20):
                born = tick - 1 + (k + 1) / (RATE // 20)
                age = (now - born) / 20.0
                if age < 0 or age > LIFE:
                    continue
                a, r, g, b = tint(stops, age / LIFE)      # (the alpha test is on the texture alone)
                frame = 0
                ang = rng.random() * 2 * math.pi
                rr = radius if rim else radius * math.sqrt(rng.random())
                out.append((math.cos(ang) * rr, y0 + 0.5 * acc * age * age, math.sin(ang) * rr, size, frame, (r, g, b)))
    return out


def render(t, distance=600.0, height=40.0, fov=70.0, size=640, seed=1):
    rng = np.random.default_rng(seed + int(t * 20))
    sheet = np.array(Image.open(SPRITE).convert("RGBA"))
    cam = np.array([0.0, height, -distance])
    target = np.array([0.0, 110.0, 0.0])
    fwd = target - cam
    fwd /= np.linalg.norm(fwd)
    right = np.cross(np.array([0.0, 1.0, 0.0]), fwd)
    right /= np.linalg.norm(right)
    up = np.cross(fwd, right)
    f = size / 2 / math.tan(math.radians(fov) / 2)

    def project(p):
        d = p - cam
        z = d @ fwd
        return size / 2 + (d @ right) * f / z, size / 2 - (d @ up) * f / z, z

    img = np.zeros((size, size, 3))
    for y in range(size):     # sky
        img[y] = np.array([0.52, 0.68, 0.9]) * (0.8 + 0.2 * y / size)
    zbuf = np.full((size, size), np.inf)
    # ground and its grid (20-block squares, thicker every 100)
    yy, xx = np.mgrid[0:size, 0:size] + 0.5
    dirs = (xx[..., None] - size / 2) / f * right + (size / 2 - yy[..., None]) / f * up + fwd
    hit = dirs[..., 1] < -1e-6
    s = np.where(hit, -cam[1] / np.where(hit, dirs[..., 1], -1), 0)
    gx = cam[0] + s * dirs[..., 0]
    gz = cam[2] + s * dirs[..., 2]
    ground = np.array([0.36, 0.45, 0.3])
    line = (np.abs((gx + 10) % 20 - 10) < 0.02 * s / 10 + 0.4) | (np.abs((gz + 10) % 20 - 10) < 0.02 * s / 10 + 0.4)
    big = (np.abs((gx + 50) % 100 - 50) < 1.2) | (np.abs((gz + 50) % 100 - 50) < 1.2)
    img[hit] = ground
    img[hit & line] = ground * 0.8
    img[hit & big] = ground * 0.6
    zbuf[hit] = s[hit] * (dirs[hit] @ fwd)
    for x, y, z, half, frame, rgb in particles(t, rng):
        sx, sy, sz = project(np.array([x, y, z]))
        if sz <= 1:
            continue
        h = half * f / sz
        x0, x1, y0, y1 = sx - h, sx + h, sy - h, sy + h
        ix0, ix1 = max(0, int(x0)), min(size - 1, int(math.ceil(x1)))
        iy0, iy1 = max(0, int(y0)), min(size - 1, int(math.ceil(y1)))
        if ix0 > ix1 or iy0 > iy1:
            continue
        X, Y = np.meshgrid(np.arange(ix0, ix1 + 1) + 0.5, np.arange(iy0, iy1 + 1) + 0.5)
        u = ((X - x0) / (x1 - x0) * 16).astype(int)
        v = ((Y - y0) / (y1 - y0) * 15).astype(int)
        ok = (u >= 0) & (u < 16) & (v >= 0) & (v < 15)
        u = np.clip(u, 0, 15)
        v = np.clip(v, 0, 14)
        tex = sheet[v, 16 * frame + u]
        show = ok & (tex[..., 3] > 0)
        sub = zbuf[iy0:iy1 + 1, ix0:ix1 + 1]
        win = show & (sz < sub)
        col = tex[..., :3] / 255.0 * (np.array(rgb) / 255.0)
        img[iy0:iy1 + 1, ix0:ix1 + 1][win] = col[win]
        sub[win] = sz
    return img


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    opts = dict(a[2:].split("=") for a in sys.argv[1:] if a.startswith("--"))
    kw = {k: (int(v) if k in ("size", "seed") else float(v)) for k, v in opts.items()}
    frames = [render(float(t), **kw) for t in args[1:]]
    Image.fromarray((np.clip(np.concatenate(frames, axis=1), 0, 1) * 255).astype(np.uint8)).save(args[0])
