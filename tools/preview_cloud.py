"""Offline preview of the atomic blast's mushroom cloud: Alex's Caves' cloud as client/render/NuclearCloud.java draws it
(tools/mushroom_cloud_model.py holds the same model), at given times and from given viewpoints, over a ground grid.

    python3 tools/preview_cloud.py <out.png> [--height 240] [--seconds 60] [--size 480x360]
                                   [--times 0.25,1,5,20,40,59.5] [--views side,three_quarter,below,above]

Each frame is drawn the way the game draws the cloud: the cloud's texture, lit like an entity; the same texture again,
glowing, where the first pass drew; the glow texture on top of that, fading as the cloud grows; and its fiery puffs.
Approximations: the puffs are drawn as round blobs (the game uses vanilla's smoke and explosion sprites, which aren't
here), the sky and ground are plain, and there's no fog (the game draws the cloud through it anyway).
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mushroom_cloud_model as mcm  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures", "effect")
SOURCE_SCALE = 3.2
MODEL_HEIGHT = 12.14
LIGHT0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
GODZILLA_HEIGHT = 45.5

# name: (camera position, looked-at point, label) for a cloud 240 blocks tall; scaled with --height
VIEWS = {
    "side": ((0.0, 3.0, -760.0), (0.0, 120.0, 0.0), "from the side, 760 blocks off, eye level"),
    "three_quarter": ((-430.0, 260.0, -430.0), (0.0, 105.0, 0.0), "three-quarters, from 260 blocks up"),
    "below": ((-210.0, 2.0, -230.0), (0.0, 200.0, 0.0), "from below, 310 blocks off"),
    "above": ((-250.0, 620.0, -300.0), (0.0, 90.0, 0.0), "from above, 620 blocks up"),
    "far": ((0.0, 700.0, -2600.0), (0.0, 60.0, 0.0), "the whole of it, 2.6 km off"),
}


def load(name):
    return np.array(Image.open(os.path.join(TEX_DIR, name)).convert("RGBA")).astype(np.float32) / 255.0


class Camera:
    def __init__(self, pos, target, w, h, fov=70.0):
        self.pos = np.array(pos, float)
        f = np.array(target, float) - self.pos
        self.f = f / np.linalg.norm(f)
        r = np.cross(self.f, [0.0, 1.0, 0.0])
        self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.r, self.f)
        self.w, self.h = w, h
        self.focal = (h / 2) / math.tan(math.radians(fov / 2))
        self.near = 0.5

    def to_cam(self, p):
        d = np.asarray(p, float) - self.pos
        return np.stack([d @ self.r, d @ self.u, d @ self.f], -1)

    def ray(self):
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        dx = (xs + 0.5 - self.w / 2) / self.focal
        dy = -(ys + 0.5 - self.h / 2) / self.focal
        return dx, dy


def clip_near(cam_pts, attrs, near):
    """Sutherland-Hodgman against z >= near: a polygon's camera-space points and per-vertex attributes."""
    out_p, out_a = [], []
    n = len(cam_pts)
    for i in range(n):
        p0, p1 = cam_pts[i], cam_pts[(i + 1) % n]
        a0, a1 = attrs[i], attrs[(i + 1) % n]
        in0, in1 = p0[2] >= near, p1[2] >= near
        if in0:
            out_p.append(p0)
            out_a.append(a0)
        if in0 != in1:
            t = (near - p0[2]) / (p1[2] - p0[2])
            out_p.append(p0 + (p1 - p0) * t)
            out_a.append(a0 + (a1 - a0) * t)
    return out_p, out_a


