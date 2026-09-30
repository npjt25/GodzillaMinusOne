"""Builds the Odo Island Monster: Godzilla Minus One's earlier, smaller dinosaur form (about 15 blocks tall), as a
blocky, voxel version of a detailed 3D model. Writes its GeckoLib geometry, textures and animations.

    python3 tools/make_odo_model.py <folder of the reference glTF (scene.gltf, scene.bin, textures/)>

(needs numpy, Pillow and scipy)

The shape and colours come from "Godzillasaurus Minus One"
(https://sketchfab.com/3d-models/godzillasaurus-minus-one-d529cfb2e48148e3a4ea0644bc48fa78) by Kaiju Models For
Free (https://sketchfab.com/ChadZilla), licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/):
its surface is sampled into VOXEL px cubes coloured from its texture, the inside filled, and every voxel given to
the bone of the Odo skeleton that the model's own skeleton moves it with. Each bone's voxels are then merged into as
few boxes as possible, with a box-UV texture painted voxel by voxel. The Odo's bones pivot where the model's joints
are, and the animations (tools/odo_animations.py) are re-fitted to its legs.
"""
import json
import math
import os
import re
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import detail_godzilla  # noqa: E402  (the burnt hide's glowing cracks)
import render_preview  # noqa: E402  (the box-UV face layout)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
NAME = "odo_island_monster"

# Model units are pixels; the renderer scales the model by 3.46 (OdoIslandMonsterRenderer), so HEIGHT px stand
# 15 blocks tall.
HEIGHT = 69.4
VOXEL = 1.0             # px per voxel
TEXEL = 2.0             # UV units per texture pixel: one pixel per voxel face
# The reference faces +z with +x on its left; the model faces -z (x stays its left). Its middle, between the feet
# and the chest, is put over the entity's position (the centre of its hitbox).
CENTRE_Z = -3.0

# Which bone of the Odo skeleton each of the reference's joints (by name, before the numbers) belongs to. Joints
# not listed go with their parent.
JOINT_BONES = {
    "bip_pelvis": "hips", "bip_pelvis.C": "hips", "bip_pelvis_l": "hips", "bip_pelvis_r": "hips", "bip_tail": "hips",
    "bip_spine_0": "hips",
    "bip_spine_1": "chest", "bip_spine_2": "chest", "bip_spine_3": "chest", "bip_Breast.C": "chest", "bip_belly.C": "chest",
    "bip_collar_l": "chest", "bip_collar_r": "chest",
    "bip_neck": "neck", "bip_neck_1": "neck", "bip_neck_2": "neck2", "bip_neck_3": "neck2", "bip_jowl_1": "neck2",
    "bip_jowl_2": "neck2",
    "bip_head": "head", "bip_jaw": "jaw",
    "bip_tail_1": "tail1", "bip_tail_2": "tail1", "bip_tail_3": "tail2", "bip_tail_4": "tail2", "bip_tail_5": "tail3",
    "bip_tail_6": "tail3", "bip_tail_7": "tail3", "bip_tail_8": "tail4", "bip_tail_9": "tail4", "bip_tail_10": "tail5",
    "bip_tail_11": "tail5",
}
for _s, _side in (("l", "left"), ("r", "right")):
    JOINT_BONES.update({
        "bip_upperarm_" + _s: "arm_" + _side, "bip_lowerarm_" + _s: "forearm_" + _side,
        "bip_hip_" + _s: "thigh_" + _side, "bip_knee_" + _s: "shin_" + _side, "bip_foot_" + _s: "ankle_" + _side,
        "bip_toe_" + _s: "foot_" + _side,
    })

