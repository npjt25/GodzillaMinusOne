"""Builds the hazmat suit (GeckoLib armour: item/HazmatSuitItem, client/render/HazmatSuitRenderer), after a blue
chemical-protection suit with breathing apparatus: a blue hood and coverall, a black full-face mask with a big visor
and a respirator at the chin, a black harness (shoulder straps, chest strap, waist belt and buckle) with a yellow gas
detector and a pressure gauge on it, an air hose, two white air cylinders on a backplate with their valves and
manifold at the bottom, black rubber gloves to mid forearm, and black rubber boots with yellow-green soles; worn and
dirty. One model for all four pieces: GeckoLib shows the hood's bones (armorHead) for the helmet, the coverall's
(armorBody, armorRightArm, armorLeftArm) for the chestplate, the legs' (armorRightLeg, armorLeftLeg) for the leggings
and the boots' (armorRightBoot, armorLeftBoot) for the boots, posing each like the player's own part. Bones and
pivots are GeckoLib's armour template's (the wearer's right at -x). Also the four item icons and the Radiation
effect's icon (the radiation trefoil).

    python3 tools/make_hazmat.py
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_props import pack, smooth_noise  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
SIZE = 128      # texture size in model units
TEXELS = 4      # texture pixels per model unit


def c(x0, y0, z0, x1, y1, z1, kind, **extra):
    """A cube from corner (x0, y0, z0) to (x1, y1, z1) (model units: x to the wearer's left, y up, z to the back)."""
    cube = {"origin": [min(x0, x1), min(y0, y1), min(z0, z1)], "size": [abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)], "kind": kind}
    cube.update(extra)
    return cube


def mirror(cubes):
    """The same cubes on the other side (x -> -x)."""
    out = []
    for q in cubes:
        m = dict(q)
        m["origin"] = [-(q["origin"][0] + q["size"][0]), q["origin"][1], q["origin"][2]]
        out.append(m)
    return out


def cylinder(cx, z_back, y0, y1, d, kind):
    """A round-ish upright cylinder, d across, its front at z_back (it stands out behind that): crossed slabs, the
    narrow one (3 units across, where the label goes) outermost behind and in front."""
    zc = z_back + d / 2
    return [c(cx - d / 2, y0, zc - d * 0.33, cx + d / 2, y1, zc + d * 0.33, kind),
            c(cx - 1.52, y0, zc - d / 2, cx + 1.52, y1, zc + d / 2, kind, label=True),
            c(cx - d * 0.44, y0, zc - d * 0.44, cx + d * 0.44, y1, zc + d * 0.44, kind)]


