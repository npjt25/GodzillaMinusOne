"""Poses the rigged Godzilla mesh with an animation frame and renders it (checks rig + weights offline).

    python3 tools/preview_godzilla.py <animation name> <time s> <out.png> [yaw] [pitch] [--bones]
"""
import json
import os
import struct
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meshlib  # noqa: E402
import render_preview as rp  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")


def read_mesh(path):
    with open(path, "rb") as f:
        data = f.read()
    o = 4
    (nb,) = struct.unpack_from(">i", data, o)
    o += 4
    bones = []
    for _ in range(nb):
        (ln,) = struct.unpack_from(">H", data, o)
        o += 2
        bones.append(data[o:o + ln].decode())
        o += ln
    (npl,) = struct.unpack_from(">i", data, o)
    o += 4
    plates = []
    for _ in range(npl):
        (ln,) = struct.unpack_from(">H", data, o)
        o += 2
        name = data[o:o + ln].decode()
        o += ln
        (t,) = struct.unpack_from(">f", data, o)
        o += 4
        plates.append((name, t))
    (nparts,) = struct.unpack_from(">i", data, o)
    o += 4
    parts = []
    dt = np.dtype([("x", ">f4"), ("y", ">f4"), ("z", ">f4"), ("nx", ">f4"), ("ny", ">f4"), ("nz", ">f4"),
                   ("u", ">f4"), ("v", ">f4"), ("b0", ">u1"), ("b1", ">u1"), ("w0", ">f4"), ("glow", ">i1")])
    for _ in range(nparts):
        (ln,) = struct.unpack_from(">H", data, o)
        o += 2
        tex = data[o:o + ln].decode()
        o += ln
        is_plate = data[o]
        o += 1
        (nv,) = struct.unpack_from(">i", data, o)
        o += 4
        rec = np.frombuffer(data, dtype=dt, count=nv, offset=o)
        o += nv * dt.itemsize
        (nt,) = struct.unpack_from(">i", data, o)
        o += 4
        tris = np.frombuffer(data, dtype=">i4", count=nt * 3, offset=o).reshape(-1, 3).astype(int)
        o += nt * 12
        parts.append(dict(texture=tex, plates=is_plate, rec=rec, tris=tris))
    return bones, plates, parts


def skinned(parts, bones, mats):
    out = []
    M = np.stack([mats[b] for b in bones])
    for p in parts:
        r = p["rec"]
        pos = np.c_[r["x"], r["y"], r["z"], np.ones(len(r))] * np.array([16, 16, 16, 1.0])
        nrm = np.c_[r["nx"], r["ny"], r["nz"]]
        b0, b1, w0 = r["b0"].astype(int), r["b1"].astype(int), r["w0"].astype(float)
        p0 = np.einsum("nij,nj->ni", M[b0], pos)[:, :3]
        p1 = np.einsum("nij,nj->ni", M[b1], pos)[:, :3]
        n0 = np.einsum("nij,nj->ni", M[b0][:, :3, :3], nrm)
        n1 = np.einsum("nij,nj->ni", M[b1][:, :3, :3], nrm)
        P = p0 * w0[:, None] + p1 * (1 - w0)[:, None]
        N = n0 * w0[:, None] + n1 * (1 - w0)[:, None]
        N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
        out.append((P, N, np.c_[r["u"], r["v"]], p["tris"], p))
    return out


def render(anim, t, out, yaw=150, pitch=12, size=900, bones_view=False, glow_time=None, anim_file=None):
    geo_bones, tw, th = rp.load_geo(os.path.join(A, "geo", "entity", "godzilla.geo.json"))
    pose = rp.animation_pose(anim_file or os.path.join(A, "animations", "entity", "godzilla.animation.json"), anim, t) if anim else {}
    mats = rp.bone_matrices(geo_bones, pose)
    bones, plates, parts = read_mesh(os.path.join(A, "godzilla_mesh", "godzilla.mesh"))
    meshes = []
    palette = np.random.RandomState(3).rand(len(bones), 3) * 0.8 + 0.2
    for P, N, UV, T, p in skinned(parts, bones, mats):
        if bones_view:
            col = palette[p["rec"]["b0"].astype(int)]
            meshes.append((P, N, UV, T, (0.8, 0.8, 0.8)))
            meshes[-1] = (P, N, UV, T, None, None, col)
        else:
            tex = np.array(Image.open(os.path.join(A, p["texture"].split(":", 1)[1])).convert("RGB")).astype(float) / 255
            glow = None
            if glow_time is not None and p["plates"]:
                g = p["rec"]["glow"].astype(int)
                times = np.array([pt for _, pt in plates])
                glow = np.clip((glow_time - times[g]) / 0.4, 0, 1)
            meshes.append((P, N, UV, T, tex, glow))
    # beam cubes (bone B) as flat cyan quads
    beam = [b for b in geo_bones if b["name"] == "B"][0]
    bp, bn, bt = [], [], []
    M = mats["B"]
    for c in beam.get("cubes", []):
        C = M
        if "rotation" in c:
            px, py, pz = c.get("pivot", [0, 0, 0])
            rx, ry, rz = c["rotation"]
            C = M @ rp.about_pivot([-px, py, pz], rp.rot_matrix(np.radians(-rx), np.radians(-ry), np.radians(rz)))
        for name, corners, uvs in rp.face_list(c, tw, th):
            q = (C @ np.c_[corners, np.ones(4)].T).T[:, :3]
            base = len(bp)
            bp.extend(q)
            n = np.cross(q[1] - q[0], q[3] - q[0])
            n = n / max(np.linalg.norm(n), 1e-9)
            bn.extend([n] * 4)
            bt.extend([[base, base + 1, base + 2], [base, base + 2, base + 3]])
    if bp:
        bp = np.array(bp)
        meshes.append((bp, np.array(bn), np.zeros((len(bp), 2)), np.array(bt), None, None, np.tile([[0.6, 0.9, 1.0]], (len(bp), 1))))
    return meshlib.rasterize(meshes, out, yaw=yaw, pitch=pitch, size=size)


def beam_line(anim, t):
    """World (model px) start and end of the beam bone's long axis at time t."""
    geo_bones, tw, th = rp.load_geo(os.path.join(A, "geo", "entity", "godzilla.geo.json"))
    pose = rp.animation_pose(os.path.join(A, "animations", "entity", "godzilla.animation.json"), anim, t)
    mats = rp.bone_matrices(geo_bones, pose)
    beam = [b for b in geo_bones if b["name"] == "B"][0]
    c = beam["cubes"][0]
    o, sz = c["origin"], c["size"]
    cx, cy = -(o[0] + sz[0] / 2), o[1] + sz[1] / 2
    near = mats["B"] @ np.array([cx, cy, o[2] + sz[2], 1.0])
    far = mats["B"] @ np.array([cx, cy, o[2], 1.0])
    return near[:3], far[:3]


if __name__ == "__main__":
    a = sys.argv[1:]
    render(a[0] if a[0] != "-" else None, float(a[1]), a[2], float(a[3]) if len(a) > 3 else 150,
           float(a[4]) if len(a) > 4 else 12, bones_view="--bones" in a)