# The Odo's skeleton: bone -> (parent, the reference joint it pivots on)
SKELETON = {
    "root": (None, None), "body": ("root", "bip_pelvis"), "hips": ("body", "bip_pelvis"), "chest": ("hips", "bip_spine_1"),
    "neck": ("chest", "bip_neck"), "neck2": ("neck", "bip_neck_2"), "head": ("neck2", "bip_head"), "jaw": ("head", "bip_jaw"),
    "tail1": ("hips", "bip_tail_1"), "tail2": ("tail1", "bip_tail_3"), "tail3": ("tail2", "bip_tail_5"),
    "tail4": ("tail3", "bip_tail_8"), "tail5": ("tail4", "bip_tail_10"),
}
for _s, _side in (("l", "left"), ("r", "right")):
    SKELETON.update({
        "arm_" + _side: ("chest", "bip_upperarm_" + _s), "forearm_" + _side: ("arm_" + _side, "bip_lowerarm_" + _s),
        "thigh_" + _side: ("hips", "bip_hip_" + _s), "shin_" + _side: ("thigh_" + _side, "bip_knee_" + _s),
        "ankle_" + _side: ("shin_" + _side, "bip_foot_" + _s), "foot_" + _side: ("ankle_" + _side, "bip_toe_" + _s),
    })


def base_name(joint):
    """'bip_tail_1_16_8' -> 'bip_tail_1' (the exporter appends two numbers)."""
    return re.sub(r"_\d+_\d+$", "", joint)


# ---------------------------------------------------------------- the reference model
CT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
NC = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def _quat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def _local(node):
    if "matrix" in node:
        return np.array(node["matrix"], float).reshape(4, 4).T
    m = np.eye(4)
    m[:3, :3] = _quat(node.get("rotation", [0, 0, 0, 1])) * np.array(node.get("scale", [1, 1, 1]))
    m[:3, 3] = node.get("translation", [0, 0, 0])
    return m


def load_reference(folder):
    """Its triangles as posed (skinned), their UVs, the joint that moves each corner most, the joints' names,
    parents and positions, and the texture."""
    g = json.load(open(os.path.join(folder, "scene.gltf")))
    buf = open(os.path.join(folder, "scene.bin"), "rb").read()

    def acc(i):
        a = g["accessors"][i]
        bv = g["bufferViews"][a["bufferView"]]
        off = bv.get("byteOffset", 0) + a.get("byteOffset", 0)
        n = NC[a["type"]]
        dt = np.dtype(CT[a["componentType"]])
        stride = bv.get("byteStride", 0)
        if stride and stride != n * dt.itemsize:
            raw = np.frombuffer(buf, np.uint8, count=stride * a["count"], offset=off).reshape(a["count"], stride)
            arr = raw[:, :n * dt.itemsize].copy().view(dt).reshape(a["count"], n)
        else:
            arr = np.frombuffer(buf, dt, count=a["count"] * n, offset=off).reshape(a["count"], n)
        if a.get("normalized"):
            arr = arr.astype(float) / np.iinfo(dt).max
        return arr

    nodes = g["nodes"]
    parent = {c: i for i, n in enumerate(nodes) for c in n.get("children", [])}
    world = {}

    def W(i):
        if i not in world:
            world[i] = (W(parent[i]) if i in parent else np.eye(4)) @ _local(nodes[i])
        return world[i]

    skin = g["skins"][0]
    joints = skin["joints"]
    ibm = acc(skin["inverseBindMatrices"]).astype(float).reshape(-1, 4, 4).transpose(0, 2, 1)
    jm = np.array([W(j) @ ibm[k] for k, j in enumerate(joints)])
    index = {j: k for k, j in enumerate(joints)}
    names = [base_name(nodes[j]["name"]) for j in joints]
    jparent = [index.get(parent.get(j), -1) for j in joints]
    jpos = np.array([W(j)[:3, 3] for j in joints])

    tris, uvs, dom = [], [], []
    for n in nodes:
        if "mesh" not in n:
            continue
        for p in g["meshes"][n["mesh"]]["primitives"]:
            at = p["attributes"]
            pos = acc(at["POSITION"]).astype(float)
            uv = acc(at["TEXCOORD_0"]).astype(float)
            J = acc(at["JOINTS_0"]).astype(int)
            Wt = acc(at["WEIGHTS_0"]).astype(float)
            Wt /= np.maximum(Wt.sum(1, keepdims=True), 1e-9)
            ph = np.c_[pos, np.ones(len(pos))]
            skinned = sum(Wt[:, k:k + 1] * np.einsum("nij,nj->ni", jm[J[:, k]], ph) for k in range(4))[:, :3]
            idx = acc(p["indices"]).astype(int).reshape(-1, 3)
            tris.append(skinned[idx])
            uvs.append(uv[idx])
            dom.append(J[np.arange(len(J)), Wt.argmax(1)][idx])
    tex = np.asarray(Image.open(os.path.join(folder, "textures", "Godzillasaurus_MinusOne_baseColor.png")).convert("RGB"))
    return np.concatenate(tris), np.concatenate(uvs), np.concatenate(dom), names, jparent, jpos, tex.astype(float) / 255.0


