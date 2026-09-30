"""Offline preview of the atomic blast: the flash, the fireball, the dust wave, the rising column, the mushroom cloud,
its cooling and its end, as client/render/NuclearCloud.java draws them (tools/nuclear_cloud_model.py holds the same
cloud), at given times and from given viewpoints, over a ground grid with Godzilla and a player for scale.

    python3 tools/preview_blast.py <out.png> [--height 240] [--size 400x300]
                                   [--times 0.15,2,8,20,60,135,172] [--views side,player,three_quarter,below,above]
                                   [--columns N]

Each frame is drawn the way the game draws the cloud: its puffs, camera-facing sprites from
textures/effect/nuclear_cloud.png, sorted back to front and blended over the scene (each puff's smoke tinted and lit,
glowing where it's hot), then the fire inside the hot ones, the flash and the fireball's glare added on top, and the
white-out of the flash over the whole view. Approximations: the sky and ground are plain and flat, the sky is lit as at
noon, and the distance haze is the preview's sky colour.
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nuclear_cloud_model as ncm  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHEET = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures", "effect", "nuclear_cloud.png")
GODZILLA_HEIGHT = 45.5
RENDER_DISTANCE = 12 * 16
HAZE = np.array([0.72, 0.8, 0.9])

# name: (camera position, looked-at point, label) for a cloud 240 blocks tall; scaled with --height
VIEWS = {
    "side": ((0.0, 3.0, -760.0), (0.0, 120.0, 0.0), "side, 760 blocks off, eye level"),
    "three_quarter": ((-430.0, 260.0, -430.0), (0.0, 105.0, 0.0), "three-quarters, 260 up"),
    "below": ((-160.0, 2.0, -170.0), (0.0, 210.0, 0.0), "from below, 230 off"),
    "above": ((-250.0, 620.0, -300.0), (0.0, 90.0, 0.0), "from above, 620 up"),
    "player": ((120.0, 2.0, -420.0), (0.0, 110.0, 0.0), "a player's eye, 440 off"),
    "far": ((0.0, 500.0, -2200.0), (0.0, 90.0, 0.0), "2.2 km off"),
}


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
        return np.array([d @ self.r, d @ self.u, d @ self.f])

    def ray(self):
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        dx = (xs + 0.5 - self.w / 2) / self.focal
        dy = -(ys + 0.5 - self.h / 2) / self.focal
        return dx, dy


def ground_and_sky(cam, img, zbuf):
    dx, dy = cam.ray()
    d = cam.f[None, None, :] + cam.r[None, None, :] * dx[..., None] + cam.u[None, None, :] * dy[..., None]
    elev = d[..., 1] / np.linalg.norm(d, axis=-1)
    sky = np.clip(elev * 1.6, 0, 1)[..., None]
    img[:] = np.array([0.40, 0.58, 0.86]) * sky + HAZE * (1 - sky)
    with np.errstate(divide="ignore", invalid="ignore"):
        t = -cam.pos[1] / d[..., 1]
    hit = (t > 0) & np.isfinite(t)
    gx = cam.pos[0] + d[..., 0] * t
    gz = cam.pos[2] + d[..., 2] * t
    thin, thick = t * 1.2 / cam.focal, t * 2.4 / cam.focal
    minor = (np.abs(((gx + 10) % 20) - 10) < thin) | (np.abs(((gz + 10) % 20) - 10) < thin)
    major = (np.abs(((gx + 50) % 100) - 50) < thick) | (np.abs(((gz + 50) % 100) - 50) < thick)
    col = np.where(major[..., None], [0.2, 0.3, 0.18], np.where(minor[..., None], [0.3, 0.42, 0.26], [0.42, 0.56, 0.34]))
    dist = t * np.linalg.norm(d, axis=-1)
    fog = np.clip((dist - RENDER_DISTANCE * 0.6) / (RENDER_DISTANCE * 3.0), 0, 0.85)[..., None]
    col = col * (1 - fog) + HAZE * fog
    img[hit] = col[hit]
    zbuf[hit] = t[hit]


def box(cam, img, zbuf, x0, y0, z0, x1, y1, z1, rgb):
    """An upright box, drawn by casting each pixel's ray at it (enough for the scale posts)."""
    dx, dy = cam.ray()
    d = cam.f[None, None, :] + cam.r[None, None, :] * dx[..., None] + cam.u[None, None, :] * dy[..., None]
    lo = np.array([x0, y0, z0]) - cam.pos
    hi = np.array([x1, y1, z1]) - cam.pos
    with np.errstate(divide="ignore", invalid="ignore"):
        t0 = lo / d
        t1 = hi / d
    tmin = np.nanmax(np.minimum(t0, t1), axis=-1)
    tmax = np.nanmin(np.maximum(t0, t1), axis=-1)
    hit = (tmax >= tmin) & (tmin > 0) & (tmin < zbuf)
    img[hit] = rgb
    zbuf[hit] = tmin[hit]


