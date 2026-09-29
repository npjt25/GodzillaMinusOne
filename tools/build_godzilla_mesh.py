"""Rigs the high-detail "Godzilla Minus One" model (Sketchfab, by ArachnoBoy) onto the add-on's skeleton.

    python3 tools/build_godzilla_mesh.py <path to the extracted model folder containing scene.gltf>

Run tools/convert_addon.py first; this script then replaces Godzilla's geometry:

  * geo/entity/godzilla.geo.json keeps the add-on's bone names and hierarchy (so all of its animations still
    work) with the joints moved onto the new mesh. Only the atomic-breath beam bone "B" keeps cubes.
  * godzilla_mesh/godzilla.mesh (binary, read by client/render/GodzillaMesh.java): the triangles, UVs,
    normals and per-vertex skin weights (two bones per vertex, automatic weights from distance to each bone,
    smoothed across the surface).
  * textures/entity/godzilla_body.png, godzilla_plates.png (recoloured to charcoal grey) and
    godzilla_plates_glow.png (the atomic-breath glow).

The mesh is scaled to 118 model pixels tall, which at the renderer's 6.1x scale is 45 blocks.
Coordinates below are GeckoLib model space: pixels, +y up, the model faces -z, +x is Godzilla's right side.
"""
import json
import math
import os
import struct
import sys
from collections import defaultdict

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meshlib  # noqa: E402

MODID = "godzillaminusone"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MODID)
HEIGHT_PX = 118.0

# Joint positions on the new mesh, for every bone of the add-on skeleton.
PIVOTS = {
    "Root": (0, 40, 0), "Allleg": (0, 40, 0),
    "Leg": (14, 40, -12), "Leg_bone": (22, 22, -18), "Foot": (24, 8, -12), "Foot3": (25, 0, -20),
    "To": (29, 2, -22), "To2": (26, 2, -27), "To3": (22, 2, -26), "To4": (19, 2, -19),
    "Leg2": (-14, 40, -12), "Leg_bone2": (-22, 22, -18), "Foot2": (-24, 8, -12), "Foot4": (-25, 0, -20),
    "To5": (-29, 2, -22), "To7": (-26, 2, -27), "To8": (-22, 2, -26), "To6": (-19, 2, -19),
    "Body": (0, 46, 0), "Metalbody": (0, 58, -4), "Chest": (0, 72, -8),
    "Neck": (0, 86, -12), "neck2": (0, 94, -18), "Head": (0, 99, -24), "Jew": (0, 99.5, -27.5),
    "B": (0, 100, -31), "Y": (0, 100, -36),
    "T7": (0, 54, 16), "T8": (-5, 52, 14), "T9": (5, 52, 14),
    "T4": (0, 66, 12), "T5": (-5, 64, 10), "T6": (5, 64, 10),
    "T": (0, 80, 4), "T2": (-6, 78, 2), "T3": (6, 78, 2),
    "T10": (-2, 94, -8), "T11": (2, 94, -8),
    "bone11": (0, 40, 12), "bone13": (0, 30, 30), "bone14": (0, 18, 50), "bone16": (0, 12, 75),
    "bone17": (0, 10, 105), "bone18": (0, 9, 132),
    "T12": (0, 46, 20), "T13": (-5, 44, 18), "T14": (5, 44, 18), "T15": (0, 36, 34), "T16": (0, 26, 52),
    "T17": (0, 20, 68), "T18": (0, 18, 86), "T19": (0, 17, 104), "T20": (0, 16, 118), "T21": (0, 15, 134),
    "T22": (0, 14, 152),
    "Right_arm": (15, 83, -13), "bone": (22, 74, -19), "bone2": (26, 68, -30), "bone3": (23, 67, -32),
    "o7": (22, 65, -36), "Finger": (27, 67, -33), "o4": (28, 68, -38), "o5": (27, 65, -38), "o6": (26, 62, -36),
    "Left_arm": (-15, 83, -13), "bone4": (-22, 74, -19), "bone5": (-26, 68, -30), "bone6": (-23, 67, -32),
    "o8": (-22, 65, -36), "Finger2": (-27, 67, -33), "o": (-28, 68, -38), "o2": (-27, 65, -38), "o3": (-26, 62, -36),
    "Smoke": (0, 16, -28), "Stomp": (0, 0, 0),
}