def raster(cam, cam_pts, attrs):
    """Yields (ys, xs, depth, interpolated attributes) for the pixels a convex polygon covers (perspective-correct)."""
    pts, ats = clip_near(list(cam_pts), list(attrs), cam.near)
    if len(pts) < 3:
        return
    P = np.array(pts)
    A = np.array(ats)
    sx = cam.w / 2 + P[:, 0] / P[:, 2] * cam.focal
    sy = cam.h / 2 - P[:, 1] / P[:, 2] * cam.focal
    iz = 1.0 / P[:, 2]
    for k in range(1, len(P) - 1):
        idx = [0, k, k + 1]
        x0, x1, x2 = sx[idx]
        y0, y1, y2 = sy[idx]
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if abs(area) < 1e-9:
            continue
        xmin, xmax = int(max(0, math.floor(min(x0, x1, x2)))), int(min(cam.w - 1, math.ceil(max(x0, x1, x2))))
        ymin, ymax = int(max(0, math.floor(min(y0, y1, y2)))), int(min(cam.h - 1, math.ceil(max(y0, y1, y2))))
        if xmin > xmax or ymin > ymax:
            continue
        ys, xs = np.mgrid[ymin:ymax + 1, xmin:xmax + 1]
        px, py = xs + 0.5, ys + 0.5
        w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area
        w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area
        w2 = 1.0 - w0 - w1
        inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not inside.any():
            continue
        w0, w1, w2 = w0[inside], w1[inside], w2[inside]
        izs = iz[idx]
        wz = w0 * izs[0] + w1 * izs[1] + w2 * izs[2]
        depth = 1.0 / wz
        at = (np.outer(w0 * izs[0], A[idx[0]]) + np.outer(w1 * izs[1], A[idx[1]]) + np.outer(w2 * izs[2], A[idx[2]])) / wz[:, None]
        yield ys[inside], xs[inside], depth, at


def sample(tex, uv):
    tx = np.clip(np.floor(uv[:, 0]).astype(int), 0, tex.shape[1] - 1)
    ty = np.clip(np.floor(uv[:, 1]).astype(int), 0, tex.shape[0] - 1)
    return tex[ty, tx]


def cloud_quads(t_ticks, lifetime, scale, pos):
    """The cloud's faces in world space at a moment (NuclearCloud.render), with its alpha and glow."""
    age = int(math.floor(t_ticks))
    partial = t_ticks - age
    quads, life, glow = mcm.quads(age, partial, lifetime)
    fade = max(10, round(lifetime / 40.0))
    alpha = min(max((lifetime - age - partial) / fade, 0.0), 1.0)
    glow = min(glow, 1.0)
    k = scale / SOURCE_SCALE
    origin = np.array(pos) - np.array([0.0, 0.5 * k, 0.0])
    flip = np.array([-scale, -scale, scale])
    out = []
    for corners, uvs, normal, name in quads:
        w = corners * flip + origin
        n = normal * np.array([-1.0, -1.0, 1.0])
        out.append((w, uvs, n / max(np.linalg.norm(n), 1e-9), name))
    return out, alpha, glow


def puffs(t_ticks, lifetime, scale, pos, rng):
    """The puffs alive at a moment (NuclearCloud.puffs, NukePuffParticle): (position, radius, rgb, is_fire)."""
    k = scale / SOURCE_SCALE
    fade = max(10, round(lifetime / 40.0))
    now = int(t_ticks)
    out = []
    for born in range(max(mcm.BALL_FOR, now - 25), now + 1):
        if born >= lifetime - fade:
            break
        life = math.log(1 + (born - mcm.BALL_FOR) / (lifetime - mcm.BALL_FOR)) * 2.0
        spread = (12 * life + 4) * scale
        smoke = math.ceil((1 + rng.integers(2)) * SOURCE_SCALE)
        fire = math.ceil(SOURCE_SCALE * SOURCE_SCALE)
        for kind, count in (("smoke", smoke), ("fire", fire)):
            for _ in range(count):
                lifetime_p = 15 + rng.integers(10)
                size = (0.8 + rng.random() * 0.3) * k
                if kind == "smoke":
                    p = (rng.random(3) - 0.5) * scale * 1.4 + pos
                    v = (rng.random(3) - 0.5) * 2.34 * k
                else:
                    r = rng.random(3)
                    p = np.array([(r[0] - 0.5) * spread, (-0.6 + r[1] * 0.5) * spread * 0.1, (r[2] - 0.5) * spread]) + pos
                    v = np.zeros(3)
                a = now - born
                if a > lifetime_p:
                    continue
                travelled = v * (1 - 0.96 ** a) / 0.04
                tint = np.array([1.0, 0xB3 / 255.0, 0.0]) * 0.95 ** a
                out.append((p + travelled, size, tint, kind == "fire"))
    return out


