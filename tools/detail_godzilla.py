"""Adds detail to the add-on's Godzilla, in its own blocky style (the high-detail Sketchfab model by ArachnoBoy
was only used as a reference for how rugged he should look):

  * the skin texture at 4x the add-on's resolution, with keloid-like nodules and cracks over the whole hide,
  * raised skin bumps (small cubes) over the body, neck, arms, legs and tail,
  * rows of teeth along both jaws.

    python3 tools/detail_godzilla.py

Run after tools/convert_addon.py (it works on the add-on files that script writes, and refuses to run twice).
"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
GEO = os.path.join(ASSETS, "geo", "entity", "godzilla.geo.json")
TEX = os.path.join(ASSETS, "textures", "entity")
UPSCALE = 4                       # texels per model pixel (the add-on's textures have 2)
ADDON_JAW_CUBES = 8               # the add-on's lower jaw; more means this script already ran

# Where the new cubes' textures go: rows 208-255 of the 256x256 UV space are unused by the add-on.
SKIN_SWATCHES = [(0, 208), (16, 208), (32, 208), (48, 208)]   # 16x16 each, sampled from his hide
TOOTH_SWATCH = (64, 208)
SWATCH = 16

# Bones that get skin bumps, with how many per 100 square pixels of surface.
BUMP_BONES = {
    "Body": 1.6, "Metalbody": 1.6, "Chest": 1.4, "Neck": 1.6, "neck2": 1.8, "Head": 0.9,
    "bone11": 1.4, "bone13": 1.4, "bone14": 1.4, "bone16": 1.2, "bone17": 1.0, "bone18": 1.0,
    "Leg": 1.6, "Leg_bone": 1.6, "Leg2": 1.6, "Leg_bone2": 1.6, "Foot": 0.8, "Foot2": 0.8,
    "Right_arm": 1.6, "Left_arm": 1.6, "bone": 1.4, "bone4": 1.4,
}


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


def upscale(img, factor):
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def detail_texture(name, detail, emissive):
    img = upscale(Image.open(os.path.join(TEX, name)).convert("RGBA"), UPSCALE // 2)
    a = np.asarray(img).astype(float)
    mul = np.where(emissive[..., None], 1.0, detail[..., None])
    a[..., :3] = np.clip(a[..., :3] * mul, 0, 255)
    return a


def add_swatches(a, r, teeth):
    """Paints the new cubes' texture swatches, sampled from the hide already on the sheet."""
    px = UPSCALE
    body = a[40 * px:200 * px, 0:200 * px]
    opaque = body[body[..., 3] > 200][:, :3]
    mean = np.median(opaque, axis=0)
    for (u, v) in SKIN_SWATCHES:
        patch = np.empty((SWATCH * px, SWATCH * px, 4))
        tone = mean * (0.85 + 0.3 * r.random())
        noise = 0.85 + 0.3 * r.random((SWATCH * px, SWATCH * px))
        patch[..., :3] = np.clip(tone * noise[..., None], 0, 255)
        patch[..., 3] = 255
        a[v * px:(v + SWATCH) * px, u * px:(u + SWATCH) * px] = patch
    u, v = TOOTH_SWATCH
    patch = np.empty((SWATCH * px, SWATCH * px, 4))
    patch[..., :3] = np.array(teeth) * (0.9 + 0.1 * r.random((SWATCH * px, SWATCH * px)))[..., None]
    patch[..., 3] = 255
    a[v * px:(v + SWATCH) * px, u * px:(u + SWATCH) * px] = patch


def build_textures():
    r = rng()
    glow = np.asarray(upscale(Image.open(os.path.join(TEX, "godzilla_glow.png")).convert("RGBA"), UPSCALE // 2))
    emissive = glow[..., 3] > 0
    size = glow.shape[0]
    detail = hide_detail(size, r)
    for name, teeth in (("godzilla.png", (172, 164, 146)), ("godzilla_dead.png", (96, 90, 84))):
        a = detail_texture(name, detail, emissive)
        add_swatches(a, rng(), teeth)
        Image.fromarray(a.astype(np.uint8), "RGBA").save(os.path.join(TEX, name))
    for name in ("godzilla_glow.png", "godzilla_charged_glow.png"):
        upscale(Image.open(os.path.join(TEX, name)).convert("RGBA"), UPSCALE // 2).save(os.path.join(TEX, name))


# ---------------------------------------------------------------- geometry

FACES = [  # axis, sign
    (0, 1), (0, -1), (1, 1), (2, 1), (2, -1)   # no bumps on undersides (-y)
]


def with_frame(cube, new):
    for k in ("rotation", "pivot"):
        if k in cube:
            new[k] = list(cube[k])
    return new


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
    total = 0
    for name, density in BUMP_BONES.items():
        new = bumps_for_bone(bones[name], density, r)
        bones[name]["cubes"].extend(new)
        total += len(new)
    tooth_cubes = teeth(bones)
    for name, cubes in tooth_cubes.items():
        bones[name]["cubes"].extend(cubes)
    with open(GEO, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")
    print("added %d skin bumps and %d teeth" % (total, sum(len(v) for v in tooth_cubes.values())))


def main():
    geo = json.load(open(GEO))["minecraft:geometry"][0]
    jaw = next(b for b in geo["bones"] if b["name"] == "Jew")
    if len(jaw["cubes"]) != ADDON_JAW_CUBES:
        raise SystemExit("already detailed; re-run tools/convert_addon.py first")
    build_textures()
    build_geometry()


if __name__ == "__main__":
    main()