# Bones that deform the skin: (start, end) of the segment used for automatic weights, and the side of the
# body they may influence (+1 right, -1 left, 0 either).
SEGMENTS = {
    "Body": ((0, 40, 2), (0, 58, -4), 0), "Metalbody": ((0, 58, -4), (0, 72, -8), 0),
    "Chest": ((0, 72, -8), (0, 86, -12), 0), "Neck": ((0, 86, -12), (0, 94, -18), 0),
    "neck2": ((0, 94, -18), (0, 99, -24), 0), "Head": ((0, 101, -24), (0, 104, -39), 0),
    "Jew": ((0, 98, -28), (0, 97.5, -40), 0),
    "Leg": ((14, 40, -12), (22, 22, -18), 1), "Leg_bone": ((22, 22, -18), (24, 8, -12), 1),
    "Foot": ((24, 8, -12), (24, 3, -21), 1),
    "Leg2": ((-14, 40, -12), (-22, 22, -18), -1), "Leg_bone2": ((-22, 22, -18), (-24, 8, -12), -1),
    "Foot2": ((-24, 8, -12), (-24, 3, -21), -1),
    "Right_arm": ((15, 83, -13), (22, 74, -19), 1), "bone": ((22, 74, -19), (26, 68, -30), 1),
    "bone2": ((26, 68, -30), (27, 66, -34), 1), "Finger": ((27, 67, -33), (27, 65, -39), 1),
    "bone3": ((23, 67, -32), (22, 65, -37), 1),
    "Left_arm": ((-15, 83, -13), (-22, 74, -19), -1), "bone4": ((-22, 74, -19), (-26, 68, -30), -1),
    "bone5": ((-26, 68, -30), (-27, 66, -34), -1), "Finger2": ((-27, 67, -33), (-27, 65, -39), -1),
    "bone6": ((-23, 67, -32), (-22, 65, -37), -1),
    "bone11": ((0, 40, 12), (0, 30, 30), 0), "bone13": ((0, 30, 30), (0, 18, 50), 0),
    "bone14": ((0, 18, 50), (0, 12, 75), 0), "bone16": ((0, 12, 75), (0, 10, 105), 0),
    "bone17": ((0, 10, 105), (0, 9, 132), 0), "bone18": ((0, 9, 132), (0, 9, 170), 0),
}

# Dorsal plate bones, in the order the charge animation punches them up (tail tip first, neck last),
# with the animation time (s) of each punch. Used to light the plates up one after another.
PLATES = [("T22", 0.08), ("T21", 0.17), ("T20", 0.25), ("T19", 3.29), ("T18", 4.38), ("T17", 5.12),
          ("T16", 5.58), ("T15", 6.0), ("T12", 6.29), ("T13", 6.29), ("T14", 6.29), ("T7", 6.67), ("T8", 6.67),
          ("T9", 6.67), ("T4", 7.21), ("T5", 7.21), ("T6", 7.21), ("T", 7.88), ("T2", 7.88), ("T3", 7.88),
          ("T10", 8.54), ("T11", 8.54)]

# Net rest pitch of the add-on's Body->Metalbody->Chest->Neck->neck2->Head chain plus B's own (degrees): the
# new rig has no rest rotations, so the beam bone takes it over to keep the beam pointing the same way.
BEAM_REST_PITCH = 4.95


