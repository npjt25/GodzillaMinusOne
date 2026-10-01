"""Tiny software renderer for Bedrock/GeckoLib geometry files.

Used to preview entity models + textures outside of Minecraft (e.g. to check
the Godzilla recolour). Orthographic (or, with a camera, perspective), z-buffered, flat-shaded; glowing parts
(overlays: emissive textures on some bones) are added on top, with a soft bloom.

    python3 tools/render_preview.py <geo.json> <texture.png> <out.png> [yaw] [pitch] [size]
"""
import json
import math
import sys

import numpy as np
from PIL import Image


def rot_matrix(rx, ry, rz):
    """GeckoLib order: matrix = Rz * Ry * Rx (radians)."""
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def affine(R=None, t=None):
    M = np.eye(4)
    if R is not None:
        M[:3, :3] = R
    if t is not None:
        M[:3, 3] = t
    return M


def about_pivot(pivot, R):
    p = np.asarray(pivot, dtype=float)
    return affine(t=p) @ affine(R=R) @ affine(t=-p)


def load_geo(path):
    data = json.load(open(path))
    geo = data["minecraft:geometry"][0]
    desc = geo["description"]
    return geo["bones"], desc.get("texture_width", 64), desc.get("texture_height", 64)


def bone_matrices(bones, pose=None):
    """World matrices per bone in GeckoLib space (x negated, units of pixels)."""
    pose = pose or {}
    by_name = {b["name"]: b for b in bones}
    cache = {}

    def get(name):
        if name in cache:
            return cache[name]
        b = by_name[name]
        parent = get(b["parent"]) if b.get("parent") in by_name else np.eye(4)
        px, py, pz = b.get("pivot", [0, 0, 0])
        rx, ry, rz = b.get("rotation", [0, 0, 0])
        ax, ay, az = pose.get(name, {}).get("rotation", [0, 0, 0])
        tx, ty, tz = pose.get(name, {}).get("position", [0, 0, 0])
        sx, sy, sz = pose.get(name, {}).get("scale", [1, 1, 1])
        R = rot_matrix(math.radians(-(rx + ax)), math.radians(-(ry + ay)), math.radians(rz + az))
        piv = np.array([-px, py, pz], dtype=float)
        M = parent @ affine(t=np.array([-tx, ty, tz])) @ affine(t=piv) @ affine(R=R) @ np.diag([sx, sy, sz, 1]) @ affine(t=-piv)
        cache[name] = M
        return M

    for b in bones:
        get(b["name"])
    return cache


def face_list(cube, tw, th):
    """Yield (corners[4] in local space, uv rect corners[4]) per face."""
    ox, oy, oz = cube["origin"]
    w, h, d = cube["size"]
    inf = cube.get("inflate", 0)
    x0, x1 = -(ox + w) - inf, -ox + inf
    y0, y1 = oy - inf, oy + h + inf
    z0, z1 = oz - inf, oz + d + inf
    uv = cube.get("uv", [0, 0])
    mirror = cube.get("mirror", False)
    faces = {
        # name: corners (top-left, top-right, bottom-right, bottom-left) as seen from outside
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],
        "down": [(x1, y0, z0), (x0, y0, z0), (x0, y0, z1), (x1, y0, z1)],
    }
    if isinstance(uv, dict):
        rects = {}
        for name, f in uv.items():
            if not f:
                continue
            (u, v), (uw, vh) = f["uv"], f.get("uv_size", [0, 0])
            rects[name] = (u, v, uw, vh)
    else:
        u, v = uv
        fw, fh, fd = math.floor(w), math.floor(h), math.floor(d)
        rects = {
            "west": (u + fd + fw, v + fd, fd, fh),
            "east": (u, v + fd, fd, fh),
            "north": (u + fd, v + fd, fw, fh),
            "south": (u + fd + fw + fd, v + fd, fw, fh),
            "up": (u + fd, v, fw, fd),
            "down": (u + fd + fw, v + fd, fw, -fd),
        }
        if mirror:
            rects["east"], rects["west"] = rects["west"], rects["east"]
    for name, corners in faces.items():
        if name not in rects:
            continue
        u, v, uw, vh = rects[name]
        if mirror and not isinstance(uv, dict):
            u, uw = u + uw, -uw
        uvs = [(u, v), (u + uw, v), (u + uw, v + vh), (u, v + vh)]
        yield name, np.array(corners, dtype=float), np.array(uvs, dtype=float) / np.array([tw, th])