# ---------------------------------------------------------------- voxels
def to_model(p, k):
    """Reference coordinates -> model px (x stays its left; it faces -z)."""
    p = np.asarray(p, float)
    return np.stack([p[..., 0] * k, p[..., 1] * k, -(p[..., 2] - CENTRE_Z) * k], -1)


def sample_surface(tris, uvs, dom, spacing):
    """Points all over the triangles (no further apart than spacing), with their UVs and joints."""
    edges = np.maximum.reduce([np.linalg.norm(tris[:, 1] - tris[:, 0], axis=1), np.linalg.norm(tris[:, 2] - tris[:, 1], axis=1),
                               np.linalg.norm(tris[:, 0] - tris[:, 2], axis=1)])
    steps = np.clip(np.ceil(edges / spacing), 1, 40).astype(int)
    pts, uv, jt = [], [], []
    for k in np.unique(steps):
        sel = steps == k
        T, U, D = tris[sel], uvs[sel], dom[sel]
        bary = np.array([(i / k, j / k, 1 - (i + j) / k) for i in range(k + 1) for j in range(k + 1 - i)])
        pts.append(np.einsum("bc,tcd->tbd", bary, T).reshape(-1, 3))
        uv.append(np.einsum("bc,tcd->tbd", bary, U).reshape(-1, 2))
        near = bary.argmax(1)
        jt.append(D[:, near].reshape(-1))
    return np.concatenate(pts), np.concatenate(uv), np.concatenate(jt)


def flood_outside(surface):
    """Voxels reachable from outside the grid without crossing the surface."""
    out = np.zeros_like(surface)
    out[0, :, :] = out[-1, :, :] = out[:, 0, :] = out[:, -1, :] = out[:, :, 0] = out[:, :, -1] = True
    out &= ~surface
    while True:
        grown = out.copy()
        grown[1:] |= out[:-1]
        grown[:-1] |= out[1:]
        grown[:, 1:] |= out[:, :-1]
        grown[:, :-1] |= out[:, 1:]
        grown[:, :, 1:] |= out[:, :, :-1]
        grown[:, :, :-1] |= out[:, :, 1:]
        grown &= ~surface
        if (grown == out).all():
            return out
        out = grown