def out(*parts):
    path = os.path.join(ASSETS, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def to_model_space(pos, scale):
    """glTF (y up, facing +z) -> GeckoLib model space (px, facing -z)."""
    res = pos.copy()
    res[:, 0] *= -1.0
    res[:, 2] *= -1.0
    return res * scale


def seg_dist(points, a, b):
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    ab = b - a
    t = np.clip(((points - a) @ ab) / max(ab @ ab, 1e-9), 0.0, 1.0)
    return np.linalg.norm(points - (a + t[:, None] * ab), axis=1)


def jaw_factor(p):
    """0..1: how much a head vertex belongs to the lower jaw (below the mouth line, in front of the hinge)."""
    x, y, z = p[:, 0], p[:, 1], p[:, 2]
    mouth_y = 100.4 + np.clip(-(z + 30.0), 0, 12) * 0.03
    below = np.clip((mouth_y - y) / 0.6 + 0.5, 0, 1)
    front = np.clip((-(z + 27.0)) / 3.0, 0, 1)
    depth = np.clip((y - 91.0) / 4.0, 0, 1)  # the throat blends back into the neck
    return below * front * depth


def weld(pos, tol=1e-3):
    keys = np.round(pos / tol).astype(np.int64)
    _, inverse = np.unique(keys, axis=0, return_inverse=True)
    return inverse.ravel()


def smooth_weights(W, tris, pos, iterations):
    """Averages weights over mesh neighbours (UV seams welded) so joints blend smoothly."""
    group = weld(pos)
    n_groups = group.max() + 1
    edges = np.concatenate([tris[:, [0, 1]], tris[:, [1, 2]], tris[:, [2, 0]]])
    ga, gb = group[edges[:, 0]], group[edges[:, 1]]
    for _ in range(iterations):
        G = np.zeros((n_groups, W.shape[1]))
        np.add.at(G, group, W)
        cnt = np.bincount(group, minlength=n_groups)[:, None]
        G /= np.maximum(cnt, 1)
        acc = G.copy()
        deg = np.ones(n_groups)
        np.add.at(acc, ga, G[gb])
        np.add.at(acc, gb, G[ga])
        np.add.at(deg, ga, 1)
        np.add.at(deg, gb, 1)
        G = acc / deg[:, None]
        W = 0.5 * W + 0.5 * G[group]
    return W


def body_weights(pos, tris, material, bone_index):
    names = list(SEGMENTS)
    D = np.stack([seg_dist(pos, a, b) for a, b, _ in SEGMENTS.values()], axis=1)
    W = 1.0 / (D * D + 4.0) ** 2
    x, y, z = pos[:, 0], pos[:, 1], pos[:, 2]
    for j, name in enumerate(names):
        side = SEGMENTS[name][2]
        if side > 0:
            W[x < -1.5, j] = 0
        elif side < 0:
            W[x > 1.5, j] = 0
    # tail vertices never follow the legs
    tail = z > 18
    for name in ("Leg", "Leg_bone", "Foot", "Leg2", "Leg_bone2", "Foot2"):
        W[tail, names.index(name)] = 0
    ji, hi = names.index("Jew"), names.index("Head")
    W[:, ji] = 0
    W /= np.maximum(W.sum(1, keepdims=True), 1e-12)
    W = smooth_weights(W, tris, pos, 4)

    # Head: skull and lower jaw are split by the mouth line; lower down / further back it blends into the neck.
    head = np.clip((y - 91.0) / 6.0, 0, 1) * np.clip((-z - 20.0) / 4.0, 0, 1)
    jaw = jaw_factor(pos)
    W *= (1.0 - head)[:, None]
    W[:, hi] += head * (1.0 - jaw)
    W[:, ji] += head * jaw
    W[material == "Material.027", :] = 0  # tongue
    W[material == "Material.027", ji] = 1
    W[material == "Material.028", :] = 0  # eyes
    W[material == "Material.028", hi] = 1
    W /= np.maximum(W.sum(1, keepdims=True), 1e-12)
    return top2(W, [bone_index[n] for n in names])


def top2(W, columns):
    order = np.argsort(-W, axis=1)[:, :2]
    rows = np.arange(len(W))
    w0 = W[rows, order[:, 0]]
    w1 = W[rows, order[:, 1]]
    total = np.maximum(w0 + w1, 1e-12)
    cols = np.asarray(columns)
    return cols[order[:, 0]], cols[order[:, 1]], w0 / total


def components(tris, n):
    parent = np.arange(n)

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for a, b, c in tris:
        for u, v in ((a, b), (b, c)):
            ru, rv = find(u), find(v)
            if ru != rv:
                parent[ru] = rv
    return np.array([find(i) for i in range(n)])


def plate_weights(pos, tris, bone_index):
    """Each dorsal plate (connected piece of the plate mesh) follows the nearest plate bone rigidly."""
    group = weld(pos)
    comp_of_group = components(group[tris], group.max() + 1)
    comp = comp_of_group[group]
    plate_names = [n for n, _ in PLATES]
    piv = np.array([PIVOTS[n] for n in plate_names], float)
    b0 = np.zeros(len(pos), int)
    glow = np.zeros(len(pos), int)
    for c in np.unique(comp):
        idx = np.where(comp == c)[0]
        centre = pos[idx].mean(0)
        # the plates run along the back: judge distance mostly along the body axis
        d = np.linalg.norm((piv - centre) * np.array([0.5, 1.0, 1.0]), axis=1)
        k = int(np.argmin(d))
        b0[idx] = bone_index[plate_names[k]]
        glow[idx] = k
    return b0, b0.copy(), np.ones(len(pos)), glow


def grey_texture(path):
    """Same treatment as the add-on textures: brown/olive -> neutral charcoal grey, luminance kept."""
    a = np.array(Image.open(path).convert("RGB")).astype(float)
    lum = 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]
    lum = np.clip(lum * 1.05 + 6.0, 0, 255)
    g = np.stack([lum - 1, lum, lum + 2], -1)
    return np.clip(g, 0, 255).astype(np.uint8)