def load_sheet():
    return np.array(Image.open(SHEET).convert("RGBA")).astype(np.float64) / 255.0


def blit(cam, img, zbuf, sheet, pos, half, roll, sprite, rgb, alpha, additive=False, write_depth=True, aspect=1.0):
    """One camera-facing sprite: its cell of the sheet, turned by roll, tinted, blended over (or added to) the image."""
    c = cam.to_cam(pos)
    z = c[2]
    if z < cam.near:
        return
    # (the game fades a puff out as the camera comes inside it, and a squashed one is squashed only seen from the
    # side: from above it's round)
    dist = float(np.linalg.norm(pos - cam.pos))
    aspect = ncm.mix(aspect, 1.0, abs(pos[1] - cam.pos[1]) / max(dist, 1e-6))
    alpha *= ncm.smoothstep(0.35 * half, 1.2 * half, dist)
    if alpha <= 0.0:
        return
    sx = cam.w / 2 + c[0] / z * cam.focal
    sy = cam.h / 2 - c[1] / z * cam.focal
    r = half / z * cam.focal * math.sqrt(2.0)
    x0, x1 = int(max(0, math.floor(sx - r))), int(min(cam.w - 1, math.ceil(sx + r)))
    y0, y1 = int(max(0, math.floor(sy - r))), int(min(cam.h - 1, math.ceil(sy + r)))
    if x0 > x1 or y0 > y1:
        return
    ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
    px = (xs + 0.5 - sx) / (half / z * cam.focal)
    py = (ys + 0.5 - sy) / (half / z * cam.focal)
    cr, sr = math.cos(roll), math.sin(roll)
    u = cr * px + sr * py
    v = (-sr * px + cr * py) / aspect
    inside = (np.abs(u) <= 1.0) & (np.abs(v) <= 1.0)
    if not inside.any():
        return
    ys, xs, u, v = ys[inside], xs[inside], u[inside], v[inside]
    row, col = divmod(sprite, 4)
    tx = np.clip(((u + 1) * 0.5 * 64).astype(int), 0, 63) + col * 64
    ty = np.clip(((v + 1) * 0.5 * 64).astype(int), 0, 63) + row * 64
    s = sheet[ty, tx]
    a = s[:, 3] * alpha
    ok = (a > 0.0) & (z <= zbuf[ys, xs] + 1e-3)
    ys, xs, s, a = ys[ok], xs[ok], s[ok], a[ok]
    if additive:
        img[ys, xs] = img[ys, xs] + s[:, :3] * rgb * a[:, None]
    else:
        img[ys, xs] = s[:, :3] * rgb * a[:, None] + img[ys, xs] * (1 - a[:, None])
        if write_depth:
            zbuf[ys, xs] = z


def haze(cam, pos, rgb):
    dist = float(np.linalg.norm(pos - cam.pos))
    k = ncm.smoothstep(RENDER_DISTANCE, 6.0 * RENDER_DISTANCE, dist) * 0.5
    return rgb * (1 - k) + HAZE * k


