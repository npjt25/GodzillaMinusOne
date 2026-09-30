"""Generates the tileable noise textures of the 3D nuclear explosion (client/render/NuclearCloud.java).

    python3 tools/make_cloud_textures.py
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures", "effect")
SIZE = 256


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


def puffs(size, rng, count=420):
    """Heaped-up round puffs of smoke (tileable): a height field of overlapping domes, lit from above, with dark
    creases where they meet, like the billows of a mushroom cloud."""
    height = np.zeros((size, size))
    yy, xx = np.mgrid[0:size, 0:size]
    for _ in range(count):
        cx, cy = rng.random(2) * size
        r = size * (0.035 + 0.075 * rng.random() ** 2)
        lift = rng.random() * 0.6
        dx = (xx - cx + size / 2) % size - size / 2
        dy = (yy - cy + size / 2) % size - size / 2
        d2 = (dx * dx + dy * dy) / (r * r)
        dome = np.where(d2 < 1, np.sqrt(np.clip(1 - d2, 0, 1)) * r / size * 8 + lift, 0)
        height = np.maximum(height, dome)
    # soften, then light it from above (the smoke is lit by the sky and shaded by itself)
    height = blur(height, 3)
    gy, gx = np.gradient(height)
    light = np.clip(0.55 - gy * size * 0.06 - gx * size * 0.02, 0, 1)
    crease = height - blur(height, 8)
    return np.clip(0.4 + light * 0.6 + crease * 1.0, 0, 1)


def blur(a, passes):
    """Tileable soft blur (repeated binomial filter)."""
    k = np.array([1, 4, 6, 4, 1], float)
    k /= k.sum()
    for _ in range(passes):
        for axis in (0, 1):
            a = sum(np.roll(a, i - 2, axis) * k[i] for i in range(5))
    return a


def blur_v(a, passes):
    k = np.array([1, 4, 6, 4, 1], float)
    k /= k.sum()
    for _ in range(passes):
        a = sum(np.roll(a, (i - 2) * 2, 0) * k[i] for i in range(5))
    return a


def save(arr, name):
    Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))


def main():
    rng = np.random.default_rng(1945)
    shade = puffs(SIZE, rng)
    detail = fbm(SIZE, rng, 4, 8)
    shade = np.clip(shade * (0.82 + 0.3 * detail), 0, 1)
    shade = 0.45 + 0.55 * (shade - shade.min()) / (shade.max() - shade.min())
    smoke = np.dstack([shade * 1.0, shade * 0.97, shade * 0.94, np.ones_like(shade)])
    save(smoke, "nuclear_smoke.png")

    # fire: tongues of flame streaming along v (up the cloud): turbulence stretched out lengthways
    t = np.zeros((SIZE, SIZE))
    amp, norm = 1.0, 0.0
    for o in range(5):
        n = value_noise(SIZE, 4 * 2 ** o, rng)
        n = blur_v(n, 3 + 2 * o)                  # smeared out along v into streaks
        n = (n - n.min()) / (n.max() - n.min())
        t += (1 - np.abs(n * 2 - 1)) * amp
        norm += amp
        amp *= 0.55
    t /= norm
    t = (t - t.min()) / (t.max() - t.min())
    fire = np.clip(0.28 + 0.8 * t ** 1.8, 0, 1)
    save(np.dstack([fire, fire, fire, np.ones_like(fire)]), "nuclear_fire.png")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
