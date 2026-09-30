"""Adds detail to the add-on's Godzilla, in its own blocky style (the high-detail Sketchfab model by ArachnoBoy
was only used as a reference for how rugged he should look):

  * the skin texture at 4x the add-on's resolution: a jagged, knobbly hide of big bulging lumps with pale keloid
    ridges and deep crevices, over smaller nodules and cracks,
  * raised skin bumps (small cubes) over the body, neck, arms, legs and tail,
  * lumps of deformed tissue: clusters of jagged, tumor-like growths bulging from his back, neck, shoulders,
    thighs and tail,
  * rows of teeth along both jaws,
  * heavy brow ridges over his eyes,
  * the burns the atomic breath leaves round his mouth (godzilla_burns.png, drawn over him after he fires, and
    godzilla_burns_glow.png, the flesh still glowing hot): raw, torn, blistered and charred flesh. The burn
    texture's alpha is the order it heals in (client.render.GodzillaRenderer): the edges close up first.

    python3 tools/detail_godzilla.py

Run after tools/convert_addon.py (it works on the add-on files that script writes, and refuses to run twice).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
GEO = os.path.join(ASSETS, "geo", "entity", "godzilla.geo.json")
TEX = os.path.join(ASSETS, "textures", "entity")
UPSCALE = 4                       # texels per model pixel (the add-on's textures have 2)
ADDON_JAW_CUBES = 8               # the add-on's lower jaw; more means this script already ran

# Where the new cubes' textures go: rows 208-255 of the 256x256 UV space are unused by the add-on.
SKIN_SWATCHES = [(0, 208), (16, 208), (32, 208), (48, 208)]   # 16x16 each, sampled from his hide
TOOTH_SWATCH = (64, 208)
TUMOR_SWATCHES = [(112, 208), (128, 208), (144, 208), (160, 208)]   # paler, knobbly scar tissue
SWATCH = 16

# Bones that get skin bumps, with how many per 100 square pixels of surface.
BUMP_BONES = {
    "Body": 1.6, "Metalbody": 1.6, "Chest": 1.4, "Neck": 1.6, "neck2": 1.8, "Head": 0.9,
    "bone11": 1.4, "bone13": 1.4, "bone14": 1.4, "bone16": 1.2, "bone17": 1.0, "bone18": 1.0,
    "Leg": 1.6, "Leg_bone": 1.6, "Leg2": 1.6, "Leg_bone2": 1.6, "Foot": 0.8, "Foot2": 0.8,
    "Right_arm": 1.6, "Left_arm": 1.6, "bone": 1.4, "bone4": 1.4,
}


# Clusters of tumor-like growths, per 100 square pixels of surface; they favour his back and top.
TUMOR_BONES = {
    "Body": 0.5, "Metalbody": 0.55, "Chest": 0.55, "Neck": 0.6, "neck2": 0.6,
    "Right_arm": 0.3, "Left_arm": 0.3, "bone": 0.2, "bone4": 0.2,
    "Leg": 0.4, "Leg2": 0.4, "Leg_bone": 0.25, "Leg_bone2": 0.25,
    "bone11": 0.45, "bone13": 0.45, "bone14": 0.4, "bone16": 0.35, "bone17": 0.3, "bone18": 0.25,
}
# How much each side of a cube attracts growths (+z is his back: the model faces -z).
TUMOR_FACES = {(2, 1): 1.7, (1, 1): 1.5, (0, 1): 1.0, (0, -1): 1.0, (2, -1): 0.35}

# Heavy brow ridges slanting down over the eyes, on both sides of the head (Head bone space: the add-on's head
# is 8.7 wide there).
EYE_Z = -30.25
EYE_Y = 74.85


def eye_cubes():
    cubes = []
    for side in (-1, 1):
        surface = 4.36 * side
        bx0 = surface - 0.75 if side > 0 else surface - 0.45
        cubes.append({"origin": [round(bx0, 3), EYE_Y + 0.72, EYE_Z - 0.55], "size": [1.2, 0.85, 2.6],
                      "uv": list(SKIN_SWATCHES[1]), "rotation": [14, -8 * side, 0],
                      "pivot": [round(bx0 + 0.6, 3), EYE_Y + 1.1, EYE_Z + 0.7]})
    return cubes


def rng():
    return np.random.default_rng(54)


# ---------------------------------------------------------------- texture

def worley(size, cell, r):
    """Distances to the nearest and second-nearest feature point (a jittered grid), in texels."""
    n = size // cell
    pts = (np.arange(n)[:, None, None] * 0 + np.stack(np.meshgrid(np.arange(n), np.arange(n), indexing="ij"), -1)
           + r.random((n, n, 2))) * cell
    yy, xx = np.meshgrid(np.arange(size) + 0.5, np.arange(size) + 0.5, indexing="ij")
    cy, cx = (yy // cell).astype(int), (xx // cell).astype(int)
    f1 = np.full((size, size), 1e9)
    f2 = np.full((size, size), 1e9)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            py = pts[(cy + dy) % n, (cx + dx) % n]
            # keep the wrap-around consistent: shift the feature point by a whole grid if we wrapped
            oy = np.where(cy + dy < 0, -size, np.where(cy + dy >= n, size, 0))
            ox = np.where(cx + dx < 0, -size, np.where(cx + dx >= n, size, 0))
            d = np.hypot(py[..., 0] + oy - yy, py[..., 1] + ox - xx)
            f2 = np.where(d < f1, f1, np.minimum(f2, d))
            f1 = np.minimum(f1, d)
    return f1, f2


def hide_detail(size, r, cell=11):
    """A brightness multiplier: raised nodules, dark cracks between them, and a fine grain."""
    f1, f2 = worley(size, cell, r)
    nodule = np.clip(1.0 - (f1 / (cell * 0.68)) ** 2, 0.0, 1.0) ** 0.8
    crack = np.clip(1.0 - (f2 - f1) / (cell * 0.2), 0.0, 1.0)
    g1, _ = worley(size, max(3, cell * 4 // 11), r)
    fine = np.clip(1.0 - (g1 / 3.0) ** 2, 0.0, 1.0)
    grain = r.random((size, size))
    return 0.74 + 0.36 * nodule + 0.06 * fine - 0.34 * crack ** 2 + 0.03 * (grain - 0.5)


def smooth_noise(size, cells, r):
    """Blobby low-frequency noise in [0, 1] (a random grid, bilinearly upsampled)."""
    grid = r.random((cells + 1, cells + 1))
    t = np.linspace(0, cells, size, endpoint=False)
    i = t.astype(int)
    f = t - i
    f = f * f * (3 - 2 * f)
    row = grid[i][:, i] * (1 - f)[None, :] + grid[i][:, i + 1] * f[None, :]
    row2 = grid[i + 1][:, i] * (1 - f)[None, :] + grid[i + 1][:, i + 1] * f[None, :]
    return row * (1 - f)[:, None] + row2 * f[:, None]


def tumor_detail(size, r):
    """Godzilla's hide: the nodules and cracks of hide_detail, raised into big, lit, bulging lumps with dark
    crevices between them, pale keloid ridges and patches of paler scar tissue."""
    base = hide_detail(size, r)
    cell = 34
    f1, f2 = worley(size, cell, r)
    lump = np.clip(1.0 - (f1 / (cell * 0.66)) ** 2, 0.0, 1.0)
    dome = lump ** 0.7
    # lit from above: the upper side of each lump is brighter, its underside falls into shadow
    light = np.clip(-np.gradient(dome, axis=0) * cell * 0.35, -1.0, 1.0)
    crevice = np.clip(1.0 - (f2 - f1) / (cell * 0.16), 0.0, 1.0) ** 2
    g1, g2 = worley(size, 17, r)
    ridge = np.clip(1.0 - np.abs((g2 - g1) - 2.2) / 1.3, 0.0, 1.0) * (1.0 - crevice)
    scar = np.clip((smooth_noise(size, 12, r) - 0.55) * 3.0, 0.0, 1.0)
    return base * (0.8 + 0.3 * dome + 0.16 * light - 0.42 * crevice + 0.2 * ridge * (0.4 + scar)) * (1.0 + 0.16 * scar)


def upscale(img, factor):
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def detail_texture(name, detail, emissive):
    img = upscale(Image.open(os.path.join(TEX, name)).convert("RGBA"), UPSCALE // 2)
    a = np.asarray(img).astype(float)
    mul = np.where(emissive[..., None], 1.0, detail[..., None])
    a[..., :3] = np.clip(a[..., :3] * mul, 0, 255)
    return a


def add_swatches(a, r, teeth, detail):
    """Paints the new cubes' texture swatches, sampled from the hide already on the sheet."""
    px = UPSCALE
    body = a[40 * px:200 * px, 0:200 * px]
    opaque = body[body[..., 3] > 200][:, :3]
    mean = np.median(opaque, axis=0)
    n = SWATCH * px
    for (u, v) in SKIN_SWATCHES:
        patch = np.empty((n, n, 4))
        tone = mean * (0.85 + 0.3 * r.random())
        noise = 0.85 + 0.3 * r.random((n, n))
        patch[..., :3] = np.clip(tone * noise[..., None], 0, 255)
        patch[..., 3] = 255
        a[v * px:(v + SWATCH) * px, u * px:(u + SWATCH) * px] = patch
    for i, (u, v) in enumerate(TUMOR_SWATCHES):
        # a knobbly patch of the hide's own pattern, in a paler, scarred tone
        y0, x0 = 64 + 90 * i, 300 + 70 * i
        knobs = detail[y0:y0 + n, x0:x0 + n]
        patch = np.empty((n, n, 4))
        tone = mean * (1.25 + 0.12 * i) + np.array([6.0, 2.0, 0.0])
        patch[..., :3] = np.clip(tone * knobs[..., None], 0, 255)
        patch[..., 3] = 255
        a[v * px:(v + SWATCH) * px, u * px:(u + SWATCH) * px] = patch

    def flat(uv, rgb, jitter):
        u, v = uv
        patch = np.empty((n, n, 4))
        patch[..., :3] = np.clip(np.array(rgb) * (1.0 - jitter + jitter * r.random((n, n)))[..., None], 0, 255)
        patch[..., 3] = 255
        a[v * px:(v + SWATCH) * px, u * px:(u + SWATCH) * px] = patch

    flat(TOOTH_SWATCH, teeth, 0.1)