def ground_and_sky(cam, img, zbuf):
    dx, dy = cam.ray()
    d = cam.f[None, None, :] + cam.r[None, None, :] * dx[..., None] + cam.u[None, None, :] * dy[..., None]
    sky = np.clip(0.55 + 0.45 * dy[..., None] / 1.2, 0, 1)
    img[:] = np.array([0.46, 0.63, 0.86]) * sky + np.array([0.75, 0.84, 0.95]) * (1 - sky)
    with np.errstate(divide="ignore", invalid="ignore"):
        t = -cam.pos[1] / d[..., 1]
    hit = (t > 0) & np.isfinite(t)
    gx = cam.pos[0] + d[..., 0] * t
    gz = cam.pos[2] + d[..., 2] * t
    # (lines about a pixel wide wherever they are)
    thin, thick = t * 1.2 / cam.focal, t * 2.4 / cam.focal
    minor = (np.abs(((gx + 10) % 20) - 10) < thin) | (np.abs(((gz + 10) % 20) - 10) < thin)
    major = (np.abs(((gx + 50) % 100) - 50) < thick) | (np.abs(((gz + 50) % 100) - 50) < thick)
    col = np.where(major[..., None], [0.2, 0.3, 0.18], np.where(minor[..., None], [0.3, 0.42, 0.26], [0.42, 0.56, 0.34]))
    img[hit] = col[hit]
    zbuf[hit] = t[hit]  # (camera-space depth along the view axis == t for this ray)


def box_quads(x0, y0, z0, x1, y1, z1):
    c = lambda x, y, z: np.array([x, y, z], float)  # noqa: E731
    return [
        [c(x0, y1, z0), c(x1, y1, z0), c(x1, y1, z1), c(x0, y1, z1)],
        [c(x0, y0, z0), c(x1, y0, z0), c(x1, y1, z0), c(x0, y1, z0)],
        [c(x0, y0, z1), c(x1, y0, z1), c(x1, y1, z1), c(x0, y1, z1)],
        [c(x0, y0, z0), c(x0, y0, z1), c(x0, y1, z1), c(x0, y1, z0)],
        [c(x1, y0, z0), c(x1, y0, z1), c(x1, y1, z1), c(x1, y1, z0)],
    ]


def flat(cam, img, zbuf, quads, rgb):
    for q in quads:
        for ys, xs, depth, _ in raster(cam, cam.to_cam(np.array(q)), np.zeros((4, 1))):
            ok = depth < zbuf[ys, xs]
            img[ys[ok], xs[ok]] = rgb
            zbuf[ys[ok], xs[ok]] = depth[ok]