def voxelize(ref):
    tris, uvs, dom, names, jparent, jpos, tex = ref
    k = HEIGHT / (tris[..., 1].max() - tris[..., 1].min())
    mt = to_model(tris, k)
    mt[..., 1] -= mt[..., 1].min()
    pts, uv, jt = sample_surface(mt, uvs, dom, VOXEL * 0.35)
    lo = np.floor(pts.min(0) / VOXEL) * VOXEL - VOXEL
    idx = np.floor((pts - lo) / VOXEL).astype(int)
    dims = idx.max(0) + 2
    flat = np.ravel_multi_index(idx.T, dims)
    th, tw = tex.shape[:2]
    col = tex[np.clip(((uv[:, 1] % 1.0) * th).astype(int), 0, th - 1), np.clip(((uv[:, 0] % 1.0) * tw).astype(int), 0, tw - 1)]
    n = np.prod(dims)
    count = np.bincount(flat, minlength=n)
    rgb = np.stack([np.bincount(flat, col[:, c], minlength=n) for c in range(3)], -1) / np.maximum(count, 1)[:, None]

    # the bone of each joint, and of each voxel (most samples win)
    def bone_of(j):
        while j >= 0 and names[j] not in JOINT_BONES:
            j = jparent[j]
        return JOINT_BONES.get(names[j], "hips") if j >= 0 else "hips"

    bone_names = sorted(set(JOINT_BONES.values()))
    joint_bone = np.array([bone_names.index(bone_of(j)) for j in range(len(names))])
    eyes = np.array([names[j] in ("bip_eye_l", "bip_eye_r") for j in range(len(names))])
    votes = np.zeros((n, len(bone_names)), np.int32)
    np.add.at(votes, (flat, joint_bone[jt]), 1)
    eye_votes = np.bincount(flat, eyes[jt].astype(float), minlength=n)

    surface = (count > 0).reshape(dims)
    solid = ~flood_outside(surface)
    bone = votes.argmax(1).reshape(dims)
    bone[~surface] = -1
    # the inside goes with the nearest bit of surface
    filled = surface.copy()
    while True:
        todo = solid & ~filled
        if not todo.any():
            break
        for axis in range(3):
            for step in (1, -1):
                src = np.roll(bone, step, axis)
                srcf = np.roll(filled, step, axis)
                take = todo & srcf & (bone < 0)
                bone[take] = src[take]
        filled = bone >= 0
    colours = rgb.reshape(tuple(dims) + (3,))
    eye = (eye_votes.reshape(dims) > count.reshape(dims) * 0.3) & surface
    joints = {name: to_model(jpos[names.index(j)], k) - np.array([0, 0, 0]) for name, (_, j) in SKELETON.items() if j}
    ymin = to_model(tris, k)[..., 1].min()
    for name in joints:
        joints[name] = joints[name] - np.array([0, ymin, 0])
    return {"lo": lo, "dims": dims, "solid": solid, "surface": surface, "bone": bone, "bones": bone_names,
            "rgb": colours, "eye": eye, "pivots": joints}


def greedy_boxes(mask):
    """Merges a voxel mask into boxes: [(x0, y0, z0, x1, y1, z1)] (exclusive ends), biggest runs first."""
    todo = mask.copy()
    boxes = []
    X, Y, Z = mask.shape
    for x in range(X):
        for y in range(Y):
            zs = np.nonzero(todo[x, y])[0]
            for z in zs:
                if not todo[x, y, z]:
                    continue
                z1 = z
                while z1 < Z and todo[x, y, z1]:
                    z1 += 1
                y1 = y + 1
                while y1 < Y and todo[x, y1, z:z1].all():
                    y1 += 1
                x1 = x + 1
                while x1 < X and todo[x1, y:y1, z:z1].all():
                    x1 += 1
                todo[x:x1, y:y1, z:z1] = False
                boxes.append((x, y, z, x1, y1, z1))
    return boxes


def colour_grid(ref, v, k, cell):
    """The reference's colours averaged over a finer grid (cell px), for painting the voxels' faces."""
    tris, uvs, dom, names, jparent, jpos, tex = ref
    mt = to_model(tris, k)
    mt[..., 1] -= mt[..., 1].min()
    pts, uv, _ = sample_surface(mt, uvs, dom, cell * 0.4)
    idx = np.floor((pts - v["lo"]) / cell).astype(int)
    dims = np.ceil(v["dims"] * VOXEL / cell).astype(int) + 1
    ok = (idx >= 0).all(1) & (idx < dims).all(1)
    flat = np.ravel_multi_index(idx[ok].T, dims)
    th, tw = tex.shape[:2]
    col = tex[np.clip(((uv[ok, 1] % 1.0) * th).astype(int), 0, th - 1), np.clip(((uv[ok, 0] % 1.0) * tw).astype(int), 0, tw - 1)]
    n = int(np.prod(dims))
    count = np.bincount(flat, minlength=n)
    rgb = np.stack([np.bincount(flat, col[:, c], minlength=n) for c in range(3)], -1) / np.maximum(count, 1)[:, None]
    return rgb.reshape(tuple(dims) + (3,)), (count > 0).reshape(dims)