def _molang(expr, t):
    """The little Molang the animations use: numbers, + - * /, parentheses, math.sin/cos (degrees) and
    query.anim_time."""
    e = expr.lower().replace("query.anim_time", "(%r)" % t).replace("q.anim_time", "(%r)" % t)
    e = e.replace("math.sin", "_sin").replace("math.cos", "_cos")
    if not all(ch in "0123456789.+-*/() _sinco" for ch in e):
        raise ValueError("unsupported Molang: " + expr)
    return float(eval(e, {"__builtins__": {}}, {"_sin": lambda d: math.sin(math.radians(d)),
                                                 "_cos": lambda d: math.cos(math.radians(d))}))


def _vec(v, t=0.0, side="post"):
    if isinstance(v, dict):
        v = v.get(side, v.get("post", v.get("pre", v.get("vector"))))
    if isinstance(v, (int, float, str)):
        v = [v] * 3
    return [_molang(x, t) if isinstance(x, str) else float(x) for x in v]


def _sample(channel, t):
    """Keyframe sampling as in Bedrock/GeckoLib: linear, or Catmull-Rom where a key asks for it."""
    if not isinstance(channel, dict):
        return _vec(channel, t)
    keys = sorted((float(k), v) for k, v in channel.items())
    if t <= keys[0][0]:
        return _vec(keys[0][1], t, "pre")
    for i, ((t0, v0), (t1, v1)) in enumerate(zip(keys, keys[1:])):
        if t0 <= t <= t1:
            a, b = np.array(_vec(v0, t)), np.array(_vec(v1, t, "pre"))
            f = (t - t0) / max(t1 - t0, 1e-9)
            cat = any(isinstance(v, dict) and v.get("lerp_mode") == "catmullrom" for v in (v0, v1))
            if not cat:
                return list(a + (b - a) * f)
            p0 = np.array(_vec(keys[i - 1][1], t)) if i > 0 else a
            p3 = np.array(_vec(keys[i + 2][1], t, "pre")) if i + 2 < len(keys) else b
            return list(0.5 * (2 * a + (-p0 + b) * f + (2 * p0 - 5 * a + 4 * b - p3) * f * f
                               + (-p0 + 3 * a - 3 * b + p3) * f ** 3))
    return _vec(keys[-1][1], t)


def animation_pose(anim_path, name, t):
    """Pose dict for bone_matrices() from a Bedrock/GeckoLib animation at time t (seconds)."""
    anim = json.load(open(anim_path))["animations"][name]
    pose = {}
    for bone, channels in anim.get("bones", {}).items():
        pose[bone] = {c: _sample(v, t) for c, v in channels.items() if c in ("rotation", "position", "scale")}
    return pose