def glow_texture(grey):
    """Blue-white atomic glow following the plate texture's detail."""
    lum = grey.astype(float).mean(-1) / 255.0
    k = np.clip(0.55 + lum * 1.6, 0, 1)
    rgb = np.stack([k * 90, k * 200, k * 255], -1)
    return np.clip(rgb, 0, 255).astype(np.uint8)


def build_geo(addon_geo):
    geo = json.load(open(addon_geo))
    g = geo["minecraft:geometry"][0]
    new_bones = []
    for bone in g["bones"]:
        name = bone["name"]
        gx, gy, gz = PIVOTS[name]
        nb = {"name": name, "pivot": [-gx, gy, gz]}  # geo files store Bedrock x (= -GeckoLib x)
        if "parent" in bone:
            nb["parent"] = bone["parent"]
        if name == "B":
            old = bone["pivot"]
            dx, dy, dz = -gx - old[0], gy - old[1], gz - old[2]
            nb["rotation"] = [BEAM_REST_PITCH, 0, 0]
            cubes = []
            for c in bone["cubes"]:
                c = dict(c)
                c["origin"] = [c["origin"][0] + dx, c["origin"][1] + dy, c["origin"][2] + dz]
                if "pivot" in c:
                    c["pivot"] = [c["pivot"][0] + dx, c["pivot"][1] + dy, c["pivot"][2] + dz]
                cubes.append(c)
            nb["cubes"] = cubes
        new_bones.append(nb)
    desc = dict(g["description"])
    desc.update(identifier="geometry.godzilla", visible_bounds_width=60, visible_bounds_height=60,
                visible_bounds_offset=[0, 20, 0])
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": desc, "bones": new_bones}]}, \
        [b["name"] for b in new_bones]


