"""Offline preview of the blocky 3D nuclear explosion (mirrors client/render/NuclearCloud.java).

    python3 tools/preview_cloud.py <out.png> <t1> [t2 ...]      (times in seconds; one frame per time, side by side)
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures", "effect")
LIFE = 55.0


def smooth(e0, e1, x):
    x = min(max((x - e0) / (e1 - e0), 0.0), 1.0)
    return x * x * (3 - 2 * x)


# ---- noise (same hash + value noise as the Java code)
def hash3(x, y, z):
    h = (x * 374761393 + y * 668265263 + z * 1274126177) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65535.0 * 2.0 - 1.0


def vnoise(x, y, z):
    xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - xi, y - yi, z - zi
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)

    def lerp(a, b, t):
        return a + (b - a) * t

    c = [[[hash3(xi + i, yi + j, zi + k) for k in (0, 1)] for j in (0, 1)] for i in (0, 1)]
    x00 = lerp(c[0][0][0], c[1][0][0], fx)
    x10 = lerp(c[0][1][0], c[1][1][0], fx)
    x01 = lerp(c[0][0][1], c[1][0][1], fx)
    x11 = lerp(c[0][1][1], c[1][1][1], fx)
    return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz)


def fbm(x, y, z):
    return (vnoise(x, y, z) * 0.6 + vnoise(x * 2.1 + 17, y * 2.1, z * 2.1) * 0.28
            + vnoise(x * 4.3, y * 4.3 + 31, z * 4.3) * 0.12)


# ---- noise tables: noise sampled on a (nu+1) x (nv+1) grid over the shape's surface parameters, looked up bilinearly
class Table:
    def __init__(self, nu, nv, noise_fn):
        self.nu, self.nv = nu, nv
        self.noise = np.array([[noise_fn((i % nu) / nu, j / nv) for j in range(nv + 1)] for i in range(nu + 1)])

    def at(self, u, v):
        """u wraps around; v is clamped to [0, 1]. Vectorised."""
        x = np.mod(u, 1.0) * self.nu
        y = np.clip(v, 0.0, 1.0) * self.nv
        i = np.minimum(np.floor(x).astype(int), self.nu - 1)
        j = np.minimum(np.floor(y).astype(int), self.nv - 1)
        fx, fy = x - i, y - j
        n = self.noise
        return ((n[i, j] * (1 - fx) + n[i + 1, j] * fx) * (1 - fy) + (n[i, j + 1] * (1 - fx) + n[i + 1, j + 1] * fx) * fy)


def sphere_noise(u, v):
    th, ph = u * 2 * math.pi, (v - 0.5) * math.pi
    d = (math.cos(ph) * math.cos(th), math.sin(ph), math.cos(ph) * math.sin(th))
    return fbm(d[0] * 2.2 + 5, d[1] * 2.2, d[2] * 2.2)


def torus_noise(u, v):
    th, ph = u * 2 * math.pi, v * 2 * math.pi
    return fbm(math.cos(th) * 3 + 11, math.sin(th) * 3, math.cos(ph) * 0.8 + math.sin(ph) * 0.8)


def stem_noise(u, v):
    th = u * 2 * math.pi
    return fbm(math.cos(th) * 1.5, v * 4.0, math.sin(th) * 1.5 + 23)


HEAD = Table(36, 20, sphere_noise)
TORUS = Table(40, 16, torus_noise)
STEM = Table(28, 18, stem_noise)
RING = Table(40, 8, torus_noise)
BASE = Table(48, 10, torus_noise)

VOXEL = 4.0
EMPTY, M_HEAD, M_TORUS, M_STEM, M_BASE, M_RING = range(6)


def head_params(t, airburst):
    a0 = 2 + 26 * (1 - math.exp(-2.2 * t))
    a = a0 + 32 * (1 - math.exp(-t / 10))
    b = a * (1 - 0.48 * smooth(1.5, 12, t))
    h = 3.0 * t if airburst else a0 * 0.8 + 92 * (1 - math.exp(-t / 7.5))
    return a0, a, b, h


FIRE_KEYS = [(0, (1, 1, 1)), (0.5, (1, 0.92, 0.65)), (1.5, (1, 0.62, 0.25)), (4, (0.95, 0.35, 0.08)),
             (8, (0.55, 0.13, 0.03)), (14, (0.25, 0.05, 0.01)), (20, (0, 0, 0))]


def fire_color(t):
    for (t0, c0), (t1, c1) in zip(FIRE_KEYS, FIRE_KEYS[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0)
            return np.array(c0) + (np.array(c1) - np.array(c0)) * f
    return np.zeros(3)


def alphas(t, airburst):
    """Opacity of each part of the cloud at time t (and whether the fireball still burns)."""
    fade = 1 - smooth(LIFE - 14, LIFE, t)
    burning = fire_color(t).max() > 0.01
    out = {M_HEAD: smooth(1.0, 4.0, t) * fade}
    if airburst:
        out[M_RING] = 0.6 * smooth(0.5, 2.5, t) * (1 - smooth(8, 14, t)) * fade
        return out, burning
    out[M_TORUS] = smooth(2.5, 7, t) * fade
    out[M_STEM] = smooth(0.6, 3.0, t) * fade
    out[M_RING] = 0.6 * smooth(2.5, 4.5, t) * (1 - smooth(8, 13, t)) * fade
    out[M_BASE] = smooth(0.3, 1.5, t) * (1 - smooth(25, 45, t)) * fade
    return out, burning


def torus_shape(cy, major, tube, squash, amp, wide):
    return ("torus", cy, major, tube, squash, amp, wide)


def shapes(t, airburst):
    """The parts of the cloud at time t, in priority order: (material, kind, parameters...)."""
    al, burning = alphas(t, airburst)
    _, a, b, h = head_params(t, airburst)
    out = []
    if al[M_HEAD] > 0.01 or burning:
        amp = 0.10 + 0.08 * smooth(1, 8, t)
        flat = 1.0 if airburst else 1 - 0.45 * smooth(2, 10, t)
        solid = 0.94 + 0.06 * smooth(1.5, 4, t)
        out.append((M_HEAD, ("head", a, b, h, amp, flat, solid), HEAD))
    if airburst:
        if al[M_RING] > 0.01:
            out.append((M_RING, torus_shape(h, a * 1.3 + 4 * t, 3.0, 0.5, 0.3, 2.2), RING))
        return out
    if al[M_TORUS] > 0.01:
        out.append((M_TORUS, torus_shape(h - b * 0.35, a * 0.78, b * 0.42, 0.85, 0.2, 1.0), TORUS))
    top = h - b * 0.45
    w = 2.5 + 11 * (1 - math.exp(-t / 5))
    if al[M_STEM] > 0.01 or burning:
        out.append((M_STEM, ("stem", top, w), STEM))
    if al[M_BASE] > 0.01:
        radius = 8 + 62 * (1 - math.exp(-t / 6))
        height = 3 + 7 * (1 - math.exp(-t / 4))
        out.append((M_BASE, torus_shape(height * 0.4, radius * 0.5, height, 1.0, 0.3, radius * 0.5 / height), BASE))
    if al[M_RING] > 0.01:
        out.append((M_RING, torus_shape(top * 0.55, w * 2.6 + 8 + 10 * smooth(2, 10, t), 4.0, 0.35, 0.45, 2.2), RING))
    return out


def extent(shape):
    """(half width, lowest y, highest y) of a shape, noise included."""
    kind = shape[0]
    if kind == "head":
        _, a, b, h, amp, flat, solid = shape
        k = solid * (1 + amp)
        return a * k, h - b * flat * k, h + b * k
    if kind == "torus":
        _, cy, major, tube, squash, amp, wide = shape
        k = tube * (1 + amp)
        return major + wide * k, cy - squash * k, cy + squash * k
    _, top, w = shape
    return w * 2.0 * 1.16, 0.0, top


def inside(shape, table, x, y, z):
    """Whether cell centres (arrays) are inside a shape."""
    kind = shape[0]
    if kind == "head":
        _, a, b, h, amp, flat, solid = shape
        dy = y - h
        bv = np.where(dy < 0, b * flat, b)
        qx, qy, qz = x / a, dy / bv, z / a
        r = np.sqrt(qx * qx + qy * qy + qz * qz)
        u = np.arctan2(qz, qx) / (2 * math.pi)
        v = np.arcsin(np.clip(qy / np.maximum(r, 1e-6), -1, 1)) / math.pi + 0.5
        return r <= solid * (1 + amp * table.at(u, v))
    if kind == "torus":
        _, cy, major, tube, squash, amp, wide = shape
        rho = np.sqrt(x * x + z * z)
        dr = (rho - major) / wide
        dyy = (y - cy) / squash
        r = np.sqrt(dr * dr + dyy * dyy)
        u = np.arctan2(z, x) / (2 * math.pi)
        v = np.arctan2(dyy, dr) / (2 * math.pi)
        return r <= tube * (1 + amp * table.at(u, v))
    _, top, w = shape
    hh = np.clip(y / max(top, 1e-6), 0, 1)
    rr = w * (0.5 + 1.5 * (1 - hh) ** 5 + hh ** 3) * (1 + 0.16 * table.at(np.arctan2(z, x) / (2 * math.pi), hh))
    return (y >= 0) & (y <= top) & (np.sqrt(x * x + z * z) <= rr)


def voxelize(t, airburst=False):
    """The cloud as a grid of cells (VOXEL blocks on a side): {(i, j, k): material}."""
    cells = {}
    for mat, shape, table in shapes(t, airburst):
        half, lo, hi = extent(shape)
        n = int(math.ceil(half / VOXEL))
        j0, j1 = max(0, int(math.floor(lo / VOXEL))), int(math.ceil(hi / VOXEL))
        ii, jj, kk = np.meshgrid(np.arange(-n, n), np.arange(j0, j1 + 1), np.arange(-n, n), indexing="ij")
        x, y, z = (ii + 0.5) * VOXEL, (jj + 0.5) * VOXEL, (kk + 0.5) * VOXEL
        hit = inside(shape, table, x, y, z)
        for i, j, k in zip(ii[hit], jj[hit], kk[hit]):
            cells.setdefault((int(i), int(j), int(k)), mat)
    return cells


# faces: direction, normal, shade (like blocks: tops lit, bottoms dark)
DIRS = [((0, 1, 0), 1.0), ((0, -1, 0), 0.5), ((1, 0, 0), 0.8), ((-1, 0, 0), 0.8), ((0, 0, 1), 0.65), ((0, 0, -1), 0.65)]


def faces(cells):
    out = []
    for (i, j, k), mat in cells.items():
        for d, (n, shade) in enumerate(DIRS):
            if (i + n[0], j + n[1], k + n[2]) not in cells:
                out.append((i, j, k, d, mat))
    return out


def face_corners(i, j, k, d):
    x0, y0, z0 = i * VOXEL, j * VOXEL, k * VOXEL
    x1, y1, z1 = x0 + VOXEL, y0 + VOXEL, z0 + VOXEL
    return {
        0: [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        1: [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
        2: [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)],
        3: [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
        4: [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
        5: [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)],
    }[d]


def colours(t, airburst):
    """Smoke colour per material, and the fire colour."""
    warm = smooth(3, 20, t)
    smoke = np.array((0.50, 0.44, 0.38)) + (np.array((0.68, 0.64, 0.60)) - np.array((0.50, 0.44, 0.38))) * warm
    return {M_HEAD: smoke, M_TORUS: smoke * 0.92, M_STEM: np.array((0.48, 0.42, 0.37)), M_BASE: np.array((0.62, 0.56, 0.49)),
            M_RING: np.array((0.95, 0.95, 0.97))}, fire_color(t)


def fire_weight(t, mat, d, j, top):
    """How brightly a face of the fireball / stem glows: at first all of it; later only the underside of the cap."""
    under = smooth(2, 5, t)
    if mat == M_HEAD:
        ny = DIRS[d][0][1]
        return (1 - under) + under * min(max(-ny * 1.3 + 0.1, 0.0), 1.0)
    if mat == M_STEM:
        hh = min(max((j + 0.5) * VOXEL / max(top, 1e-6), 0.0), 1.0)
        return 0.8 * (1 - hh) ** 2
    return 0.0


def load_tex(name):
    return np.asarray(Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")).astype(float) / 255.0


def render(t, size=520, airburst=False, extent_blocks=None):
    tex = {k: load_tex("nuclear_" + k) for k in ("smoke", "fire")}
    yaw, pitch = math.radians(30), math.radians(6)
    ry = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    rx = np.array([[1, 0, 0], [0, math.cos(pitch), -math.sin(pitch)], [0, math.sin(pitch), math.cos(pitch)]])
    view = rx @ ry
    scale = size / (extent_blocks or 300.0)
    img = np.zeros((size, size, 3))
    for y in range(size):
        img[y] = np.array((0.45, 0.62, 0.85)) * (1 - y / size * 0.3)
    ground_y = int(size * 0.85)
    img[ground_y:] = (0.35, 0.42, 0.28)
    zbuf = np.full((size, size), -np.inf)

    def project(p):
        q = np.asarray(p) @ view.T
        return q[..., 0] * scale + size / 2, -(q[..., 1]) * scale + ground_y, q[..., 2]

    al, burning = alphas(t, airburst)
    smoke, fire = colours(t, airburst)
    _, a, b, h = head_params(t, airburst)
    top = h - b * 0.45
    tris, fires = [], []
    for (i, j, k, d, mat) in faces(voxelize(t, airburst)):
        corners = face_corners(i, j, k, d)
        sx, sy, sz = project(corners)
        normal, shade = DIRS[d]
        axes = [q for q in range(3) if normal[q] == 0]
        uvs = [(c[axes[0]] / 32.0, c[axes[1]] / 32.0) for c in corners]
        depth = float(np.mean(sz))
        if al.get(mat, 0) > 0.01:
            col = smoke[mat] * shade
            verts = [(sx[q], sy[q], sz[q], uvs[q], col) for q in range(4)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                tris.append((depth, [verts[q] for q in tri], al[mat], "smoke"))
        if burning and mat in (M_HEAD, M_STEM):
            w = fire_weight(t, mat, d, j, top)
            if w > 0.01:
                verts = [(sx[q], sy[q], sz[q], uvs[q], fire * w) for q in range(4)]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    fires.append((depth, [verts[q] for q in tri], 1.0, "fire"))
    tris.sort(key=lambda it: -it[0])
    for group, add in ((tris, False), (fires, True)):
        for depth, vs, alpha, texname in group:
            raster(img, zbuf, vs, alpha, tex[texname], add)
    return img


def raster(img, zbuf, vs, alpha, tex, additive):
    size = img.shape[0]
    (x0, y0, z0, uv0, c0), (x1, y1, z1, uv1, c1), (x2, y2, z2, uv2, c2) = vs
    xmin, xmax = int(max(0, math.floor(min(x0, x1, x2)))), int(min(size - 1, math.ceil(max(x0, x1, x2))))
    ymin, ymax = int(max(0, math.floor(min(y0, y1, y2)))), int(min(size - 1, math.ceil(max(y0, y1, y2))))
    if xmin > xmax or ymin > ymax:
        return
    det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(det) < 1e-9:
        return
    X, Y = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
    w0 = ((y1 - y2) * (X - x2) + (x2 - x1) * (Y - y2)) / det
    w1 = ((y2 - y0) * (X - x2) + (x0 - x2) * (Y - y2)) / det
    w2 = 1 - w0 - w1
    inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
    if not inside.any():
        return
    depth = -(w0 * z0 + w1 * z1 + w2 * z2)
    sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
    ok = inside & (depth > sub)
    th, tw = tex.shape[:2]
    u = w0 * uv0[0] + w1 * uv1[0] + w2 * uv2[0]
    v = w0 * uv0[1] + w1 * uv1[1] + w2 * uv2[1]
    tx = np.mod((u * tw).astype(int), tw)
    ty = np.mod((v * th).astype(int), th)
    t = tex[ty, tx]
    col = w0[..., None] * c0 + w1[..., None] * c1 + w2[..., None] * c2
    rgb = t[..., :3] * col
    a = t[..., 3] * alpha
    region = img[ymin:ymax + 1, xmin:xmax + 1]
    if additive:
        region[ok] = np.clip(region[ok] + rgb[ok], 0, 1)
    else:
        ok = ok & (a >= 0.1)
        region[ok] = region[ok] * (1 - a[ok, None]) + rgb[ok] * a[ok, None]
        sub[ok] = depth[ok]


if __name__ == "__main__":
    out = sys.argv[1]
    times = [float(x) for x in sys.argv[2:] if not x.startswith("--")]
    air = "--airburst" in sys.argv
    frames = [render(t, airburst=air) for t in times]
    img = np.concatenate(frames, axis=1)
    Image.fromarray((np.clip(img, 0, 1) * 255).astype("uint8")).save(out)
