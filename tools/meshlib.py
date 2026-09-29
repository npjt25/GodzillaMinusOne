"""Small helpers for the high-detail Godzilla mesh: glTF loading and a software triangle rasterizer.

Used by tools/build_godzilla_mesh.py (which rigs the mesh onto the add-on's skeleton) and for previews.
"""
import json
import math
import os
import struct

import numpy as np
from PIL import Image

_COMPONENT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
_NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def _node_matrix(node):
    if "matrix" in node:
        return np.array(node["matrix"], dtype=float).reshape(4, 4).T
    m = np.eye(4)
    if "scale" in node:
        m = np.diag(list(node["scale"]) + [1.0]) @ m
    if "rotation" in node:
        x, y, z, w = node["rotation"]
        r = np.array([
            [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])
        rm = np.eye(4)
        rm[:3, :3] = r
        m = rm @ m
    if "translation" in node:
        t = np.eye(4)
        t[:3, 3] = node["translation"]
        m = t @ m
    return m


def load_gltf(path):
    """Returns a list of primitives: dict(pos, normal, uv, idx, material, texture) in scene space."""
    base = os.path.dirname(path)
    g = json.load(open(path))
    buffers = [open(os.path.join(base, b["uri"]), "rb").read() for b in g["buffers"]]

    def accessor(i):
        a = g["accessors"][i]
        view = g["bufferViews"][a["bufferView"]]
        data = buffers[view["buffer"]]
        dtype = _COMPONENT[a["componentType"]]
        n = _NCOMP[a["type"]]
        offset = view.get("byteOffset", 0) + a.get("byteOffset", 0)
        stride = view.get("byteStride", 0)
        item = np.dtype(dtype).itemsize * n
        if stride and stride != item:
            raw = np.frombuffer(data, dtype=np.uint8, count=stride * (a["count"] - 1) + item, offset=offset)
            rows = np.lib.stride_tricks.as_strided(raw, shape=(a["count"], item), strides=(stride, 1))
            arr = np.frombuffer(rows.copy().tobytes(), dtype=dtype).reshape(a["count"], n)
        else:
            arr = np.frombuffer(data, dtype=dtype, count=a["count"] * n, offset=offset).reshape(a["count"], n)
        return arr.astype(np.float64) if dtype == np.float32 else arr.astype(np.int64)

    prims = []

    def visit(ni, parent):
        node = g["nodes"][ni]
        m = parent @ _node_matrix(node)
        if "mesh" in node:
            for p in g["meshes"][node["mesh"]]["primitives"]:
                pos = accessor(p["attributes"]["POSITION"])
                nrm = accessor(p["attributes"]["NORMAL"])
                uv = accessor(p["attributes"]["TEXCOORD_0"])
                idx = accessor(p["indices"]).reshape(-1, 3)
                pos = (m @ np.c_[pos, np.ones(len(pos))].T).T[:, :3]
                nm = np.linalg.inv(m[:3, :3]).T
                nrm = (nm @ nrm.T).T
                nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
                mat = g["materials"][p["material"]]
                tex = mat["pbrMetallicRoughness"]["baseColorTexture"]["index"]
                image = g["images"][g["textures"][tex]["source"]]["uri"]
                prims.append(dict(pos=pos, normal=nrm, uv=uv, idx=idx, material=mat["name"],
                                  texture=os.path.join(base, image)))
        for c in node.get("children", []):
            visit(c, m)

    for root in g["scenes"][g.get("scene", 0)]["nodes"]:
        visit(root, np.eye(4))
    return prims


def rot_y(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_x(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rasterize(meshes, out_path, yaw=150.0, pitch=12.0, size=900, bg=(34, 38, 46), fit=None, tint=None):
    """meshes: list of (pos[N,3], normal[N,3], uv[N,2], tris[M,3], texture array HxWx3 (0..1) or rgb tuple,
    optional per-vertex rgb multiplier/additive). Orthographic, z-buffered, Lambert shaded."""
    view = rot_x(pitch) @ rot_y(yaw)
    allp = np.concatenate([(view @ m[0].T).T for m in meshes]) if fit is None else (view @ fit.T).T
    lo, hi = allp.min(0), allp.max(0)
    scale = (size * 0.9) / max(hi[0] - lo[0], hi[1] - lo[1])
    center = (lo + hi) / 2
    img = np.zeros((size, size, 3))
    img[:] = np.array(bg) / 255.0
    zbuf = np.full((size, size), -np.inf)
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)
    for mesh in meshes:
        pos, nrm, uv, tris, tex = mesh[:5]
        glow = mesh[5] if len(mesh) > 5 else None
        vcol = mesh[6] if len(mesh) > 6 else None
        p = (view @ pos.T).T
        sx = (p[:, 0] - center[0]) * scale + size / 2
        sy = -(p[:, 1] - center[1]) * scale + size / 2
        sz = p[:, 2]
        shade_v = 0.45 + 0.55 * np.clip(np.abs(nrm @ light), 0, 1)
        is_img = isinstance(tex, np.ndarray)
        if vcol is not None:
            is_img = False
        if is_img:
            th, tw = tex.shape[:2]
        for t in tris:
            a, b, c = t
            x0, y0, x1, y1, x2, y2 = sx[a], sy[a], sx[b], sy[b], sx[c], sy[c]
            xmin = int(max(0, math.floor(min(x0, x1, x2))))
            xmax = int(min(size - 1, math.ceil(max(x0, x1, x2))))
            ymin = int(max(0, math.floor(min(y0, y1, y2))))
            ymax = int(min(size - 1, math.ceil(max(y0, y1, y2))))
            if xmin > xmax or ymin > ymax:
                continue
            det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if abs(det) < 1e-9:
                continue
            X, Y = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
            w0 = ((y1 - y2) * (X - x2) + (x2 - x1) * (Y - y2)) / det
            w1 = ((y2 - y0) * (X - x2) + (x0 - x2) * (Y - y2)) / det
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            depth = -(w0 * sz[a] + w1 * sz[b] + w2 * sz[c])
            sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
            win = inside & (depth > sub)
            if not win.any():
                continue
            if is_img:
                u = w0 * uv[a, 0] + w1 * uv[b, 0] + w2 * uv[c, 0]
                v = w0 * uv[a, 1] + w1 * uv[b, 1] + w2 * uv[c, 1]
                tx = np.mod((u * tw).astype(int), tw)
                ty = np.mod((v * th).astype(int), th)
                col = tex[ty, tx]
            elif vcol is not None:
                col = (w0[..., None] * vcol[a] + w1[..., None] * vcol[b] + w2[..., None] * vcol[c])
            else:
                col = np.broadcast_to(np.array(tex, dtype=float), win.shape + (3,))
            shade = (w0 * shade_v[a] + w1 * shade_v[b] + w2 * shade_v[c])[..., None]
            rgb = col * shade
            if glow is not None:
                gl = (w0 * glow[a] + w1 * glow[b] + w2 * glow[c])[..., None]
                rgb = np.clip(rgb + gl * np.array([0.35, 0.75, 1.0]), 0, 1)
            region = img[ymin:ymax + 1, xmin:xmax + 1]
            region[win] = rgb[win]
            sub[win] = depth[win]
    Image.fromarray((np.clip(img, 0, 1) * 255).astype("uint8")).save(out_path)
    return view, center, scale


def overlay_grid(path, view, center, scale, size, h_axis, v_axis, step=10, marks=()):
    """Draws model-space grid lines on a render made with an axis-aligned view (yaw 0/90/180/270, pitch 0)."""
    from PIL import ImageDraw
    img = Image.open(path).convert("RGB")
    d = ImageDraw.Draw(img)

    def to_screen(p):
        q = view @ np.asarray(p, dtype=float)
        return ((q[0] - center[0]) * scale + size / 2, -(q[1] - center[1]) * scale + size / 2)

    for g in range(-300, 301, step):
        p = np.zeros(3)
        p[h_axis] = g
        x = to_screen(p)[0]
        if 0 <= x < size:
            d.line([(x, 0), (x, size)], fill=(90, 90, 130) if g % 50 else (150, 150, 200))
            d.text((x + 2, 2), str(g), fill=(200, 200, 255))
        p = np.zeros(3)
        p[v_axis] = g
        y = to_screen(p)[1]
        if 0 <= y < size:
            d.line([(0, y), (size, y)], fill=(90, 90, 130) if g % 50 else (150, 150, 200))
            d.text((2, y - 11), str(g), fill=(200, 200, 255))
    for name, pos in marks:
        x, y = to_screen(pos)
        d.ellipse([x - 5, y - 5, x + 5, y + 5], outline=(255, 255, 0), width=2)
        d.text((x + 6, y - 6), name, fill=(255, 255, 0))
    img.save(path)
