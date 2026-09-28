"""Tiny software renderer for Bedrock/GeckoLib geometry files.

Used to preview entity models + textures outside of Minecraft (e.g. to check
the Godzilla recolour). Orthographic, z-buffered, flat-shaded.

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


def render(geo_path, tex_path, out_path, yaw=150.0, pitch=15.0, size=900, pose=None, bg=(34, 38, 46),
           hide=(), extra_scale=1.0):
    bones, tw, th = load_geo(geo_path)
    tex = np.array(Image.open(tex_path).convert("RGBA")).astype(float) / 255.0
    TH, TW = tex.shape[:2]
    mats = bone_matrices(bones, pose)

    view = rot_matrix(math.radians(pitch), math.radians(yaw), 0)
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
                quads.append((pts, uvs))
    allpts = np.concatenate([q[0] for q in quads])
    vp = (view @ allpts.T).T
    lo, hi = vp.min(0), vp.max(0)
    scale = (size * 0.9) / max(hi[0] - lo[0], hi[1] - lo[1]) * extra_scale
    center = (lo + hi) / 2

    img = np.zeros((size, size, 3))
    img[:] = np.array(bg) / 255.0
    zbuf = np.full((size, size), -np.inf)
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)

    for pts, uvs in quads:
        p = (view @ pts.T).T
        sx = (p[:, 0] - center[0]) * scale + size / 2
        sy = -(p[:, 1] - center[1]) * scale + size / 2
        z = p[:, 2]
        # parallelogram param: P = P0 + a*(P1-P0) + b*(P3-P0)
        e1 = np.array([sx[1] - sx[0], sy[1] - sy[0]])
        e2 = np.array([sx[3] - sx[0], sy[3] - sy[0]])
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        # back-face culling via the normal (cull faces pointing away from the viewer)
        n = np.cross(p[1] - p[0], p[3] - p[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        if n[2] > 0:  # viewer looks along +z in view space
            continue
        xmin, xmax = int(max(0, np.floor(sx.min()))), int(min(size - 1, np.ceil(sx.max())))
        ymin, ymax = int(max(0, np.floor(sy.min()))), int(min(size - 1, np.ceil(sy.max())))
        if xmin > xmax or ymin > ymax:
            continue
        X, Y = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
        dx, dy = X - sx[0], Y - sy[0]
        a = (dx * e2[1] - dy * e2[0]) / det
        bcoef = (e1[0] * dy - e1[1] * dx) / det
        inside = (a >= 0) & (a <= 1) & (bcoef >= 0) & (bcoef <= 1)
        if not inside.any():
            continue
        depth = -(z[0] + a * (z[1] - z[0]) + bcoef * (z[3] - z[0]))
        u = uvs[0, 0] + a * (uvs[1, 0] - uvs[0, 0]) + bcoef * (uvs[3, 0] - uvs[0, 0])
        v = uvs[0, 1] + a * (uvs[1, 1] - uvs[0, 1]) + bcoef * (uvs[3, 1] - uvs[0, 1])
        tx = np.clip((u * TW).astype(int), 0, TW - 1)
        ty = np.clip((v * TH).astype(int), 0, TH - 1)
        col = tex[ty, tx]
        visible = inside & (col[..., 3] > 0.1)
        sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
        win = visible & (depth > sub)
        if not win.any():
            continue
        # world-space normal for lighting
        wn = np.cross(pts[1] - pts[0], pts[3] - pts[0])
        wn /= max(np.linalg.norm(wn), 1e-9)
        shade = 0.55 + 0.45 * max(0.0, float(-wn @ light))
        rgb = col[..., :3] * shade
        img[ymin:ymax + 1, xmin:xmax + 1][win] = rgb[win]
        sub[win] = depth[win]
    Image.fromarray((np.clip(img, 0, 1) * 255).astype("uint8")).save(out_path)


if __name__ == "__main__":
    args = sys.argv[1:]
    render(args[0], args[1], args[2], *(float(a) for a in args[3:5]), *(int(a) for a in args[5:6]))
