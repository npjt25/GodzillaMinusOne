"""Noise used to paint textures (the Godzilla burns, the props' worn metal)."""
import numpy as np


def worley(size, cell, r):
    """Distances to the nearest and second-nearest feature point (a jittered grid), in texels."""
    n = size // cell
    pts = (np.arange(n)[:, None, None] * 0 + np.stack(np.meshgrid(np.arange(n), np.arange(n), indexing="ij"), -1)
           + r.random((n, n, 2))) * cell
    yy, xx = np.meshgrid(np.arange(size) + 0.5, np.arange(size) + 0.5, indexing="ij")
    cy, cx = (yy // cell).astype(int), (xx // cell).astype(int)
    f1 = np.full((size, size), 1e9)
    f2 = np.full((size, size), 1e9)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            py = pts[(cy + dy) % n, (cx + dx) % n]
            # keep the wrap-around consistent: shift the feature point by a whole grid if we wrapped
            oy = np.where(cy + dy < 0, -size, np.where(cy + dy >= n, size, 0))
            ox = np.where(cx + dx < 0, -size, np.where(cx + dx >= n, size, 0))
            d = np.hypot(py[..., 0] + oy - yy, py[..., 1] + ox - xx)
            f2 = np.where(d < f1, f1, np.minimum(f2, d))
            f1 = np.minimum(f1, d)
    return f1, f2


def hide_detail(size, r, cell=11):
    """A brightness multiplier: raised nodules, dark cracks between them, and a fine grain."""
    f1, f2 = worley(size, cell, r)
    nodule = np.clip(1.0 - (f1 / (cell * 0.68)) ** 2, 0.0, 1.0) ** 0.8
    crack = np.clip(1.0 - (f2 - f1) / (cell * 0.2), 0.0, 1.0)
    g1, _ = worley(size, max(3, cell * 4 // 11), r)
    fine = np.clip(1.0 - (g1 / 3.0) ** 2, 0.0, 1.0)
    grain = r.random((size, size))
    return 0.74 + 0.36 * nodule + 0.06 * fine - 0.34 * crack ** 2 + 0.03 * (grain - 0.5)


def smooth_noise(size, cells, r):
    """Blobby low-frequency noise in [0, 1] (a random grid, bilinearly upsampled)."""
    grid = r.random((cells + 1, cells + 1))
    t = np.linspace(0, cells, size, endpoint=False)
    i = t.astype(int)
    f = t - i
    f = f * f * (3 - 2 * f)
    row = grid[i][:, i] * (1 - f)[None, :] + grid[i][:, i + 1] * f[None, :]
    row2 = grid[i + 1][:, i] * (1 - f)[None, :] + grid[i + 1][:, i + 1] * f[None, :]
    return row * (1 - f)[:, None] + row2 * f[:, None]