def spread(values, known):
    """Gives every cell the colour of the nearest cell the reference's surface passed through."""
    from scipy import ndimage
    _, (i, j, k) = ndimage.distance_transform_edt(~known, return_indices=True)
    return values[i, j, k]


def grade(rgb):
    """The reference's hide, a little lifted for Minecraft's darker lighting: charcoal and olive-brown scales."""
    lum = rgb.mean(-1, keepdims=True)
    rgb = lum + (rgb - lum) * 1.25
    return np.clip(np.clip(rgb, 0.0, 1.0) ** 0.85 * 1.2 + 0.01, 0.0, 1.0)


def pack(cubes):
    """Shelf-packs every cube's box UV (in UV units); sets each cube's "uv"; returns the sheet size."""
    items = sorted(cubes, key=lambda c: (-(c["size"][1] + c["size"][2]), -(c["size"][0] + c["size"][2])))
    width = 512
    x = y = shelf = 0
    for c in items:
        w, h, d = c["size"]
        rw, rh = int(2 * (w + d)), int(h + d)
        if x + rw > width:
            x, y, shelf = 0, y + shelf, 0
        c["uv"] = [x, y]
        x += rw
        shelf = max(shelf, rh)
    height = 1 << int(math.ceil(math.log2(y + shelf)))
    return width, height


def paint(cubes, width, height, colours, known, v, cell):
    """Paints each face of each cube, texel by texel, with the colour just inside it."""
    img = np.zeros((height, width, 3))
    eye = np.zeros((height, width), bool)
    filled = img.sum(-1) < 0
    for c in cubes:
        for name, corners, uvs in render_preview.face_list(c, width, height):
            u0, v0 = uvs[0] * [width, height]
            du = (uvs[1] - uvs[0]) * [width, height]
            dv = (uvs[3] - uvs[0]) * [width, height]
            us = np.arange(int(round(min(u0, u0 + du[0]))), int(round(max(u0, u0 + du[0]))))
            vs = np.arange(int(round(min(v0, v0 + dv[1]))), int(round(max(v0, v0 + dv[1]))))
            if not len(us) or not len(vs):
                continue
            U, V = np.meshgrid(us + 0.5, vs + 0.5)
            a = (U - u0) / du[0]
            b = (V - v0) / dv[1]
            p = corners[0] + a[..., None] * (corners[1] - corners[0]) + b[..., None] * (corners[3] - corners[0])
            p[..., 0] = -p[..., 0]                          # (render_preview's space has x negated)
            normal = np.cross(corners[1] - corners[0], corners[3] - corners[0])
            normal[0] = -normal[0]
            normal /= np.linalg.norm(normal)
            inside = p + normal * (cell * 0.5)              # (the corners wind inwards: step in, not out)
            if not c["inside"](inside.reshape(-1, 3)).all():
                inside = p - normal * (cell * 0.5)
            ci = np.clip(np.floor((inside - v["lo"]) / cell).astype(int), 0, np.array(colours.shape[:3]) - 1)
            img[V.astype(int), U.astype(int)] = colours[ci[..., 0], ci[..., 1], ci[..., 2]]
            vi = np.clip(np.floor((inside - v["lo"]) / VOXEL).astype(int), 0, v["dims"] - 1)
            eye[V.astype(int), U.astype(int)] = v["eye"][vi[..., 0], vi[..., 1], vi[..., 2]]
            filled[V.astype(int), U.astype(int)] = True
    return img, eye, filled


