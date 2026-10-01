"""The glow inside Godzilla's open mouth for the charged statue pose (client.render.GodzillaStatueRenderer): the
floor of his mouth, his palate and the inner faces of his teeth, lit cyan-blue, brightest towards the throat.

    python3 tools/godzilla_mouth_glow.py

Writes textures/entity/godzilla_mouth_glow.png: painted only on texels of the add-on's texture layout that the
inside of his mouth uses and nothing else does (some texels are shared between faces), clear and black elsewhere
(it is drawn additively, over his head and jaw only).
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_preview as rp  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
GEO = os.path.join(ASSETS, "geo", "entity", "godzilla.geo.json")
OUT = os.path.join(ASSETS, "textures", "entity", "godzilla_mouth_glow.png")
SIZE = 512                     # the add-on's textures: 2 texels per UV unit
JAW_OPEN = 28.0                # degrees: the faces are sorted with the jaw open, as in the poses
DIM = np.array([16.0, 96.0, 200.0])      # at the lips
BRIGHT = np.array([70.0, 190.0, 255.0])  # towards the throat


def faces(bones, by_name, tw, th):
    """(bone, centre, outward normal, uvs) of each face of the head and jaw, in the model's space, jaw open."""
    jaw = by_name["Jew"]["pivot"]
    opened = rp.about_pivot([-jaw[0], jaw[1], jaw[2]], rp.rot_matrix(math.radians(-JAW_OPEN), 0, 0))
    for name in bones:
        for c in by_name[name].get("cubes", []):
            C = np.eye(4)
            if "rotation" in c:
                px, py, pz = c.get("pivot", [0, 0, 0])
                rx, ry, rz = c["rotation"]
                C = rp.about_pivot([-px, py, pz], rp.rot_matrix(math.radians(-rx), math.radians(-ry), math.radians(rz)))
            if name == "Jew":
                C = opened @ C
            for _, corners, uvs in rp.face_list(c, tw, th):
                pts = (C @ np.c_[corners, np.ones(4)].T).T[:, :3]
                n = np.cross(pts[1] - pts[0], pts[3] - pts[0])    # (points inwards)
                yield name, pts.mean(0), -n / max(np.linalg.norm(n), 1e-9), uvs


def inside_mouth(bone, centre, out):
    """Faces of the inside of his mouth: the palate and the undersides of the upper teeth, the floor of the mouth
    and the tops of the lower teeth, and the teeth's inner sides."""
    if centre[2] > -23.5:
        return False                                   # behind the corners of his mouth
    inner_side = abs(out[0]) > 0.75 and out[0] * centre[0] < 0 and abs(centre[0]) < 4.2 and centre[2] < -26
    if bone == "Head":
        return (out[1] < -0.5 and centre[1] < 75.5) or (inner_side and centre[1] < 75)
    return out[1] > 0.5 or inner_side


def texels(uvs):
    px = uvs * SIZE
    u0, v0 = np.floor(px.min(0)).astype(int)
    u1, v1 = np.ceil(px.max(0)).astype(int)
    return max(u0, 0), max(v0, 0), min(u1, SIZE), min(v1, SIZE)


def build():
    bones, tw, th = rp.load_geo(GEO)
    by_name = {b["name"]: b for b in bones}
    shared = np.zeros((SIZE, SIZE), bool)
    glow = np.zeros((SIZE, SIZE))
    for name, centre, out, uvs in faces([b["name"] for b in bones], by_name, tw, th):
        u0, v0, u1, v1 = texels(uvs)
        if u1 <= u0 or v1 <= v0:
            continue
        if name not in ("Head", "Jew") or not inside_mouth(name, centre, out):
            shared[v0:v1, u0:u1] = True
            continue
        deep = np.clip((centre[2] + 37.0) / 12.0, 0.0, 1.0)
        glow[v0:v1, u0:u1] = np.maximum(glow[v0:v1, u0:u1], 0.55 + 0.45 * deep)
    glow[shared] = 0.0

    img = np.zeros((SIZE, SIZE, 4))
    lit = glow > 0
    img[..., :3] = (DIM + (BRIGHT - DIM) * np.clip((glow[..., None] - 0.55) / 0.45, 0, 1)) * lit[..., None]
    img[..., 3] = lit * 255
    Image.fromarray(img.astype(np.uint8), "RGBA").save(OUT)
    print("mouth glow: %d texels" % lit.sum())


if __name__ == "__main__":
    build()