def scale_props(scale, ground, player_at=None):
    """Quads for a scale reference, in the model's units (scale: its renderer scale): a checkerboard of one-block
    squares over ground = (x0, x1, z0, z1) blocks, and a player-sized figure (0.6 x 1.8 x 0.6 blocks, blue) standing
    at player_at = (x, z) blocks. For render(props=...)."""
    px = 16.0 / scale
    quads = []
    x0, x1, z0, z1 = ground
    for i in range(int(x0), int(x1)):
        for k in range(int(z0), int(z1)):
            c = (0.42, 0.5, 0.36) if (i + k) % 2 == 0 else (0.36, 0.44, 0.31)
            if i % 10 == 0 or k % 10 == 0:
                c = tuple(v * 0.8 for v in c)
            a, b, e, f = i * px, (i + 1) * px, k * px, (k + 1) * px
            quads.append((np.array([(a, 0, f), (b, 0, f), (b, 0, e), (a, 0, e)], float), c))
    if player_at:
        x, z = player_at
        lo = np.array([x - 0.3, 0.0, z - 0.3]) * px
        hi = np.array([x + 0.3, 1.8, z + 0.3]) * px
        (x0, x1), (y0, y1), (z0, z1) = (lo[0], hi[0]), (lo[1], hi[1]), (lo[2], hi[2])
        faces = [[(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)], [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
                 [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
                 [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)]]
        for fc in faces:
            quads.append((np.array(fc, float), (0.25, 0.45, 0.95)))
    return quads


def look_at(eye, target, up=(0.0, 1.0, 0.0)):
    """Rows: the camera's right, up and forward (into the picture), for render(camera=...)."""
    f = np.asarray(target, float) - np.asarray(eye, float)
    f /= np.linalg.norm(f)
    r = np.cross(f, up)
    r /= np.linalg.norm(r)
    return np.array([r, np.cross(r, f), f])


def render(geo_path, tex_path, out_path, yaw=150.0, pitch=15.0, size=900, pose=None, bg=(34, 38, 46),
           hide=(), extra_scale=1.0, flat_bones=None, fit_bones=None, props=None, fit_props=False,
           camera=None, overlays=(), bloom=0.0, width=None):
    """camera: {"eye": (x, y, z), "target": (x, y, z), "fov": degrees} in the model's space (pixels, x negated as
    in bone_matrices) for a perspective view instead of the orthographic yaw/pitch one (which fits the model to the
    picture). overlays: (texture path, bone names, brightness) glowing parts, added on top of the model where the
    texture isn't clear; bloom: how much of that glow spreads round it. width: a wider picture (default square)."""
    bones, tw, th = load_geo(geo_path)
    width = width or size
    glow_tex = [(np.array(Image.open(path).convert("RGBA")).astype(float) / 255.0, set(names), gain)
                for path, names, gain in overlays]
    tex = np.array(Image.open(tex_path).convert("RGBA")).astype(float) / 255.0
    TH, TW = tex.shape[:2]
    mats = bone_matrices(bones, pose)

    view = rot_matrix(math.radians(-pitch), 0, 0) @ rot_matrix(0, math.radians(yaw), 0)   # turn, then look down
    quads = []
    for b in bones:
        if b["name"] in hide:
            continue
        M = mats[b["name"]]
        for c in b.get("cubes", []):
            C = M
            if "rotation" in c:
                px, py, pz = c.get("pivot", [0, 0, 0])
                rx, ry, rz = c["rotation"]
                C = M @ about_pivot([-px, py, pz], rot_matrix(math.radians(-rx), math.radians(-ry), math.radians(rz)))
            for name, corners, uvs in face_list(c, tw, th):
                pts = (C @ np.c_[corners, np.ones(4)].T).T[:, :3]
                quads.append((pts, uvs, (flat_bones or {}).get(b["name"]), b["name"]))
    # (props are wound the other way round from the cubes' faces, whose normals point inwards)
    prop_quads = [(np.asarray(corners)[::-1], None, rgb, "#prop") for corners, rgb in (props or [])]
    fit = [q[0] for q in quads if not fit_bones or q[3] in fit_bones] + ([q[0] for q in prop_quads] if fit_props else [])
    quads += prop_quads
    if camera is None:
        allpts = np.concatenate(fit)
        vp = (view @ allpts.T).T
        lo, hi = vp.min(0), vp.max(0)
        scale = (size * 0.9) / max(hi[0] - lo[0], hi[1] - lo[1]) * extra_scale
        center = (lo + hi) / 2
    else:
        eye = np.asarray(camera["eye"], float)
        cam = look_at(eye, camera["target"])
        focal = (size / 2) / math.tan(math.radians(camera.get("fov", 50.0)) / 2)

    img = np.zeros((size, width, 3))
    if np.ndim(bg) == 2:                             # a gradient, top to bottom
        top, bottom = np.array(bg[0]) / 255.0, np.array(bg[1]) / 255.0
        img[:] = top + (bottom - top) * np.linspace(0, 1, size)[:, None, None]
    else:
        img[:] = np.array(bg) / 255.0
    zbuf = np.full((size, width), -np.inf)
    glow = np.zeros((size, width, 3))
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)

    for pts, uvs, flat, bone in quads:
        n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        if camera is None:
            p = (view @ pts.T).T
            sx = (p[:, 0] - center[0]) * scale + width / 2
            sy = -(p[:, 1] - center[1]) * scale + size / 2
            # back-face culling via the (inward) normal: the viewer looks along +z
            if (view @ n)[2] < 0:
                continue
        else:
            p = (cam @ (pts - eye).T).T
            if (p[:, 2] < 1.0).any() or n @ (pts[0] - eye) < 0:
                continue                             # behind the camera, or facing away from it (n points inwards)
            sx = width / 2 + focal * p[:, 0] / p[:, 2]
            sy = size / 2 - focal * p[:, 1] / p[:, 2]
        xmin, xmax = int(max(0, np.floor(sx.min()))), int(min(width - 1, np.ceil(sx.max())))
        ymin, ymax = int(max(0, np.floor(sy.min()))), int(min(size - 1, np.ceil(sy.max())))
        if xmin > xmax or ymin > ymax:
            continue
        X, Y = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
        # parallelogram param: P = P0 + a*(P1-P0) + b*(P3-P0)
        if camera is None:
            e1 = np.array([sx[1] - sx[0], sy[1] - sy[0]])
            e2 = np.array([sx[3] - sx[0], sy[3] - sy[0]])
            det = e1[0] * e2[1] - e1[1] * e2[0]
            if abs(det) < 1e-6:
                continue
            dx, dy = X - sx[0], Y - sy[0]
            a = (dx * e2[1] - dy * e2[0]) / det
            bcoef = (e1[0] * dy - e1[1] * dx) / det
            z = p[:, 2]
            depth = -(z[0] + a * (z[1] - z[0]) + bcoef * (z[3] - z[0]))
        else:
            # where each pixel's ray meets the face's plane
            e1, e2 = p[1] - p[0], p[3] - p[0]
            pn = np.cross(e1, e2)
            d = np.stack([(X - width / 2) / focal, -(Y - size / 2) / focal, np.ones_like(X)], -1)
            dn = d @ pn
            if np.abs(dn).max() < 1e-9:
                continue
            with np.errstate(divide="ignore", invalid="ignore"):
                t = (pn @ p[0]) / dn
            hit = d * t[..., None] - p[0]
            u1, u2 = np.cross(e2, pn), np.cross(pn, e1)
            a = (hit @ u1) / (e1 @ u1)
            bcoef = (hit @ u2) / (e2 @ u2)
            depth = -t
        inside = (a >= 0) & (a <= 1) & (bcoef >= 0) & (bcoef <= 1)
        if not inside.any():
            continue
        if uvs is None:
            col = np.empty(a.shape + (4,))
            col[...] = list(flat) + [1.0]
        else:
            u = uvs[0, 0] + a * (uvs[1, 0] - uvs[0, 0]) + bcoef * (uvs[3, 0] - uvs[0, 0])
            v = uvs[0, 1] + a * (uvs[1, 1] - uvs[0, 1]) + bcoef * (uvs[3, 1] - uvs[0, 1])
            tx = np.clip((u * TW).astype(int), 0, TW - 1)
            ty = np.clip((v * TH).astype(int), 0, TH - 1)
            col = tex[ty, tx]
            if flat is not None:
                col = np.empty(col.shape)
                col[...] = np.array(list(flat) + [255]) / 255.0
        visible = inside & (col[..., 3] > 0.1)
        sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
        win = visible & (depth > sub)
        if not win.any():
            continue
        # world-space normal for lighting
        wn = np.cross(pts[1] - pts[0], pts[3] - pts[0])
        wn /= max(np.linalg.norm(wn), 1e-9)
        shade = 1.0 if flat is not None and uvs is not None else 0.55 + 0.45 * max(0.0, float(-wn @ light))
        if uvs is None and abs(wn[1]) > 0.99:
            shade = 1.0                              # the ground: flat, unshaded
        rgb = col[..., :3] * shade
        img[ymin:ymax + 1, xmin:xmax + 1][win] = rgb[win]
        sub[win] = depth[win]
        lit = np.zeros(rgb.shape)
        if uvs is not None:
            for gtex, names, gain in glow_tex:
                if bone in names:
                    g = gtex[ty, tx]
                    lit += np.where(g[..., 3:] > 0.1, g[..., :3] * gain, 0.0)
        glow[ymin:ymax + 1, xmin:xmax + 1][win] = lit[win]
    img += glow
    if bloom > 0 and glow.any():
        from scipy.ndimage import gaussian_filter
        spread = size / 90.0
        img += bloom * (gaussian_filter(glow, (spread, spread, 0)) * 1.6 + gaussian_filter(glow, (spread * 4, spread * 4, 0)))
    Image.fromarray((np.clip(img, 0, 1) * 255).astype("uint8")).save(out_path)


if __name__ == "__main__":
    args = sys.argv[1:]
    render(args[0], args[1], args[2], *(float(a) for a in args[3:5]), *(int(a) for a in args[5:6]))