def build_textures():
    r = rng()
    glow = np.asarray(upscale(Image.open(os.path.join(TEX, "godzilla_glow.png")).convert("RGBA"), UPSCALE // 2))
    emissive = glow[..., 3] > 0
    size = glow.shape[0]
    detail = tumor_detail(size, r)
    for name, teeth in (("godzilla.png", (172, 164, 146)), ("godzilla_dead.png", (96, 90, 84))):
        a = detail_texture(name, detail, emissive)
        add_swatches(a, rng(), teeth, detail)
        Image.fromarray(a.astype(np.uint8), "RGBA").save(os.path.join(TEX, name))
    for name in ("godzilla_glow.png", "godzilla_charged_glow.png"):
        img = np.asarray(upscale(Image.open(os.path.join(TEX, name)).convert("RGBA"), UPSCALE // 2)).copy()
        Image.fromarray(img, "RGBA").save(os.path.join(TEX, name))


# ---------------------------------------------------------------- geometry

FACES = [  # axis, sign
    (0, 1), (0, -1), (1, 1), (2, 1), (2, -1)   # no bumps on undersides (-y)
]


def with_frame(cube, new):
    for k in ("rotation", "pivot"):
        if k in cube:
            new[k] = list(cube[k])
    return new


def overlaps(cube, boxes):
    o, s = cube["origin"], cube["size"]
    for bo, bs in boxes:
        if all(o[k] < bo[k] + bs[k] and bo[k] < o[k] + s[k] for k in range(3)):
            return True
    return False


def face_cube(c, axis, sign, ca, cb, wa, wb, lift, h, uv, r, jagged):
    """A small cube on a face of cube c, centred at (ca, cb) on the face, standing lift + h proud of it."""
    o, s = c["origin"], c["size"]
    a, b = [k for k in range(3) if k != axis]
    inset = 0.3
    origin = [0.0, 0.0, 0.0]
    size = [0.0, 0.0, 0.0]
    origin[a], size[a] = ca - wa / 2, wa
    origin[b], size[b] = cb - wb / 2, wb
    size[axis] = inset + lift + h
    origin[axis] = o[axis] + s[axis] - inset if sign > 0 else o[axis] + inset - size[axis]
    cube = {"origin": [round(q, 3) for q in origin], "size": [round(q, 3) for q in size], "uv": list(uv)}
    if jagged and "rotation" not in c:
        rot = [0.0, 0.0, 0.0]
        rot[axis] = float(r.uniform(-30, 30))
        rot[a] = float(r.uniform(-12, 12))
        cube["rotation"] = [round(q, 2) for q in rot]
        cube["pivot"] = [round(origin[k] + size[k] / 2, 3) for k in range(3)]
        return cube
    return with_frame(c, cube)


def tumors_for_bone(bone, density, r):
    """Lumps of deformed tissue: a bulging mass with smaller knobs crowding round and on top of it."""
    cubes = [c for c in bone.get("cubes", []) if min(c["size"]) >= 2.0 and isinstance(c.get("uv"), list)]
    faces = []
    for c in cubes:
        s = c["size"]
        for (axis, sign), pull in TUMOR_FACES.items():
            a, b = [i for i in range(3) if i != axis]
            if s[a] >= 2.2 and s[b] >= 2.2:
                faces.append((c, axis, sign, s[a] * s[b], pull))
    if not faces:
        return []
    area = sum(f[3] for f in faces)
    count = int(round(area / 100.0 * density))
    weights = np.array([f[3] * f[4] for f in faces])
    out = []
    for i in r.choice(len(faces), size=count, p=weights / weights.sum()):
        c, axis, sign, _, _ = faces[i]
        o, s = c["origin"], c["size"]
        a, b = [k for k in range(3) if k != axis]
        w = float(min(r.uniform(1.6, 3.2), s[a] - 0.4, s[b] - 0.4))
        wb = float(min(w * r.uniform(0.7, 1.1), s[b] - 0.4))
        ca = o[a] + float(r.uniform(w / 2 + 0.2, s[a] - w / 2 - 0.2))
        cb = o[b] + float(r.uniform(wb / 2 + 0.2, s[b] - wb / 2 - 0.2))
        mass = float(r.uniform(0.6, 1.3))
        uv = TUMOR_SWATCHES[int(r.integers(len(TUMOR_SWATCHES)))]
        out.append(face_cube(c, axis, sign, ca, cb, w, wb, 0.0, mass, uv, r, True))
        for _ in range(int(r.integers(2, 6))):
            kw = float(r.uniform(0.6, 1.5))
            kb = kw * float(r.uniform(0.7, 1.3))
            on_top = r.random() < 0.45
            da = float(r.uniform(-0.55, 0.55)) * w
            db = float(r.uniform(-0.55, 0.55)) * wb
            lift = mass * float(r.uniform(0.5, 0.9)) if on_top else 0.0
            ka = float(np.clip(ca + da, o[a] + kw / 2 + 0.1, o[a] + s[a] - kw / 2 - 0.1))
            kbb = float(np.clip(cb + db, o[b] + kb / 2 + 0.1, o[b] + s[b] - kb / 2 - 0.1))
            knob_uv = TUMOR_SWATCHES[int(r.integers(len(TUMOR_SWATCHES)))] if r.random() < 0.7 else SKIN_SWATCHES[int(r.integers(4))]
            out.append(face_cube(c, axis, sign, ka, kbb, kw, kb, lift, float(r.uniform(0.35, 0.9)), knob_uv, r, True))
    return out


def bumps_for_bone(bone, density, r):
    cubes = [c for c in bone.get("cubes", []) if min(c["size"]) >= 1.5 and isinstance(c.get("uv"), list)]
    faces = []
    for c in cubes:
        s = c["size"]
        for axis, sign in FACES:
            a, b = [i for i in range(3) if i != axis]
            faces.append((c, axis, sign, s[a] * s[b]))
    area = sum(f[3] for f in faces)
    count = int(round(area / 100.0 * density))
    if count == 0 or not faces:
        return []
    weights = np.array([f[3] for f in faces]) / area
    out = []
    for i in r.choice(len(faces), size=count, p=weights):
        c, axis, sign, _ = faces[i]
        o, s = c["origin"], c["size"]
        a, b = [k for k in range(3) if k != axis]
        w = float(r.uniform(0.7, 1.7))
        h = float(r.uniform(0.35, 1.0))
        inset = 0.25
        if s[a] < w + 0.4 or s[b] < w + 0.4:
            continue
        origin = [0.0, 0.0, 0.0]
        size = [0.0, 0.0, 0.0]
        origin[a] = o[a] + float(r.uniform(0.2, s[a] - w - 0.2))
        origin[b] = o[b] + float(r.uniform(0.2, s[b] - w - 0.2))
        size[a] = size[b] = round(w, 3)
        size[axis] = round(h + inset, 3)
        origin[axis] = o[axis] + s[axis] - inset if sign > 0 else o[axis] - h
        u, v = SKIN_SWATCHES[int(r.integers(len(SKIN_SWATCHES)))]
        cube = {"origin": [round(q, 3) for q in origin], "size": size, "uv": [u, v]}
        out.append(with_frame(c, cube))
    return out


def find_cube(bone, origin, size):
    for c in bone["cubes"]:
        if all(abs(p - q) < 0.02 for p, q in zip(c["origin"], origin)) and all(abs(p - q) < 0.02 for p, q in zip(c["size"], size)):
            return c
    raise SystemExit("cube %s %s not found in %s" % (origin, size, bone["name"]))


def teeth(bones):
    """Two rows of teeth along the jaws, each tooth in the frame of the jaw cube it sits on."""
    head = bones["Head"]
    jaw = bones["Jew"]
    added = {"Head": [], "Jew": []}
    u, v = TOOTH_SWATCH

    def tooth(frame, x0, y0, z0, w, h, d):
        return with_frame(frame, {"origin": [round(x0, 3), round(y0, 3), round(z0, 3)], "size": [w, h, d], "uv": [u, v]})

    lengths = [0.95, 0.7, 0.85, 0.55, 0.75, 0.5, 0.6, 0.45]   # uneven, like a real set of teeth
    # lower jaw: along the outer top edge of both halves, pointing up
    for side, (origin, x0) in enumerate((([-4, 70.43, -34.87], -3.9), ([0, 70.43, -34.87], 3.55))):
        c = find_cube(jaw, origin, [4, 3, 9])
        top = c["origin"][1] + c["size"][1]
        for i in range(8):
            z = -34.5 + i * 0.98
            h = lengths[(i + side * 3) % len(lengths)] * 0.9
            added["Jew"].append(tooth(c, x0, top - 0.15, z, 0.35, round(h + 0.15, 3), 0.4))
    # upper jaw: hanging from the outer lower edge of the upper lip, pointing down
    for side, (origin, x0) in enumerate((([-3.5, 72.75, -36.01], -3.4), ([-0.42, 72.75, -36.01], 3.13))):
        c = find_cube(head, origin, [4, 1, 8])
        bottom = c["origin"][1]
        for i in range(8):
            z = -35.7 + i * 0.95
            h = lengths[(i + 1 + side * 5) % len(lengths)]
            added["Head"].append(tooth(c, x0, round(bottom - h, 3), z, 0.35, round(h + 0.15, 3), 0.4))
    # front teeth under the snout
    c = find_cube(head, [-2.46, 72.32, -36.09], [5, 2, 6])
    for i, h in enumerate((0.7, 0.95, 0.9, 0.65)):
        x = -1.6 + i * 0.95
        added["Head"].append(tooth(c, x, round(c["origin"][1] - h, 3), -35.95, 0.35, round(h + 0.15, 3), 0.4))
    return added


def build_geometry():
    data = json.load(open(GEO))
    geo = data["minecraft:geometry"][0]
    bones = {b["name"]: b for b in geo["bones"]}
    r = rng()
    eyes = eye_cubes()
    keep_clear = [([c["origin"][0] - 0.6, c["origin"][1] - 0.6, c["origin"][2] - 0.6], [c["size"][0] + 1.2, c["size"][1] + 1.2, c["size"][2] + 1.2])
                  for c in eyes]
    total = 0
    for name, density in BUMP_BONES.items():
        new = bumps_for_bone(bones[name], density, r)
        if name == "Head":
            new = [c for c in new if not overlaps(c, keep_clear)]
        bones[name]["cubes"].extend(new)
        total += len(new)
    tumors = 0
    r2 = np.random.default_rng(77)
    for name, density in TUMOR_BONES.items():
        new = tumors_for_bone(bones[name], density, r2)
        bones[name]["cubes"].extend(new)
        tumors += len(new)
    tooth_cubes = teeth(bones)
    for name, cubes in tooth_cubes.items():
        bones[name]["cubes"].extend(cubes)
    bones["Head"]["cubes"].extend(eyes)
    with open(GEO, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")
    print("added %d skin bumps, %d tumor cubes, %d teeth and the brows" % (total, tumors, sum(len(v) for v in tooth_cubes.values())))


# ---------------------------------------------------------------- burns

BURN_BONES = ("Head", "Jew", "neck2")
MOUTH = np.array([-0.04, 71.9, -31.5])    # Bedrock pixels (the beam leaves the mouth here: edit_animations.py)


def build_burns():
    """Raw, blistered, charred flesh round the mouth, on the texels only the head, jaw and upper neck use."""
    import render_preview as rp
    bones, tw, th = rp.load_geo(GEO)
    size = 256 * UPSCALE
    shared = np.zeros((size, size), bool)
    weight = np.zeros((size, size))
    for b in bones:
        mouth_bone = b["name"] in BURN_BONES
        for c in b.get("cubes", []):
            if not isinstance(c.get("uv"), list) or c["uv"][1] >= 208:
                if not mouth_bone:
                    continue
            for name, corners, uvs in rp.face_list(c, tw, th):
                px = uvs * size
                u0, v0 = np.floor(px.min(0)).astype(int)
                u1, v1 = np.ceil(px.max(0)).astype(int)
                u0, v0, u1, v1 = max(u0, 0), max(v0, 0), min(u1, size), min(v1, size)
                if u1 <= u0 or v1 <= v0:
                    continue
                if not mouth_bone or c["uv"][1] >= 208:
                    shared[v0:v1, u0:u1] = True
                    continue
                # where each texel of the face is on him (corners: top-left, top-right, bottom-right, bottom-left)
                uu, vv = np.meshgrid(np.linspace(0, 1, u1 - u0), np.linspace(0, 1, v1 - v0))
                p = (corners[0] * ((1 - uu) * (1 - vv))[..., None] + corners[1] * (uu * (1 - vv))[..., None]
                     + corners[2] * (uu * vv)[..., None] + corners[3] * ((1 - uu) * vv)[..., None])
                mouth = np.array([-MOUTH[0], MOUTH[1], MOUTH[2]])
                d = np.linalg.norm((p - mouth) * np.array([1.0, 1.3, 0.8]), axis=-1)
                w = np.exp(-(d / 5.5) ** 2) * np.clip((75.6 - p[..., 1]) / 1.2, 0.0, 1.0)   # below the eyes
                weight[v0:v1, u0:u1] = np.maximum(weight[v0:v1, u0:u1], w)
    weight[shared] = 0.0

    r = np.random.default_rng(91)
    from_hide = hide_detail(size, r, cell=9)
    blotch = smooth_noise(size, 48, r) * 0.6 + smooth_noise(size, 110, r) * 0.4
    wound = weight * 1.25 + (blotch - 0.5) * 0.9
    burnt = wound > 0.32
    core = np.clip((wound - 0.32) / 0.6, 0.0, 1.0)

    img = np.zeros((size, size, 4))
    char = np.array([34.0, 26.0, 24.0])
    raw_dark = np.array([96.0, 18.0, 16.0])
    raw = np.array([188.0, 58.0, 46.0])
    pink = np.array([222.0, 128.0, 112.0])
    col = (char[None, None] * (1 - np.clip(core * 3, 0, 1))[..., None]
           + raw_dark[None, None] * np.clip(core * 3, 0, 1)[..., None] * (1 - np.clip(core * 2 - 0.6, 0, 1))[..., None]
           + raw[None, None] * np.clip(core * 2 - 0.6, 0, 1)[..., None])
    # torn, stringy flesh: the hide's own cracks and nodules show through as darker and lighter tissue
    col *= np.clip(from_hide, 0.6, 1.35)[..., None]
    # blisters: pale, glistening bubbles in the raw flesh
    f1, _ = worley(size, 7, r)
    blister = (f1 < 1.6) & (r.random((size, size)) < 0.5) & (core > 0.15) & (core < 0.85)
    col[blister] = pink * (0.9 + 0.2 * r.random((int(blister.sum()), 1)))
    col[blister & (f1 < 0.7)] = [246, 220, 196]
    img[..., :3] = np.clip(col, 0, 255)
    # alpha: the order it heals in (the rim first, the deepest wounds last), never below the cut-out threshold
    img[..., 3] = np.where(burnt, 0.22 + 0.78 * core ** 0.8, 0.0) * 255
    Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(TEX, "godzilla_burns.png"))

    glow = np.zeros((size, size, 4))
    g1, g2 = worley(size, 11, r)
    crack = (np.abs(g2 - g1) < 1.3) & (core > 0.35)
    glow[crack] = [255, 118, 34, 255]
    glow[crack & (core > 0.7)] = [255, 196, 90, 255]
    Image.fromarray(glow.astype(np.uint8), "RGBA").save(os.path.join(TEX, "godzilla_burns_glow.png"))
    print("burns: %d texels" % burnt.sum())


def main():
    geo = json.load(open(GEO))["minecraft:geometry"][0]
    jaw = next(b for b in geo["bones"] if b["name"] == "Jew")
    if len(jaw["cubes"]) != ADDON_JAW_CUBES:
        raise SystemExit("already detailed; re-run tools/convert_addon.py first")
    build_textures()
    build_geometry()
    build_burns()


if __name__ == "__main__":
    main()