def render(view, t, settings, size=(400, 300), seed=7):
    cloud = ncm.Cloud(settings, seed)
    cam_pos, target, _ = VIEWS[view]
    f = settings.height / 240.0
    cam = Camera(np.array(cam_pos) * f, np.array(target) * f, *size)
    w, h = size
    img = np.zeros((h, w, 3))
    zbuf = np.full((h, w), np.inf)
    ground_and_sky(cam, img, zbuf)
    # scale references: Godzilla's height (red post) and a player (blue), off to one side between the camera and
    # ground zero
    side = -cam.r * 150.0 * f + np.array([cam.pos[0], 0.0, cam.pos[2]]) * 0.35
    gx, gz = side[0], side[2]
    box(cam, img, zbuf, gx - 3, 0, gz - 3, gx + 3, GODZILLA_HEIGHT, gz + 3, [0.85, 0.15, 0.12])
    px, pz = cam.pos[0] * 0.92 + cam.r[0] * 6, cam.pos[2] * 0.92 + cam.r[2] * 6
    box(cam, img, zbuf, px - 0.3, 0, pz - 0.3, px + 0.3, 1.8, pz + 0.3, [0.15, 0.3, 0.9])

    sheet = load_sheet()
    puffs = cloud.build(t)
    extras = cloud.extras(t)
    # 1) the smoke, and the fireball's surface, sorted back to front
    drawn = []
    for (pos, half, aspect, roll, sprite, smoke, hot, hv, alpha, glow_sprite, glow_rgb) in puffs:
        rgb = haze(cam, pos, smoke * (1 - hv)) + hot * hv
        drawn.append((float(np.linalg.norm(pos - cam.pos)), pos, half, aspect, roll, sprite, rgb, alpha))
    for (pos, half, sprite, rgb, additive, alpha) in extras:
        if not additive:
            drawn.append((float(np.linalg.norm(pos - cam.pos)), pos, half, 1.0, 0.0, sprite, rgb, alpha))
    drawn.sort(key=lambda d: -d[0])
    for _, pos, half, aspect, roll, sprite, rgb, alpha in drawn:
        blit(cam, img, zbuf, sheet, pos, half, roll, sprite, rgb, alpha, aspect=aspect)
    # 2) added on top: the fire in the hot puffs, the flash and the fireball's glare
    for (pos, half, aspect, roll, sprite, smoke, hot, hv, alpha, glow_sprite, glow_rgb) in puffs:
        if glow_sprite is not None and glow_rgb is not None and glow_rgb.max() > 0.01:
            blit(cam, img, zbuf, sheet, pos, half, roll, glow_sprite, glow_rgb * 1.6, alpha, additive=True, aspect=aspect)
    for (pos, half, sprite, rgb, additive, alpha) in extras:
        if additive:
            blit(cam, img, zbuf, sheet, pos, half, 0.0, sprite, rgb, alpha, additive=True)
    # 3) the flash whiting out the view
    white = screen_flash(cloud, cam, t)
    if white > 0:
        tint = ncm.mix(ncm.FLASH_YELLOW, np.ones(3), white)
        img = img * (1 - white) + tint * white
    return (np.clip(img, 0, 1) * 255).astype(np.uint8), dict(heat=cloud.heat(t), flash=cloud.flash(t), white=white)


def screen_flash(cloud, cam, t):
    """ClientFx's flash: brighter the nearer the blast and the more directly you face it."""
    H = cloud.s.height
    f = cloud.flash(t) * cloud.s.flash_strength
    if f <= 0:
        return 0.0
    to = np.array([0.0, 0.05 * H, 0.0]) - cam.pos
    dist = np.linalg.norm(to)
    facing = 0.35 + 0.65 * max(0.0, float(to @ cam.f) / dist)
    near = ncm.clamp(1.25 - dist / (8.0 * H))
    return min(0.95, f * facing * near)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out")
    ap.add_argument("--height", type=float, default=240.0)
    ap.add_argument("--size", default="400x300")
    ap.add_argument("--times", default="0.15,2,8,20,60,135,172")
    ap.add_argument("--views", default="side,player,three_quarter,below,above")
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--columns", type=int, default=0, help="frames per row (default: one per view)")
    a = ap.parse_args()
    settings = ncm.Settings(height=a.height)
    w, h = (int(v) for v in a.size.split("x"))
    times = [float(t) for t in a.times.split(",")]
    views = a.views.split(",")
    label_h = 18
    cells = [(t, v) for t in times for v in views]
    columns = a.columns or len(views)
    rows = (len(cells) + columns - 1) // columns
    sheet = Image.new("RGB", (w * columns, (h + label_h) * rows + 22), (24, 26, 32))
    d = ImageDraw.Draw(sheet)
    d.text((6, 4), "Offline preview (not a game capture). Grid: 20-block squares, darker every 100. Red post: Godzilla "
                   "(45 blocks); blue: a player. Cloud %d blocks tall." % a.height, fill=(220, 220, 230))
    for i, (t, v) in enumerate(cells):
        r, c = divmod(i, columns)
        frame, info = render(v, t, settings, (w, h), a.seed)
        y = 22 + r * (h + label_h)
        sheet.paste(Image.fromarray(frame), (c * w, y + label_h))
        d.text((c * w + 6, y + 3), "%s s, %s" % (("%.2f" % t).rstrip("0").rstrip("."), VIEWS[v][2]), fill=(220, 220, 230))
        print("rendered %s at %.2f s (heat %.2f, flash %.2f, white-out %.2f)" % (v, t, info["heat"], info["flash"], info["white"]), flush=True)
    sheet.save(a.out)
    print("wrote", a.out)


if __name__ == "__main__":
    main()