def model():
    head = [
        # the hood, the visor's black rubber frame, the visor (a bulge in two layers), the respirator and its filter
        c(-4.7, 23.3, -4.6, 4.7, 32.6, 4.8, "hood"),
        c(-3.6, 32.4, -3.6, 3.6, 33.2, 3.8, "hood"),
        c(-4.9, 22.6, -4.4, 4.9, 24.2, 5.0, "hood_skirt"),
        c(-3.9, 24.2, -5.4, 3.9, 31.4, -4.5, "mask"),
        c(-3.3, 26.0, -5.85, 3.3, 30.8, -5.3, "visor"),
        c(-2.6, 26.5, -6.15, 2.6, 30.3, -5.8, "visor"),
        c(-1.55, 23.8, -6.6, 1.55, 26.2, -5.3, "mask"),
        c(-1.05, 24.0, -7.3, 1.05, 26.05, -6.5, "filter"),
        c(1.4, 24.4, -6.4, 2.6, 25.6, -5.2, "valve"),
        # the mask's harness: straps over the hood's sides, top and back
        c(-4.95, 27.2, -4.4, -4.6, 28.2, 2.6, "strap"),
        c(4.6, 27.2, -4.4, 4.95, 28.2, 2.6, "strap"),
        c(-0.6, 32.5, -4.4, 0.6, 33.3, 3.2, "strap"),
        c(-3.6, 27.0, 4.7, 3.6, 28.0, 5.1, "strap"),
    ]
    body = [
        # the coverall: its body, the hood's collar, the zip down the front
        c(-4.6, 11.6, -2.6, 4.6, 24.4, 2.6, "suit"),
        c(-4.4, 23.4, -2.8, 4.4, 24.6, 2.8, "suit_dark"),
        c(-0.3, 12.0, -2.75, 0.3, 23.4, -2.6, "zip"),
        # the harness: shoulder straps over the front and back, the chest strap, the waist belt and its buckle
        c(-3.4, 13.4, -3.0, -2.0, 24.7, -2.55, "strap"),
        c(2.0, 13.4, -3.0, 3.4, 24.7, -2.55, "strap"),
        c(-3.4, 24.4, -3.0, -2.0, 24.9, 2.9, "strap"),
        c(2.0, 24.4, -3.0, 3.4, 24.9, 2.9, "strap"),
        c(-2.0, 18.8, -2.95, 2.0, 19.6, -2.6, "strap"),
        c(-4.85, 11.9, -2.95, 4.85, 13.6, 2.95, "strap"),
        c(-1.05, 11.95, -3.3, 1.05, 13.55, -2.9, "buckle"),
        # the yellow gas detector (the wearer's right) and the pressure gauge on the other strap, with its hose
        c(-4.1, 16.0, -4.05, -2.05, 19.05, -3.0, "detector"),
        c(1.85, 19.5, -3.6, 3.9, 21.55, -2.95, "gauge"),
        c(2.6, 15.8, -3.25, 3.2, 19.6, -2.9, "hose"),
        # the air hose from the respirator over the chest to the right hip
        c(-1.2, 21.6, -3.25, -0.5, 24.4, -2.7, "hose"),
        c(-2.2, 19.6, -3.3, -1.2, 21.8, -2.7, "hose"),
        c(-3.2, 14.0, -3.35, -2.2, 19.8, -2.95, "hose"),
        # the backplate, the two cylinders on it, the strap round them, their valve heads and the manifold
        c(-3.8, 12.8, 2.6, 3.8, 23.6, 3.4, "rubber"),
        *cylinder(-2.0, 3.3, 12.4, 22.6, 4.05, "tank"),
        *cylinder(2.0, 3.3, 12.4, 22.6, 4.05, "tank"),
        c(-3.15, 22.6, 4.2, -0.85, 23.4, 6.45, "tank_neck"),
        c(0.85, 22.6, 4.2, 3.15, 23.4, 6.45, "tank_neck"),
        c(-4.2, 13.8, 3.1, 4.2, 14.6, 7.55, "strap"),
        c(-2.6, 11.3, 4.4, -1.3, 12.6, 5.8, "valve"),
        c(1.3, 11.3, 4.4, 2.6, 12.6, 5.8, "valve"),
        c(-2.0, 10.8, 4.7, 2.0, 11.4, 5.5, "valve"),
        c(-0.5, 10.4, 4.6, 0.5, 11.0, 5.6, "rubber"),
    ]
    right_arm = [
        # the sleeve (baggy), its cuff, the glove up to mid forearm
        c(-8.6, 15.8, -2.6, -3.4, 24.6, 2.6, "suit"),
        c(-8.8, 15.0, -2.8, -3.2, 16.4, 2.8, "rubber"),
        c(-8.5, 11.5, -2.5, -3.5, 15.4, 2.5, "glove"),
    ]
    right_leg = [
        # the trouser leg, baggy at the knee
        c(-4.45, 4.6, -2.55, 0.65, 12.2, 2.55, "suit"),
        c(-4.6, 6.6, -2.7, 0.8, 9.0, 2.7, "suit_dark"),
    ]
    right_boot = [
        # the rubber boot to mid shin, its turned-down top, toe cap and yellow-green sole
        c(-4.55, 0.4, -2.85, 0.75, 7.4, 2.75, "boot"),
        c(-4.75, 6.8, -3.0, 0.95, 7.9, 2.9, "boot_top"),
        c(-4.55, 0.4, -3.6, 0.75, 2.6, -2.8, "boot"),
        c(-4.7, -0.05, -3.8, 0.9, 0.55, 3.0, "sole"),
    ]
    bones = {
        "armorHead": {"pivot": [0, 24, 0], "cubes": head},
        "armorBody": {"pivot": [0, 24, 0], "cubes": body},
        "armorRightArm": {"pivot": [-4, 22, 0], "cubes": right_arm},
        "armorLeftArm": {"pivot": [4, 22, 0], "cubes": mirror(right_arm)},
        "armorRightLeg": {"pivot": [-1.9, 12, 0], "cubes": right_leg},
        "armorLeftLeg": {"pivot": [1.9, 12, 0], "cubes": mirror(right_leg)},
        "armorRightBoot": {"pivot": [-1.9, 12, 0], "cubes": right_boot},
        "armorLeftBoot": {"pivot": [1.9, 12, 0], "cubes": mirror(right_boot)},
    }
    return bones


