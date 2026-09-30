"""Generates the tileable noise textures of the 3D nuclear explosion (client/render/NuclearCloud.java).

    python3 tools/make_cloud_textures.py
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures", "effect")
SIZE = 128


def value_noise(size, cells, rng):
    """Tileable smooth value noise in [0, 1]."""
    grid = rng.random((cells, cells))
    x = np.arange(size) * cells / size
    i0 = np.floor(x).astype(int)
    f = x - i0
    f = f * f * (3 - 2 * f)
    i1 = (i0 + 1) % cells
    a = grid[np.ix_(i0, i0)]
    b = grid[np.ix_(i0, i1)]
    c = grid[np.ix_(i1, i0)]
    d = grid[np.ix_(i1, i1)]
    fy = f[:, None]
    fx = f[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(size, rng, octaves=5, base=4):
    total = np.zeros((size, size))
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        total += value_noise(size, base * 2 ** o, rng) * amp
        norm += amp
        amp *= 0.5
    return total / norm


def billows(size, rng):
    """Puffy 'cauliflower' look: sum of |noise| (billow noise)."""
    total = np.zeros((size, size))
    amp = 1.0
    norm = 0.0
    for o in range(5):
        n = value_noise(size, 4 * 2 ** o, rng)
        total += (1 - np.abs(n * 2 - 1)) * amp
        norm += amp
        amp *= 0.55
    t = total / norm
    return (t - t.min()) / (t.max() - t.min())


def save(arr, name):
    Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))


def main():
    rng = np.random.default_rng(1945)
    b = billows(SIZE, rng)
    detail = fbm(SIZE, rng, 4, 8)
    shade = 0.62 + 0.38 * (0.75 * b + 0.25 * detail)
    smoke = np.dstack([shade * 1.0, shade * 0.97, shade * 0.93, np.ones_like(shade)])
    save(smoke, "nuclear_smoke.png")

    fbm(SIZE, rng, 5, 4)   # (was the smooth cloud's fluffy edge texture; kept so the fire texture stays the same)

    t = fbm(SIZE, rng, 5, 4)
    turb = 1 - np.abs(t * 2 - 1)
    turb = (turb - turb.min()) / (turb.max() - turb.min())
    fire = np.clip(0.35 + 0.65 * turb ** 2.5 + 0.15 * (fbm(SIZE, rng, 3, 8) - 0.5), 0, 1)
    save(np.dstack([fire, fire, fire, np.ones_like(fire)]), "nuclear_fire.png")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