def render(view, t_seconds, height=240.0, seconds=60.0, size=(480, 360), seed=1):
    scale = height / MODEL_HEIGHT
    k = scale / SOURCE_SCALE
    lifetime = max(mcm.BALL_FOR + 40, int(seconds * 20))
    pos = np.array([0.0, 2.0 * k, 0.0])
    cam_pos, target, _ = VIEWS[view]
    f = height / 240.0
    cam = Camera(np.array(cam_pos) * f, np.array(target) * f, *size)
    w, h = size
    img = np.zeros((h, w, 3))
    zbuf = np.full((h, w), np.inf)
    ground_and_sky(cam, img, zbuf)
    # scale references: Godzilla's height (red post) and a player (blue), 150 blocks out towards the camera's left
    side = -cam.r * 150.0
    gx, gz = side[0], side[2]
    flat(cam, img, zbuf, box_quads(gx - 2, 0, gz - 2, gx + 2, GODZILLA_HEIGHT, gz + 2), [0.85, 0.15, 0.12])
    flat(cam, img, zbuf, box_quads(gx + 5, 0, gz - 0.3, gx + 5.6, 1.8, gz + 0.3), [0.15, 0.3, 0.9])

    t_ticks = t_seconds * 20.0
    rng = np.random.default_rng(seed)
    # the puffs (particles are drawn before the cloud): round blobs facing the camera
    for p, radius, tint, fire in puffs(t_ticks, lifetime, scale, pos, rng):
        c = cam.to_cam(p)
        if c[2] < cam.near:
            continue
        q = [p + (-cam.r - cam.u) * radius, p + (cam.r - cam.u) * radius, p + (cam.r + cam.u) * radius, p + (-cam.r + cam.u) * radius]
        uv = np.array([[-1, -1], [1, -1], [1, 1], [-1, 1]], float)
        for ys, xs, depth, at in raster(cam, cam.to_cam(np.array(q)), uv):
            rr = (at ** 2).sum(-1)
            ok = (rr < 1.0) & (depth < zbuf[ys, xs])
            shade = (0.7 + 0.3 * (1 - rr[ok]))[:, None] * (1.0 if fire else 0.8)
            img[ys[ok], xs[ok]] = np.clip(tint * shade, 0, 1)
            zbuf[ys[ok], xs[ok]] = depth[ok]

    tex, glow_tex = load("mushroom_cloud.png"), load("mushroom_cloud_glow.png")
    quads, alpha, glow = cloud_quads(t_ticks, lifetime, scale, pos)
    # 1) the texture, see-through, lit like an entity, sorted back to front
    order = sorted(range(len(quads)), key=lambda i: -np.linalg.norm(quads[i][0].mean(0) - cam.pos))
    top = np.full((h, w), -1)
    for i in order:
        corners, uvs, n, _ = quads[i]
        light = min(1.0, (max(0.0, n @ LIGHT0) + max(0.0, n @ LIGHT1)) * 0.6 + 0.4)
        for ys, xs, depth, uv in raster(cam, cam.to_cam(corners), uvs):
            s = sample(tex, uv)
            ok = (s[:, 3] * alpha >= 0.1) & (depth <= zbuf[ys, xs] + 1e-4)
            yy, xx = ys[ok], xs[ok]
            a = (s[ok, 3] * alpha)[:, None]
            img[yy, xx] = np.clip(s[ok, :3] * light * a + img[yy, xx] * (1 - a), 0, 1)
            zbuf[yy, xx] = depth[ok]
            top[yy, xx] = i
    # 2) and 3) glowing, added on where the first pass drew
    for i in range(len(quads)):
        corners, uvs, n, _ = quads[i]
        for ys, xs, depth, uv in raster(cam, cam.to_cam(corners), uvs):
            ok = top[ys, xs] == i
            if not ok.any():
                continue
            yy, xx = ys[ok], xs[ok]
            s = sample(tex, uv[ok])
            g = sample(glow_tex, uv[ok])
            add = s[:, :3] * (s[:, 3] * alpha)[:, None] + g[:, :3] * (g[:, 3] * glow * alpha)[:, None]
            img[yy, xx] = np.clip(img[yy, xx] + add, 0, 1)
    return (img * 255).astype(np.uint8), dict(alpha=alpha, glow=glow, scale=scale)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out")
    ap.add_argument("--height", type=float, default=240.0)
    ap.add_argument("--seconds", type=float, default=60.0)
    ap.add_argument("--size", default="400x300")
    ap.add_argument("--times", default="0.25,1,5,20,40,59.5")
    ap.add_argument("--views", default="side,three_quarter,below,above,far")
    a = ap.parse_args()
    w, h = (int(v) for v in a.size.split("x"))
    times = [float(t) for t in a.times.split(",")]
    views = a.views.split(",")
    label_h = 18
    sheet = Image.new("RGB", (w * len(views), (h + label_h) * len(times) + 22), (24, 26, 32))
    d = ImageDraw.Draw(sheet)
    d.text((6, 4), "Offline preview (not a game capture). Grid: 20-block squares, darker every 100. Red post: Godzilla's "
                   "height (45 blocks); blue: a player. Cloud %d blocks tall, %d s." % (a.height, a.seconds), fill=(220, 220, 230))
    for r, t in enumerate(times):
        for c, v in enumerate(views):
            frame, info = render(v, t, a.height, a.seconds, (w, h))
            y = 22 + r * (h + label_h)
            sheet.paste(Image.fromarray(frame), (c * w, y + label_h))
            d.text((c * w + 6, y + 3), "%.2f s, %s (glow %.2f, alpha %.2f)" % (t, VIEWS[v][2], info["glow"], info["alpha"]),
                   fill=(220, 220, 230))
            print("rendered %s at %.2f s" % (v, t), flush=True)
    sheet.save(a.out)
    print("wrote", a.out)


if __name__ == "__main__":
    main()