# ---------------------------------------------------------------- painting

BLUE = np.array([38.0, 92.0, 184.0])
BLUE_DARK = np.array([28.0, 66.0, 140.0])
DIRT = np.array([92.0, 84.0, 70.0])
GRIME = np.array([40.0, 46.0, 58.0])
BLACK = np.array([24.0, 24.0, 27.0])
WHITE = np.array([222.0, 224.0, 226.0])


def net(sx, sy, sz):
    """A cube's box-UV net (model units): name -> (x, y, w, h). north is the front (-z), south the back."""
    return {"top": (sz, 0, sx, sz), "bottom": (sz + sx, 0, sx, sz), "east": (0, sz, sz, sy), "north": (sz, sz, sx, sy),
            "west": (sz + sx, sz, sz, sy), "south": (2 * sz + sx, sz, sx, sy)}


def fabric(h, w, base, rng, dirt_low=0.0, seam=True):
    """Blue coverall fabric: soft folds running down it, creases, a seam or two, and grime, more of it low down."""
    face = np.empty((h, w, 3))
    face[:] = base
    folds = smooth_noise(h, w, max(3.0, w / 3.0), rng)
    stretch = smooth_noise(h, w, 6.0, rng, octaves=3)
    face *= (0.8 + 0.32 * folds[..., None]) * (0.9 + 0.16 * stretch[..., None])
    # creases: short dark diagonal strokes, a highlight beside each
    for _ in range(max(1, (h * w) // 140)):
        y, x = rng.integers(0, h), rng.integers(0, w)
        length = rng.integers(3, 9)
        dx = rng.choice([-1, 1])
        for i in range(length):
            yy, xx = y + i, x + (i // 2) * dx
            if 0 <= yy < h and 0 <= xx < w:
                face[yy, xx] *= 0.72
                if 0 <= xx + 1 < w:
                    face[yy, xx + 1] = np.minimum(255, face[yy, xx + 1] * 1.12)
    if seam and w > 12:
        for x in (w // 4, 3 * w // 4):
            face[:, x] *= 0.78
            face[::3, x] *= 0.85
    # grime: brownish-grey blotches, heavier towards the bottom (dirt_low), and a few splashes
    down = (np.arange(h) / max(1, h - 1))[:, None]
    blot = smooth_noise(h, w, 5.0, rng, octaves=3)
    amount = np.clip((blot - 0.55) * 2.2, 0, 1) * 0.45 + np.clip(down - (1 - dirt_low), 0, 1) * blot * 0.9
    mix = np.where(rng.random((h, w)) < 0.5, 0, 1)[..., None]
    grime = DIRT * (1 - mix) + GRIME * mix
    face = face * (1 - amount[..., None]) + grime * amount[..., None]
    for _ in range((h * w) // 400):
        y, x = rng.integers(0, h), rng.integers(0, w)
        r = rng.integers(1, 3)
        face[max(0, y - r):y + r, max(0, x - r):x + r] = face[max(0, y - r):y + r, max(0, x - r):x + r] * 0.6 + DIRT * 0.4
    return face


def rubber(h, w, base, rng, gloss=0.25):
    """Black rubber: a soft sheen across its middle, scuffed, a little dust."""
    face = np.empty((h, w, 3))
    face[:] = base
    xs = (np.arange(w) / max(1, w - 1))[None, :]
    face += (gloss * 60.0 * np.exp(-((xs - 0.35) ** 2) / 0.02))[..., None]
    face *= (0.9 + 0.2 * smooth_noise(h, w, 4.0, rng)[..., None])
    dust = np.clip(smooth_noise(h, w, 3.0, rng, octaves=3) - 0.62, 0, 1) * 1.2
    face = face * (1 - dust[..., None]) + DIRT * dust[..., None]
    return face


def webbing(h, w, rng):
    """Black strap webbing: woven rows, stitching along its edges."""
    face = rubber(h, w, BLACK, rng, gloss=0.08)
    face[::2] *= 0.85
    if h > 4 and w > 4:
        face[1, ::3] = [92, 92, 96]
        face[-2, ::3] = [92, 92, 96]
    return face


def paint(bones, seed=7):
    """Each cube's faces where GeckoLib looks for them: its box UV net with the cube's size rounded down (a face less
    than a unit across is drawn from one row or column of the next one's). Its whole place on the sheet is first
    filled with its kind's colour, so those edges are never see-through."""
    rng = np.random.default_rng(seed)
    n = SIZE * TEXELS
    img = np.zeros((n, n, 4))
    for bname, bone in bones.items():
        for q in bone["cubes"]:
            u, v = q["uv"]
            cx, cy, cz = (max(1, math.ceil(k)) for k in q["size"])
            sx, sy, sz = (math.floor(k) for k in q["size"])
            kind = q["kind"]
            y0, x0 = v * TEXELS, u * TEXELS
            h, w = (cy + cz) * TEXELS, 2 * (cx + cz) * TEXELS
            img[y0:y0 + h, x0:x0 + w, :3] = np.clip(paint_face(kind, "fill", h, w, bname, rng), 0, 255)
            img[y0:y0 + h, x0:x0 + w, 3] = 255
            for name, (fx, fy, fw, fh) in net(sx, sy, sz).items():
                h, w = fh * TEXELS, fw * TEXELS
                if h == 0 or w == 0:
                    continue
                face = paint_face(kind, name, h, w, bname, rng, label=q.get("label", False))
                y0, x0 = (v + fy) * TEXELS, (u + fx) * TEXELS
                img[y0:y0 + h, x0:x0 + w, :3] = np.clip(face, 0, 255)
                img[y0:y0 + h, x0:x0 + w, 3] = 255
    return img


def paint_face(kind, name, h, w, bone, rng, label=False):
    low = 0.0
    if bone.endswith("Leg"):
        low = 0.6
    elif bone == "armorBody":
        low = 0.25
    if kind in ("suit", "hood", "suit_dark", "hood_skirt"):
        base = BLUE_DARK if kind in ("suit_dark", "hood_skirt") else BLUE * (1.06 if kind == "hood" else 1.0)
        face = fabric(h, w, base, rng, dirt_low=low, seam=kind == "suit")
        if kind == "hood" and name == "south":
            face[h // 3:h // 3 + 2, :] *= 0.7    # the hood's back seam
        return face
    if kind in ("rubber", "mask", "boot", "boot_top", "glove"):
        face = rubber(h, w, BLACK if kind != "glove" else BLACK * 1.1, rng, gloss=0.35 if kind in ("boot", "glove") else 0.2)
        if kind == "boot" and name in ("north", "south", "east", "west"):
            # mud splashed up the boot from the sole
            down = (np.arange(h) / max(1, h - 1))[:, None]
            mud = np.clip(down - 0.55, 0, 1) * smooth_noise(h, w, 2.5, rng) * 1.6
            face = face * (1 - mud[..., None]) + DIRT * mud[..., None]
        if kind == "glove":
            face[::5] *= 0.8                          # the glove's wrinkles round the wrist
        return face
    if kind == "strap":
        return webbing(h, w, rng)
    if kind == "zip":
        face = np.empty((h, w, 3))
        face[:] = BLUE_DARK * 0.8
        face[::2, w // 2 - 1:w // 2 + 1] = [150, 150, 150]
        return face
    if kind == "hose":
        face = rubber(h, w, BLACK, rng, gloss=0.3)
        face[::3] *= 0.6                             # corrugated
        return face
    if kind == "visor":
        face = np.empty((h, w, 3))
        ys, xs = np.mgrid[0:h, 0:w] / np.array([max(1, h - 1), max(1, w - 1)])[:, None, None]
        face[:] = np.array([30.0, 50.0, 64.0])
        face += (28.0 * (1 - ys))[..., None] * np.array([0.6, 0.9, 1.0])
        # reflections: two diagonal streaks of light, and a softer glow over the top
        streak = np.abs((xs - ys * 0.55) - 0.25) < 0.05
        face[streak] = face[streak] * 0.3 + np.array([210.0, 230.0, 240.0]) * 0.7
        streak2 = np.abs((xs - ys * 0.55) - 0.36) < 0.02
        face[streak2] = face[streak2] * 0.5 + np.array([190.0, 210.0, 225.0]) * 0.5
        if name == "north":
            face[:2, :] = face[:2, :] * 0.5          # the frame's shadow along the top
        return face
    if kind == "filter":
        face = rubber(h, w, np.array([52.0, 52.0, 56.0]), rng, gloss=0.4)
        if name == "north":
            face[h // 3::3, w // 4:w - w // 4] *= 0.4  # the filter's slots
        return face
    if kind == "valve":
        face = np.empty((h, w, 3))
        face[:] = np.array([168.0, 170.0, 174.0])
        face *= (0.8 + 0.35 * (np.arange(w) / max(1, w - 1))[None, :, None])
        face[:, ::3] *= 0.85
        return face
    if kind == "buckle":
        face = np.empty((h, w, 3))
        face[:] = np.array([150.0, 152.0, 156.0])
        face[1:-1, 1:-1] = np.array([60.0, 60.0, 64.0])
        face[h // 2 - 1:h // 2 + 1, 1:-1] = np.array([150.0, 152.0, 156.0])
        return face
    if kind == "detector":
        face = np.empty((h, w, 3))
        face[:] = np.array([226.0, 186.0, 28.0])
        face *= (0.88 + 0.2 * smooth_noise(h, w, 3.0, rng)[..., None])
        if name == "north":
            face[h // 6:h // 2, w // 5:w - w // 5] = np.array([40.0, 48.0, 40.0])   # the display
            face[h // 6 + 2:h // 6 + 4, w // 5 + 2:w // 2] = np.array([120.0, 200.0, 90.0])
            face[h // 2 + 3:h // 2 + 6, w // 4:w // 4 + 3] = BLACK                  # its buttons
            face[h // 2 + 3:h // 2 + 6, w - w // 4 - 3:w - w // 4] = BLACK
            face[-3:, :] *= 0.6
        return face
    if kind == "gauge":
        face = rubber(h, w, BLACK, rng, gloss=0.3)
        if name == "north":
            yy, xx = np.mgrid[0:h, 0:w]
            r = (min(h, w) / 2) - 1
            cy, cx = (h - 1) / 2, (w - 1) / 2
            dial = (yy - cy) ** 2 + (xx - cx) ** 2 <= r * r
            face[dial] = np.array([235.0, 235.0, 230.0])
            for t in np.linspace(0, 1, 12):
                a = math.radians(225 - 270 * t)
                face[int(round(cy - math.sin(a) * (r - 1))), int(round(cx + math.cos(a) * (r - 1)))] = BLACK
            for i in range(int(r)):
                a = math.radians(70)
                face[int(round(cy - math.sin(a) * i)), int(round(cx + math.cos(a) * i))] = np.array([200.0, 30.0, 30.0])
        return face
    if kind in ("tank", "tank_neck"):
        face = np.empty((h, w, 3))
        face[:] = WHITE if kind == "tank" else np.array([60.0, 62.0, 66.0])
        xs = (np.arange(w) / max(1, w - 1))[None, :]
        face *= (0.78 + 0.3 * np.sin(np.pi * xs) ** 0.6)[..., None]          # round: darker at its sides
        face *= (0.93 + 0.1 * smooth_noise(h, w, 4.0, rng)[..., None])
        if kind == "tank" and name in ("north", "south", "east", "west") and h > 20:
            top = int(h * 0.12)
            face[top:top + 6] = face[top:top + 6] * 0.55 + np.array([120.0, 124.0, 130.0]) * 0.45   # a grey band
            face[int(h * 0.8):int(h * 0.8) + 3] *= 0.8
            if name == "south" and label:
                # the red diamond hazard label, and the lettering under it
                cy, cx, r = int(h * 0.38), w // 2, min(w // 3, 7)
                yy, xx = np.mgrid[0:h, 0:w]
                diamond = np.abs(yy - cy) + np.abs(xx - cx) <= r
                face[diamond] = np.array([196.0, 32.0, 30.0])
                inner = np.abs(yy - cy) + np.abs(xx - cx) <= max(1, r - 3)
                face[inner] = np.array([235.0, 235.0, 235.0])
                for row in range(int(h * 0.55), int(h * 0.72), 3):
                    face[row, w // 5:w - w // 5:2] = np.array([70.0, 70.0, 76.0])
            # scuffs
            for _ in range(4):
                y, x = rng.integers(0, h), rng.integers(0, w)
                face[y:y + 1, x:x + 3] *= 0.7
        return face
    if kind == "sole":
        face = np.empty((h, w, 3))
        face[:] = np.array([186.0, 204.0, 88.0])
        face *= (0.85 + 0.2 * smooth_noise(h, w, 2.0, rng)[..., None])
        if name == "bottom":
            face[::3] *= 0.7                         # the tread
        return face
    raise SystemExit("no paint for " + kind)


def write_model(bones):
    pack(bones, SIZE)
    img = paint(bones)
    Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(ASSETS, "textures", "armor", "hazmat_suit.png"))
    out = []
    for bname, b in bones.items():
        cubes = [{"origin": [round(k, 3) for k in q["origin"]], "size": [round(k, 3) for k in q["size"]], "uv": q["uv"]}
                 for q in b["cubes"]]
        out.append({"name": bname, "pivot": b["pivot"], "cubes": cubes})
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.hazmat_suit", "texture_width": SIZE, "texture_height": SIZE,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out}]}
    with open(os.path.join(ASSETS, "geo", "armor", "hazmat_suit.geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
        f.write("\n")
    with open(os.path.join(ASSETS, "animations", "armor", "hazmat_suit.animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": {}}, f, indent=1)
        f.write("\n")
    print("wrote hazmat_suit", sum(len(b["cubes"]) for b in bones.values()), "cubes")


# ---------------------------------------------------------------- icons

PALETTE = {
    ".": None, "B": (38, 92, 184), "b": (26, 64, 132), "L": (78, 132, 214), "K": (22, 22, 26), "k": (60, 60, 66),
    "V": (44, 70, 88), "v": (150, 196, 220), "W": (226, 228, 230), "w": (170, 172, 178), "Y": (230, 190, 30),
    "G": (186, 204, 88), "R": (196, 32, 30), "S": (160, 162, 168), "D": (100, 92, 76),
}

ICONS = {
    "hazmat_hood": [
        "................",
        ".....bBBBBb.....",
        "....bBLLBBBb....",
        "...bBLBBBBBBb...",
        "...BBKKKKKKBB...",
        "..bBKVvVVVVKBb..",
        "..bBKVVvVVVKBb..",
        "..bBKVVVvVVKBb..",
        "..bBKVVVVVVKBb..",
        "..bBBKKKKKKBBb..",
        "...bBBKkkKBBb...",
        "...bBBKSSKBBb...",
        "....bBBKKBBb....",
        "....bbBBBBbb....",
        ".....bbbbbb.....",
        "................",
    ],
    "hazmat_suit": [
        "................",
        "...WW.bBBb.WW...",
        "..bWWBBBBBBWWb..",
        ".bBKBBBBBBBBKBb.",
        ".BBKBYBBBBSBKBB.",
        ".BBKBYBBBBBBKBB.",
        ".BBKKKKKKKKKKBB.",
        ".BBKBBBBBBBBKBB.",
        ".bBbBBBBBBBBbBb.",
        ".bBbBBBBBBBBbBb.",
        ".KKbBBBBBBBBbKK.",
        ".KK.KKKSSKKK.KK.",
        "....bBBBBBBb....",
        "....bBBbbBBb....",
        "................",
        "................",
    ],
    "hazmat_trousers": [
        "................",
        "...KKKKKKKKKK...",
        "...BBBBBBBBBB...",
        "...BBLBBBBLBB...",
        "...BBBBbbBBBB...",
        "...BBLBbbBLBB...",
        "...BBBB..BBBB...",
        "...bBBB..BBBb...",
        "...BBLB..BLBB...",
        "...BBBB..BBBB...",
        "...BBDB..BDBB...",
        "...BBBB..BBBB...",
        "...bBBb..bBBb...",
        "...bbbb..bbbb...",
        "................",
        "................",
    ],
    "hazmat_boots": [
        "................",
        "................",
        "..KKKK....KKKK..",
        "..KkKK....KkKK..",
        "..KkKK....KkKK..",
        "..KkKK....KkKK..",
        "..KKKK....KKKK..",
        "..KKKK....KKKK..",
        "..KKKK....KKKK..",
        ".KKKKKK..KKKKKK.",
        ".KKKKKKK.KKKKKKK",
        ".DKKKKKK.DKKKKKK",
        ".GGGGGGG.GGGGGGG",
        "................",
        "................",
        "................",
    ],
}


def write_icons():
    for name, rows in ICONS.items():
        img = Image.new("RGBA", (16, 16))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                col = PALETTE[ch]
                if col:
                    img.putpixel((x, y), col + (255,))
        img.save(os.path.join(ASSETS, "textures", "item", name + ".png"))
        with open(os.path.join(ASSETS, "models", "item", name + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "godzillaminusone:item/" + name}}, f, indent=2)
            f.write("\n")
    print("wrote", len(ICONS), "icons")


def write_radiation_icon():
    """The Radiation effect's icon (18 x 18): the radiation trefoil, black on a yellow disc."""
    n, s = 18, 8
    big = n * s
    yy, xx = np.mgrid[0:big, 0:big] + 0.5
    cx = cy = big / 2
    r = np.hypot(xx - cx, yy - cy) / s
    ang = (np.degrees(np.arctan2(cy - yy, xx - cx)) + 360.0) % 360.0
    disc = r <= 8.6
    rim = (r > 7.7) & disc
    blades = (r >= 2.4) & (r <= 7.0) & (((ang - 30.0) % 120.0) < 60.0)
    hub = r <= 1.6
    rgba = np.zeros((big, big, 4))
    rgba[disc] = [250, 210, 30, 255]
    rgba[rim] = [40, 34, 10, 255]
    rgba[blades | hub] = [20, 18, 12, 255]
    img = Image.fromarray(rgba.astype(np.uint8), "RGBA").resize((n, n), Image.LANCZOS)
    out = os.path.join(ASSETS, "textures", "mob_effect")
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, "radiation.png"))
    print("wrote the Radiation icon")


def main():
    for d in (("textures", "armor"), ("geo", "armor"), ("animations", "armor")):
        os.makedirs(os.path.join(ASSETS, *d), exist_ok=True)
    write_model(model())
    write_icons()
    write_radiation_icon()


if __name__ == "__main__":
    main()