def build_cubes(v):
    """Each bone's voxels merged into boxes (model px)."""
    cubes = {name: [] for name in SKELETON}
    for i, bone in enumerate(v["bones"]):
        for x0, y0, z0, x1, y1, z1 in greedy_boxes(v["solid"] & (v["bone"] == i)):
            origin = v["lo"] + np.array([x0, y0, z0]) * VOXEL
            size = np.array([x1 - x0, y1 - y0, z1 - z0]) * VOXEL
            lo, hi = origin, origin + size
            cubes[bone].append({"origin": [float(q) for q in origin], "size": [float(q) for q in size],
                                "inside": lambda p, lo=lo, hi=hi: ((p >= lo - 1e-6) & (p <= hi + 1e-6)).all(1)})
    return cubes


def build_textures(cubes, width, height, v, colours, known):
    flat = [c for cs in cubes.values() for c in cs]
    img, eye, filled = paint(flat, width, height, colours, known, v, VOXEL / 2)
    rng = np.random.default_rng(1954)
    img = grade(img) * (0.95 + 0.1 * rng.random((height, width)))[..., None]
    img[eye] = [0.95, 0.62, 0.18]
    tex = os.path.join(ASSETS, "textures", "entity")

    def save(rgb, alpha, name):
        Image.fromarray(np.dstack([np.clip(rgb, 0, 1) * 255, alpha * 255]).astype(np.uint8), "RGBA").save(os.path.join(tex, name))

    save(img, filled, NAME + ".png")
    glow = np.zeros_like(img)
    glow[eye] = [1.0, 0.6, 0.24]
    save(glow, eye, NAME + "_glow.png")
    # burnt to a crisp: charred black, the scales' pattern barely showing, with glowing cracks
    lum = img.mean(-1, keepdims=True)
    burnt = 0.07 + lum * np.array([0.32, 0.29, 0.27])
    f1, f2 = detail_godzilla.worley(max(width, height), 14, rng)
    embers = np.clip(1.0 - (f2 - f1) / 1.8, 0.0, 1.0) ** 3 * (rng.random(f1.shape) > 0.35)
    embers = embers[:height, :width] * filled
    save(burnt, filled, NAME + "_burnt.png")
    save(np.dstack([embers, embers * 0.43, embers * 0.12]), embers > 0.05, NAME + "_burnt_glow.png")


def write_geometry(cubes, pivots, width, height):
    bones = []
    for name, (parent, _) in SKELETON.items():
        b = {"name": name, "pivot": [round(float(q), 3) for q in pivots.get(name, (0, 0, 0))]}
        if parent:
            b["parent"] = parent
        if cubes[name]:
            b["cubes"] = [{"origin": [round(q, 3) for q in c["origin"]], "size": [round(q, 3) for q in c["size"]], "uv": c["uv"]}
                          for c in cubes[name]]
        bones.append(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + NAME, "texture_width": width, "texture_height": height,
                        "visible_bounds_width": 10, "visible_bounds_height": 8, "visible_bounds_offset": [0, 2, 0]},
        "bones": bones}]}
    path = os.path.join(ASSETS, "geo", "entity", NAME + ".geo.json")
    with open(path, "w") as f:
        json.dump(geo, f, separators=(",", ":"))
        f.write("\n")
    return path


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    ref = load_reference(sys.argv[1])
    v = voxelize(ref)
    k = HEIGHT / (ref[0][..., 1].max() - ref[0][..., 1].min())
    colours, known = colour_grid(ref, v, k, VOXEL / 2)
    colours = spread(colours, known)
    cubes = build_cubes(v)
    width, height = pack([c for cs in cubes.values() for c in cs])
    build_textures(cubes, width, height, v, colours, known)
    path = write_geometry(cubes, v["pivots"], width, height)
    import odo_animations
    odo_animations.write(os.path.join(ASSETS, "animations", "entity", NAME + ".animation.json"))
    print("wrote %s: %d bones, %d cubes, texture %dx%d" % (os.path.relpath(path, ROOT), len(SKELETON),
                                                           sum(len(c) for c in cubes.values()), width, height))


if __name__ == "__main__":
    main()
