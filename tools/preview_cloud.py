"""Offline preview of the 3D nuclear explosion (mirrors client/render/NuclearCloud.java; seen mirrored).

    python3 tools/preview_cloud.py <out.png> <t1> [t2 ...]      (times in seconds; one frame per time, side by side)
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures", "effect")
LIFE = 60.0
SCALE = 1.7


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


def billow(x, y, z):
    return 1 - 2 * abs(vnoise(x, y, z))


def fbm(x, y, z):
    return (billow(x, y, z) * 0.55 + billow(x * 2.1 + 17, y * 2.1, z * 2.1) * 0.3
            + billow(x * 4.3, y * 4.3 + 31, z * 4.3) * 0.15)


# ---- grids: (nu+1) x (nv+1) vertices, u wraps around
class Grid:
    def __init__(self, nu, nv, noise_fn):
        self.nu, self.nv = nu, nv
        self.noise = np.array([[noise_fn(i / nu, j / nv) for j in range(nv + 1)] for i in range(nu + 1)])

    def normals(self, p):
        nu, nv = self.nu, self.nv
        n = np.zeros_like(p)
        for i in range(nu + 1):
            for j in range(nv + 1):
                a = p[(i + 1) % nu][j] - p[(i - 1) % nu][j]
                b = p[i][min(j + 1, nv)] - p[i][max(j - 1, 0)]
                c = np.cross(b, a)
                l = np.linalg.norm(c)
                n[i][j] = c / l if l > 1e-6 else (0, 1, 0)
        return n


def sphere_noise(u, v):
    th, ph = u * 2 * math.pi, (v - 0.5) * math.pi
    d = (math.cos(ph) * math.cos(th), math.sin(ph), math.cos(ph) * math.sin(th))
    return fbm(d[0] * 2.6 + 5, d[1] * 2.6, d[2] * 2.6)


def torus_noise(u, v):
    th, ph = u * 2 * math.pi, v * 2 * math.pi
    return fbm(math.cos(th) * 3.5 + 11, math.sin(th) * 3.5, (math.cos(ph) + math.sin(ph)) * 0.9)


def stem_noise(u, v):
    th = u * 2 * math.pi
    return fbm(math.cos(th) * 1.5, v * 4.0, math.sin(th) * 1.5 + 23)


HEAD = Grid(56, 28, sphere_noise)
TORUS = Grid(56, 18, torus_noise)
STEM = Grid(36, 22, stem_noise)
RING = Grid(64, 8, torus_noise)
BASE = Grid(64, 10, torus_noise)
SHOCK = Grid(72, 4, torus_noise)


def head_params(t, airburst):
    a0 = 2 + 26 * (1 - math.exp(-2.2 * t))
    a = a0 + 32 * (1 - math.exp(-t / 10))
    b = a * (1 - 0.48 * smooth(1.5, 12, t))
    h = 3.0 * t if airburst else a0 * 0.8 + 92 * (1 - math.exp(-t / 7.5))
    return a0, a, b, h


def head_points(t, airburst, scale):
    a0, a, b, h = head_params(t, airburst)
    amp = 0.12 + 0.10 * smooth(1, 8, t)
    flat = 1 - 0.45 * smooth(2, 10, t) if not airburst else 1.0
    p = np.zeros((HEAD.nu + 1, HEAD.nv + 1, 3))
    for i in range(HEAD.nu + 1):
        th = i / HEAD.nu * 2 * math.pi
        for j in range(HEAD.nv + 1):
            ph = (j / HEAD.nv - 0.5) * math.pi
            dx, dy, dz = math.cos(ph) * math.cos(th), math.sin(ph), math.cos(ph) * math.sin(th)
            r = scale * (1 + amp * HEAD.noise[i][j])
            y = dy * b * r * (flat if dy < 0 else 1.0)
            p[i][j] = (dx * a * r, h + y, dz * a * r)
    return p


def torus_points(grid, t, cy, major, tube, squash, amp, wide=1.0):
    p = np.zeros((grid.nu + 1, grid.nv + 1, 3))
    for i in range(grid.nu + 1):
        th = i / grid.nu * 2 * math.pi
        for j in range(grid.nv + 1):
            ph = j / grid.nv * 2 * math.pi
            r = tube * (1 + amp * grid.noise[i][j])
            rr = major + wide * r * math.cos(ph)
            p[i][j] = (math.cos(th) * rr, cy + squash * r * math.sin(ph), math.sin(th) * rr)
    return p


def stem_points(t, scale):
    _, a, b, h = head_params(t, False)
    top = h - b * 0.45
    w = 2.5 + 11 * (1 - math.exp(-t / 5))
    p = np.zeros((STEM.nu + 1, STEM.nv + 1, 3))
    for i in range(STEM.nu + 1):
        th = i / STEM.nu * 2 * math.pi
        for j in range(STEM.nv + 1):
            hh = j / STEM.nv
            r = scale * w * (0.5 + 1.5 * (1 - hh) ** 5 + 1.0 * hh ** 3) * (1 + 0.2 * STEM.noise[i][j])
            p[i][j] = (math.cos(th) * r, hh * top, math.sin(th) * r)
    return p


FIRE_KEYS = [(0, (1, 1, 1)), (0.5, (1, 0.93, 0.7)), (1.5, (1, 0.72, 0.3)), (4, (1, 0.5, 0.12)),
             (10, (0.9, 0.32, 0.06)), (20, (0.62, 0.17, 0.03)), (32, (0.26, 0.06, 0.01)), (42, (0, 0, 0))]


def fire_color(t):
    for (t0, c0), (t1, c1) in zip(FIRE_KEYS, FIRE_KEYS[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0)
            return np.array(c0) + (np.array(c1) - np.array(c0)) * f
    return np.zeros(3)


def parts(t, airburst=False):
    """(grid, points, rgb, alpha, texture, additive, fire weight fn or None, uv repeat, uv offset)"""
    fade = 1 - smooth(LIFE - 15, LIFE, t)
    out = []
    _, a, b, h = head_params(t, airburst)
    fc = fire_color(t)
    burning = fc.max() > 0.01
    cool = smooth(4, 30, t)
    smoke = np.array((0.34, 0.29, 0.26)) + (np.array((0.56, 0.53, 0.51)) - np.array((0.34, 0.29, 0.26))) * cool
    head_alpha = 0.74 * smooth(0.8, 3.5, t) * fade
    under = smooth(1.5, 4.5, t)
    if not airburst:
        shock = smooth(0, 0.3, t) * (1 - smooth(1.5, 4.5, t))
        if shock > 0.01:
            out.append((SHOCK, torus_points(SHOCK, t, 0.8, 10 + 75 * t, 1.6 + 0.4 * t, 0.6, 0.25, 2.5),
                        np.array((1.0, 0.52, 0.14)) * shock, 1.0, "fire", True, None, (12, 1), (0, t * 0.3)))
    out.append((HEAD, head_points(t, airburst, 1.0), smoke, head_alpha, "smoke", False, None, (4, 2), (0, 0)))
    if burning:
        out.append((HEAD, head_points(t, airburst, 1.0 + 0.06 * under), fc, 1.0, "fire", True,
                    lambda n, j, g: (1 - under) + under * min(max(-n[1] * 1.4 + 0.15, 0), 1), (4, 2), (0, -t * 0.05)))
    if airburst:
        ring_a = 0.45 * smooth(0.5, 2.5, t) * (1 - smooth(8, 14, t)) * fade
        if ring_a > 0.01:
            out.append((RING, torus_points(RING, t, h, a * 1.3 + 4 * t, 3.0, 0.5, 0.3, 2.2), np.array((0.95, 0.95, 0.97)), ring_a,
                        "smoke", False, None, (6, 1), (0, 0)))
        return out
    tor_a = 0.74 * smooth(2.5, 7, t) * fade
    if tor_a > 0.01:
        cy, major, tube = h - b * 0.35, a * 0.78, b * 0.42
        out.append((TORUS, torus_points(TORUS, t, cy, major, tube, 0.85, 0.22), smoke * 0.9, tor_a, "smoke", False, None, (5, 1), (0, t * 0.04)))
        if burning:
            glow = smooth(2.5, 7, t) * 0.8
            out.append((TORUS, torus_points(TORUS, t, cy, major, tube * 1.06, 0.85, 0.22), fc * glow, 1.0, "fire", True,
                        lambda n, j, g: min(max(-n[1] * 1.5, 0), 1), (5, 1), (0.3, t * 0.04)))
    top = h - b * 0.45
    w = 2.5 + 11 * (1 - math.exp(-t / 5))
    stem_a = 0.6 * smooth(0.6, 3.0, t) * fade
    if stem_a > 0.01:
        out.append((STEM, stem_points(t, 1.0), smoke, stem_a, "smoke", False, None, (3, 3), (0, -t * 0.06)))
        if burning:
            sc = np.array((fc[0], min(1.0, fc[1] * 1.15), fc[2]))
            out.append((STEM, stem_points(t, 0.72), sc, 1.0, "fire", True, lambda n, j, g: 1 - 0.55 * j / g.nv, (3, 4), (0, -t * 0.12)))
    ring_a = 0.45 * smooth(2.5, 4.5, t) * (1 - smooth(8, 13, t)) * fade
    if ring_a > 0.01:
        out.append((RING, torus_points(RING, t, top * 0.55, w * 2.6 + 8 + 10 * smooth(2, 10, t), 4.0, 0.35, 0.45, 2.2),
                    np.array((0.95, 0.95, 0.97)), ring_a, "smoke", False, None, (6, 1), (0, 0)))
    base_a = 0.6 * smooth(0.3, 1.5, t) * (1 - smooth(25, 45, t)) * fade
    if base_a > 0.01:
        radius = 8 + 62 * (1 - math.exp(-t / 6))
        height = 3 + 7 * (1 - math.exp(-t / 4))
        out.append((BASE, torus_points(BASE, t, height * 0.4, radius * 0.5, height, 1.0, 0.3, radius * 0.5 / height),
                    np.array((0.55, 0.49, 0.43)), base_a, "smoke", False, None, (8, 1), (0, 0)))
        if burning:
            glow = 0.6 * (1 - smooth(4, 14, t))
            if glow > 0.01:
                out.append((BASE, torus_points(BASE, t, height * 0.4, radius * 0.5, height * 0.9, 1.0, 0.3, radius * 0.5 / height),
                            fc * glow, 1.0, "fire", True, lambda n, j, g: 0.4 + 0.6 * min(max(-n[1], 0), 1), (8, 1), (0.5, 0)))
    return out


def load_tex(name):
    return np.asarray(Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")).astype(float) / 255.0


def render(t, size=520, airburst=False, extent=None):
    tex = {k: load_tex("nuclear_" + k) for k in ("smoke", "fire")}
    yaw, pitch = math.radians(30), math.radians(6)
    ry = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    rx = np.array([[1, 0, 0], [0, math.cos(pitch), -math.sin(pitch)], [0, math.sin(pitch), math.cos(pitch)]])
    view = rx @ ry
    extent = extent or 460.0
    scale = size / extent
    sky = np.zeros((size, size, 3))
    for y in range(size):
        sky[y] = np.array((0.45, 0.62, 0.85)) * (1 - y / size * 0.3)
    img = sky.copy()
    ground_y = int(size * 0.85)
    img[ground_y:] = (0.35, 0.42, 0.28)
    zbuf = np.full((size, size), -np.inf)
    light = np.array([0.3, 0.9, -0.3])
    light /= np.linalg.norm(light)

    def project(p):
        q = (p * SCALE) @ view.T
        return q[..., 0] * scale + size / 2, -(q[..., 1]) * scale + ground_y, q[..., 2]

    tris = []
    fires = []
    for grid, pts, rgb, alpha, texname, additive, weight, rep, off in parts(t, airburst):
        nrm = grid.normals(pts)
        sx, sy, sz = project(pts)
        for i in range(grid.nu):
            for j in range(grid.nv):
                quad = [(i, j), (i, j + 1), (i + 1, j + 1), (i + 1, j)]
                verts = []
                for (a, b) in quad:
                    n = nrm[a % (grid.nu + 1)][b]
                    uv = (a / grid.nu * rep[0] + off[0], b / grid.nv * rep[1] + off[1])
                    if additive:
                        w = weight(n, b, grid) if weight else 0.72 + 0.28 * (n[1] * 0.5 + 0.5)
                        col = rgb * w
                    else:
                        ao = 0.72 + 0.28 * (n[1] * 0.5 + 0.5)
                        lam = 0.55 + 0.45 * max(n @ light, 0)
                        col = rgb * ao * lam
                    verts.append((sx[a][b], sy[a][b], sz[a][b], uv, col))
                # the outer faces only (the render types cull the inner ones): the viewer looks along +z
                # (this view looks along +z with y up, a mirror of OpenGL's: its front faces wind clockwise here, and
                # with y down on the image, the cross product of a front face is positive)
                (x0, y0), (x1, y1), (x2, y2) = [(v[0], v[1]) for v in verts[:3]]
                cross = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
                if cross < 0:
                    continue
                depth = np.mean([v[2] for v in verts])
                for tri in ((0, 1, 2), (0, 2, 3)):
                    item = (depth, [verts[k] for k in tri], alpha, texname)
                    (fires if additive else tris).append(item)
    # back to front: the viewer looks along +z
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
    u = (w0 * uv0[0] + w1 * uv1[0] + w2 * uv2[0]) * tw - 0.5
    v = (w0 * uv0[1] + w1 * uv1[1] + w2 * uv2[1]) * th - 0.5
    # bilinear, repeating (the render types filter the cloud's textures smoothly)
    x0i, y0i = np.floor(u).astype(int), np.floor(v).astype(int)
    fx, fy = (u - x0i)[..., None], (v - y0i)[..., None]
    x0i, y0i = np.mod(x0i, tw), np.mod(y0i, th)
    x1i, y1i = np.mod(x0i + 1, tw), np.mod(y0i + 1, th)
    t = (tex[y0i, x0i] * (1 - fx) + tex[y0i, x1i] * fx) * (1 - fy) + (tex[y1i, x0i] * (1 - fx) + tex[y1i, x1i] * fx) * fy
    col = w0[..., None] * c0 + w1[..., None] * c1 + w2[..., None] * c2
    rgb = t[..., :3] * col
    a = t[..., 3] * alpha
    region = img[ymin:ymax + 1, xmin:xmax + 1]
    if additive:
        region[ok] = np.clip(region[ok] + rgb[ok], 0, 1)
    else:
        ok = ok & (a >= 0.1)
        region[ok] = region[ok] * (1 - a[ok, None]) + rgb[ok] * a[ok, None]   # (the smoke writes no depth)


if __name__ == "__main__":
    out = sys.argv[1]
    times = [float(x) for x in sys.argv[2:] if not x.startswith("--")]
    air = "--airburst" in sys.argv
    frames = [render(t, airburst=air) for t in times]
    img = np.concatenate(frames, axis=1)
    Image.fromarray((np.clip(img, 0, 1) * 255).astype("uint8")).save(out)