def write_mesh(path, bone_names, parts):
    with open(path, "wb") as f:
        f.write(b"GZM1")
        f.write(struct.pack(">i", len(bone_names)))
        for n in bone_names:
            data = n.encode("utf-8")
            f.write(struct.pack(">H", len(data)) + data)
        f.write(struct.pack(">i", len(PLATES)))
        for n, t in PLATES:
            data = n.encode("utf-8")
            f.write(struct.pack(">H", len(data)) + data + struct.pack(">f", t))
        f.write(struct.pack(">i", len(parts)))
        for p in parts:
            tex = p["texture"].encode("utf-8")
            f.write(struct.pack(">H", len(tex)) + tex)
            f.write(struct.pack(">b", 1 if p["plates"] else 0))
            n = len(p["pos"])
            f.write(struct.pack(">i", n))
            rec = np.zeros(n, dtype=[("x", ">f4"), ("y", ">f4"), ("z", ">f4"), ("nx", ">f4"), ("ny", ">f4"), ("nz", ">f4"),
                                     ("u", ">f4"), ("v", ">f4"), ("b0", ">u1"), ("b1", ">u1"), ("w0", ">f4"), ("glow", ">i1")])
            rec["x"], rec["y"], rec["z"] = (p["pos"] / 16.0).T
            rec["nx"], rec["ny"], rec["nz"] = p["normal"].T
            rec["u"], rec["v"] = p["uv"].T
            rec["b0"], rec["b1"], rec["w0"] = p["b0"], p["b1"], p["w0"]
            rec["glow"] = p["glow"]
            f.write(rec.tobytes())
            tris = p["idx"].astype(">i4")
            f.write(struct.pack(">i", len(tris)))
            f.write(tris.tobytes())


def build(gltf_path):
    prims = meshlib.load_gltf(gltf_path)
    height = max(p["pos"][:, 1].max() for p in prims)
    scale = HEIGHT_PX / height

    geo, bone_names = build_geo(os.path.join(ASSETS, "geo", "entity", "godzilla.geo.json"))
    bone_index = {n: i for i, n in enumerate(bone_names)}

    body_tex = next(p["texture"] for p in prims if p["material"] == "Material")
    plate_tex = next(p["texture"] for p in prims if p["material"] == "Material.029")
    grey_body = grey_texture(body_tex)
    grey_plates = grey_texture(plate_tex)
    Image.fromarray(grey_body).save(out("textures", "entity", "godzilla_body.png"))
    Image.fromarray(grey_plates).save(out("textures", "entity", "godzilla_plates.png"))
    Image.fromarray(glow_texture(grey_plates)).save(out("textures", "entity", "godzilla_plates_glow.png"))

    # Merge the body-textured primitives (skin, teeth, tongue, eyes) into one part; plates are the other.
    body_prims = [p for p in prims if p["texture"] == body_tex]
    offset = 0
    pos, nrm, uv, idx, mats = [], [], [], [], []
    for p in body_prims:
        mats.append(np.full(len(p["pos"]), p["material"], dtype=object))
        pos.append(to_model_space(p["pos"], scale))
        n = p["normal"].copy()
        n[:, 0] *= -1
        n[:, 2] *= -1
        nrm.append(n)
        uv.append(p["uv"])
        idx.append(p["idx"] + offset)
        offset += len(p["pos"])
    body = dict(pos=np.concatenate(pos), normal=np.concatenate(nrm), uv=np.concatenate(uv), idx=np.concatenate(idx))
    material = np.concatenate(mats)
    plate_prim = next(p for p in prims if p["material"] == "Material.029")
    pn = plate_prim["normal"].copy()
    pn[:, 0] *= -1
    pn[:, 2] *= -1
    plates = dict(pos=to_model_space(plate_prim["pos"], scale), normal=pn, uv=plate_prim["uv"], idx=plate_prim["idx"])

    body["b0"], body["b1"], body["w0"] = body_weights(body["pos"], body["idx"], material, bone_index)
    body["glow"] = np.full(len(body["pos"]), -1)
    plates["b0"], plates["b1"], plates["w0"], plates["glow"] = plate_weights(plates["pos"], plates["idx"], bone_index)

    body.update(texture=MODID + ":textures/entity/godzilla_body.png", plates=False)
    plates.update(texture=MODID + ":textures/entity/godzilla_plates.png", plates=True)

    with open(out("geo", "entity", "godzilla.geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
    write_mesh(out("godzilla_mesh", "godzilla.mesh"), bone_names, [body, plates])
    print("mesh: %d + %d vertices, %d + %d triangles, scale %.3f px/unit" % (
        len(body["pos"]), len(plates["pos"]), len(body["idx"]), len(plates["idx"]), scale))
    return geo, bone_names, body, plates


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    build(os.path.join(sys.argv[1], "scene.gltf") if os.path.isdir(sys.argv[1]) else sys.argv[1])
