"""Builds the GeckoLib models and textures of the mod's own props:

  * the searchlight: a WWII-style drum searchlight on a yoke, on a turntable with a seat for its operator;
    bone "turret" turns with the operator, bone "lamp" tilts up and down (client/render/SearchlightRenderer),
  * the nuclear bomb: a stubby, finned bomb in olive drab, built nose-down (it falls nose first),
  * the Shinsei Maru: the film's small, run-down, weather-beaten minesweeper (a converted fishing boat), built at
    half size (the renderer doubles it): a dark hull weeping rust, paint flaking off it, a rust-red waterline, tyres
    for fenders, a weathered wheelhouse with the wheel up on its roof, a mast and its stays, cages, nets and floats
    on the foredeck, boarding ladders port and starboard, a davit and a rack of recovered naval mines astern, and the
    Type 93 13 mm machine gun on a platform at the stern pointing aft (bones "gun" turns, "gun_barrel" tilts;
    "mine1".."mine3" show the mines aboard; client/render/ShinseiMaruRenderer).

    python3 tools/make_props.py
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import noise  # noqa: E402  (worley noise for the metal's wear)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
TEXELS = 4


def box(cx, cy, cz, sx, sy, sz, kind, rot=None, pivot=None):
    c = {"origin": [cx - sx / 2, cy - sy / 2, cz - sz / 2], "size": [sx, sy, sz], "kind": kind}
    if rot:
        c["rotation"] = list(rot)
        c["pivot"] = list(pivot if pivot else (cx, cy, cz))
    return c


def octagon(cx, cy, cz, d, length, kind, axis="z"):
    """A round-ish prism of diameter d along the given axis (a stepped circle: two crossed slabs and a square)."""
    shapes = ((d, d * 0.6), (d * 0.6, d), (d * 0.86, d * 0.86))
    if axis == "z":
        return [box(cx, cy, cz, a, b, length, kind) for a, b in shapes]
    return [box(cx, cy, cz, a, length, b, kind) for a, b in shapes]


def pack(bones, size):
    items = []
    for bone in bones.values():
        for c in bone["cubes"]:
            sx, sy, sz = (max(1, math.ceil(v)) for v in c["size"])
            items.append((sy + sz, 2 * (sx + sz), c))
    items.sort(key=lambda it: (-it[0], -it[1]))
    x = y = shelf = 0
    for hgt, wid, c in items:
        if x + wid > size:
            x, y, shelf = 0, y + shelf, 0
        if y + hgt > size:
            raise SystemExit("texture sheet full")
        c["uv"] = [x, y]
        x += wid
        shelf = max(shelf, hgt)


def paint(bones, size, palette, glow_kinds, seed, texels=TEXELS, planks=None, weather=None):
    """Each cube's UV net in its kind's colour, worn; kinds in {@code planks} get plank seams: "h" across the
    texture's rows (strakes along a hull's side), "v" down its columns (deck boards along the top face); kinds in
    {@code weather} ({kind: amount}) are weathered: rust runs streaking down them, blotches of rust and grime, and
    paint flaking off in patches."""
    TEXELS = texels  # noqa: N806
    planks = planks or {}
    weather = weather or {}
    rng = np.random.default_rng(seed)
    n = size * TEXELS
    f1, f2 = noise.worley(n, 9, rng)
    wear = 0.9 + 0.12 * np.clip(1 - f1 / 6, 0, 1) - 0.1 * np.clip(1 - (f2 - f1) / 1.5, 0, 1) ** 2
    img = np.zeros((n, n, 4))
    glow = np.zeros((n, n, 4))
    for bone in bones.values():
        for c in bone["cubes"]:
            u, v = c["uv"]
            sx, sy, sz = (max(1, math.ceil(q)) for q in c["size"])
            w, h = 2 * (sx + sz) * TEXELS, (sy + sz) * TEXELS
            region = np.empty((h, w, 3))
            region[:] = palette[c["kind"]]
            region *= wear[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, None]
            region *= (0.95 + 0.1 * rng.random((h, w)))[..., None]
            if c["kind"] == "band":
                region[:] = palette["band"]
            direction = planks.get(c["kind"])
            if direction:
                step = 3 * TEXELS
                axis = 0 if direction == "h" else 1
                count = region.shape[axis]
                idx = np.arange(count)
                tint = 0.85 + 0.3 * rng.random(count // step + 2)[idx // step]   # each plank a little different
                seam = np.where(idx % step == 0, 0.55, 1.0)
                shade = tint * seam
                region *= shade[:, None, None] if axis == 0 else shade[None, :, None]
            amount = weather.get(c["kind"])
            if amount:
                region = weathered(region, amount, rng)
            img[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, :3] = np.clip(region, 0, 255)
            img[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w, 3] = 255
            if c["kind"] in glow_kinds:
                glow[v * TEXELS:v * TEXELS + h, u * TEXELS:u * TEXELS + w] = list(glow_kinds[c["kind"]]) + [255]
    return img, glow


RUST = np.array([118.0, 58.0, 30.0])
RUST_DARK = np.array([62.0, 34.0, 22.0])
GRIME = np.array([34.0, 30.0, 26.0])
PRIMER = np.array([138.0, 70.0, 48.0])


def weathered(region, amount, rng):
    """Rust weeping down in streaks (from the top of the texture net: down the sides of a box), blotches of rust and
    grime, and patches where the paint has flaked off to the red primer under it."""
    h, w = region.shape[:2]
    out = region.copy()
    # streaks: a few columns, each running down a random length, fading as it goes, a little wavy
    streak = np.zeros((h, w))
    for _ in range(max(1, int(w * 0.1 * amount))):
        x = rng.integers(0, w)
        top = rng.integers(0, max(1, h // 2))
        length = rng.integers(max(2, h // 6), max(3, h))
        width = rng.integers(1, 3)
        ys = np.arange(top, min(h, top + length))
        fade = 1.0 - (ys - top) / max(1, length)
        for k, y in enumerate(ys):
            xx = min(w - 1, max(0, x + int(round(np.sin(k * 0.4) * 0.6))))
            streak[y, xx:xx + width] = np.maximum(streak[y, xx:xx + width], fade[k] * (0.6 + 0.4 * rng.random()))
    # blotches: smooth noise, thresholded
    blot = rng.random((max(2, h // 4 + 2), max(2, w // 4 + 2)))
    blot = np.kron(blot, np.ones((4, 4)))[:h, :w]
    rust = np.clip((blot - (1.0 - 0.22 * amount)) * 2.5, 0.0, 1.0)
    flake = (rng.random((h, w)) < 0.04 * amount) | (np.kron(rng.random((max(2, h // 3 + 2), max(2, w // 3 + 2))), np.ones((3, 3)))[:h, :w] > 1.0 - 0.08 * amount)
    mix = np.clip(streak * 0.85 * amount + rust * 0.7, 0.0, 1.0)[..., None]
    colour = RUST * (1 - streak[..., None] * 0.5) + RUST_DARK * streak[..., None] * 0.5
    out = out * (1 - mix) + colour * mix
    out[flake] = PRIMER * (0.8 + 0.3 * rng.random((int(flake.sum()), 1)))
    grime = np.clip(rng.random((h, w)) * 0.25 * amount, 0, 1)[..., None]
    return out * (1 - grime) + GRIME * grime


def write(name, bones, size, palette, glow_kinds=None, seed=1, texels=TEXELS, planks=None, bounds=4, weather=None, styles=None):
    """styles (kind -> how it's painted, see paint_boat) paints the texture face by face instead of with paint()."""
    pack(bones, size)
    if styles is not None:
        img, glow = paint_boat(bones, size, palette, seed, texels, styles), None
    else:
        img, glow = paint(bones, size, palette, glow_kinds or {}, seed, texels, planks, weather)
    tex = os.path.join(ASSETS, "textures", "entity")
    Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(tex, name + ".png"))
    if glow_kinds:
        Image.fromarray(glow.astype(np.uint8), "RGBA").save(os.path.join(tex, name + "_glow.png"))
    out = []
    for bname, b in bones.items():
        entry = {"name": bname, "pivot": b["pivot"]}
        if b.get("parent"):
            entry["parent"] = b["parent"]
        cubes = []
        for c in b["cubes"]:
            cube = {"origin": [round(q, 3) for q in c["origin"]], "size": [round(q, 3) for q in c["size"]], "uv": c["uv"]}
            for k in ("rotation", "pivot"):
                if k in c:
                    cube[k] = [round(q, 3) for q in c[k]]
            cubes.append(cube)
        if cubes:
            entry["cubes"] = cubes
        out.append(entry)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + name, "texture_width": size, "texture_height": size,
                        "visible_bounds_width": bounds, "visible_bounds_height": bounds, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out}]}
    with open(os.path.join(ASSETS, "geo", "entity", name + ".geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
        f.write("\n")
    print("wrote", name, sum(len(b["cubes"]) for b in bones.values()), "cubes")


# ---------------------------------------------------------------- the Shinsei Maru's weathered paint

RUST_DARK2 = np.array([70.0, 36.0, 22.0])
RUST_MID = np.array([122.0, 62.0, 32.0])
RUST_LIGHT = np.array([160.0, 92.0, 50.0])
BARE = np.array([104.0, 102.0, 98.0])
ALGAE = np.array([58.0, 66.0, 40.0])


def smooth_noise(h, w, cell, rng, octaves=2):
    """Value noise in [0, 1]: random values on a coarse grid, bilinearly blended, a finer octave on top."""
    out = np.zeros((h, w))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        c = max(1.0, cell / (2 ** o))
        gh, gw = int(h / c) + 2, int(w / c) + 2
        grid = rng.random((gh, gw))
        ys, xs = np.arange(h) / c, np.arange(w) / c
        y0, x0 = np.floor(ys).astype(int), np.floor(xs).astype(int)
        fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
        fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
        a = grid[y0][:, x0]
        b = grid[y0][:, x0 + 1]
        cc = grid[y0 + 1][:, x0]
        d = grid[y0 + 1][:, x0 + 1]
        out += amp * ((a * (1 - fx) + b * fx) * (1 - fy) + (cc * (1 - fx) + d * fx) * fy)
        total += amp
        amp *= 0.5
    return out / total


def faces(sx, sy, sz):
    """The faces of a box's UV net (Bedrock box UV, in texels): (name, x, y, width, height). Sides run top to bottom."""
    return [("top", sz, 0, sx, sz), ("bottom", sz + sx, 0, sx, sz),
            ("side", 0, sz, sz, sy), ("side", sz, sz, sx, sy), ("side", sz + sx, sz, sz, sy), ("side", 2 * sz + sx, sz, sx, sy)]


def rusty_side(face, base, amount, t, rng, primer=True, algae=0.0):
    """A painted side, weathered: grime darkening towards the bottom, rust running down from the top edge in streaks
    (darker, browner as they go), rust blooms with dark cores, flakes of paint gone to the primer (or bare metal) with
    a dark rim, and the edges worn pale."""
    h, w = face.shape[:2]
    if h == 0 or w == 0:
        return face
    out = face.copy()
    down = (np.arange(h) / max(1, h - 1))[:, None]
    if min(h, w) <= 2 * t:
        # a thin rail or rung: just rusty, here and there (flakes and worn edges would stripe it)
        n = smooth_noise(h, w, 2.0 * t, rng)
        m = (np.clip((n - (1.0 - 0.45 * amount)) / 0.2, 0, 1) * 0.8)[..., None]
        return out * (1 - m) + RUST_MID * m
    out *= (1.0 - 0.22 * amount * down ** 1.5)[..., None]
    # streaks: from points along the top, running down, wavering, fading; a few long ones, more short ones
    for _ in range(int(w * 0.09 * amount * t) + (1 if rng.random() < amount else 0)):
        x = rng.integers(0, w)
        length = int(h * rng.uniform(0.25, 1.0))
        width = rng.integers(1, max(2, t + 1))
        strength = rng.uniform(0.35, 0.8) * amount
        for k in range(length):
            xx = int(np.clip(x + round(np.sin(k * 0.35 + x) * 0.7), 0, w - 1))
            f = (1.0 - k / max(1, length)) ** 0.8 * strength
            col = RUST_MID * (1 - k / max(1, length)) + RUST_DARK2 * (k / max(1, length))
            out[k, xx:xx + width] = out[k, xx:xx + width] * (1 - f) + col * f
    # rust blooms: noise above a threshold, dark at the heart, orange round it
    n = smooth_noise(h, w, 4.0 * t, rng)
    bloom = np.clip((n - (1.0 - 0.34 * amount)) / 0.12, 0, 1)
    heart = np.clip((n - (1.0 - 0.14 * amount)) / 0.06, 0, 1)
    rust_col = RUST_LIGHT * (1 - heart[..., None]) + RUST_DARK2 * heart[..., None]
    out = out * (1 - 0.85 * bloom[..., None]) + rust_col * (0.85 * bloom[..., None])
    # flakes: patches where the paint has gone, to the red primer or bare metal, rimmed dark
    f = smooth_noise(h, w, 3.0 * t, rng)
    flake = f > 1.0 - 0.1 * amount
    rim = np.zeros_like(flake)
    rim[1:, :] |= flake[:-1, :] & ~flake[1:, :]
    rim[:, 1:] |= flake[:, :-1] & ~flake[:, 1:]
    under = PRIMER if primer else BARE
    out[flake] = under * (0.85 + 0.25 * rng.random((int(flake.sum()), 1)))
    out[rim] *= 0.7
    # worn edges: the paint rubbed pale along the face's edges, here and there
    edge = np.zeros((h, w), bool)
    edge[0, :] = edge[-1, :] = True
    edge[:, 0] = edge[:, -1] = True
    worn = edge & (rng.random((h, w)) < 0.45 * amount)
    out[worn] = out[worn] * 0.6 + np.array([150.0, 146.0, 136.0]) * 0.4
    if algae > 0:
        band = (np.clip((down - 0.6) / 0.4, 0, 1) * algae * (0.6 + 0.4 * smooth_noise(h, w, 2.0 * t, rng)))[..., None]
        out = out * (1 - band) + ALGAE * band
    return out


def planked(face, base, t, rng, along_rows, stain=0.0):
    """Planks (3 model pixels wide) laid along the face's rows or columns: each its own shade, dark seams, wood grain
    running along it, a nail at each end of each length; oil and water stains on top."""
    h, w = face.shape[:2]
    if h == 0 or w == 0:
        return face
    step = 3 * t
    across = np.arange(h) if along_rows else np.arange(w)
    plank = across // step
    tint = 0.82 + 0.3 * rng.random(int(plank.max()) + 2)
    seam = (across % step == 0)
    grain_dir = (h, w)
    g = smooth_noise(h, w, 1.0, rng, octaves=1)
    # grain: noise stretched along the planks
    stretch = smooth_noise(*(grain_dir), 6.0 * t, rng)
    if along_rows:
        grain = 0.9 + 0.2 * (0.6 * stretch + 0.4 * np.repeat(g[:, :1], w, axis=1))
        shade = (tint[plank] * np.where(seam, 0.55, 1.0))[:, None]
    else:
        grain = 0.9 + 0.2 * (0.6 * stretch + 0.4 * np.repeat(g[:1, :], h, axis=0))
        shade = (tint[plank] * np.where(seam, 0.55, 1.0))[None, :]
    out = face * (shade * grain)[..., None]
    # plank ends with nails every ~16 model pixels along
    length = w if along_rows else h
    for p in range(int(plank.max()) + 1):
        offset = rng.integers(0, 16 * t)
        for end in range(offset, length, 16 * t):
            mid = p * step + step // 2
            if along_rows and mid < h:
                out[min(h - 1, p * step):min(h, (p + 1) * step), end:end + 1] *= 0.7
                out[mid, min(w - 1, end + t)] *= 0.45
            elif not along_rows and mid < w:
                out[end:end + 1, min(w - 1, p * step):min(w, (p + 1) * step)] *= 0.7
                out[min(h - 1, end + t), mid] *= 0.45
    if stain > 0:
        st = smooth_noise(h, w, 5.0 * t, rng)
        m = np.clip((st - (1.0 - 0.3 * stain)) / 0.15, 0, 1)[..., None]
        out = out * (1 - 0.45 * m)
    return out


def net_pattern(face, t, rng):
    """Netting: a mesh of knotted twine, dark gaps between."""
    h, w = face.shape[:2]
    out = face * 0.55
    yy, xx = np.mgrid[0:h, 0:w]
    mesh = ((xx + yy) % (2 * t) == 0) | ((xx - yy) % (2 * t) == 0)
    out[mesh] = face[mesh] * 1.15
    knots = ((xx + yy) % (2 * t) == 0) & ((xx - yy) % (2 * t) == 0)
    out[knots] = face[knots] * 1.35
    return out


def rope_pattern(face, t, rng):
    """Twisted rope: diagonal strands."""
    h, w = face.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    band = ((xx + yy) // max(1, t)) % 3
    return face * np.choose(band, [0.8, 1.05, 1.2])[..., None]


def tyre_pattern(face, t, rng):
    """An old tyre: worn rubber, tread grooves, scuffed paler."""
    h, w = face.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    tread = np.where((xx // max(1, t)) % 3 == 0, 0.7, 1.0)
    scuff = 1.0 + 0.5 * (smooth_noise(h, w, 3.0 * t, rng) > 0.72)
    return face * (tread * scuff)[..., None]


def window_pattern(face, t, rng):
    """Old glass: dark, a pale sky reflection across the top, a diagonal glint, grime in the bottom corners."""
    h, w = face.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    out = face.copy()
    sky = np.clip(1.0 - yy / max(1, h * 0.6), 0, 1)[..., None]
    out = out * (1 - 0.35 * sky) + np.array([120.0, 136.0, 150.0]) * 0.35 * sky
    glint = (np.abs((xx - yy) - w // 3) < max(1, t // 2)) & (yy < h // 2)
    out[glint] = out[glint] * 0.75 + 40
    grime = np.clip(yy / max(1, h - 1) - 0.6, 0, 1)[..., None] * 1.6
    return out * (1 - 0.5 * grime) + np.array([70.0, 62.0, 50.0]) * 0.5 * grime


def paint_boat(bones, size, palette, seed, texels, styles):
    """The Shinsei Maru's texture: each cube's faces painted for what they are (styles: kind -> how)."""
    rng = np.random.default_rng(seed)
    n = size * texels
    img = np.zeros((n, n, 4))
    t = texels
    for bone in bones.values():
        for c in bone["cubes"]:
            u, v = c["uv"]
            sx, sy, sz = (max(1, math.ceil(q)) * t for q in c["size"])
            kind = c["kind"]
            base = np.array(palette[kind], float)
            style = styles.get(kind, {})
            for name, fx, fy, fw, fh in faces(sx, sy, sz):
                if fw == 0 or fh == 0:
                    continue
                face = np.empty((fh, fw, 3))
                face[:] = base
                # paint is never one flat colour: a slow mottle and a fine grain
                face *= (0.9 + 0.2 * smooth_noise(fh, fw, 6.0 * t, rng))[..., None]
                face *= (0.95 + 0.1 * rng.random((fh, fw)))[..., None]
                how = style.get("how", "plain")
                amount = style.get("weather", 0.0)
                if how == "painted":
                    if name == "side":
                        face = rusty_side(face, base, amount, t, rng, primer=style.get("primer", True), algae=style.get("algae", 0.0))
                    elif name == "top":
                        face = rusty_side(face, base, amount * 0.6, t, rng, primer=style.get("primer", True))
                    else:
                        face *= 0.75
                elif how == "planks":
                    along_rows = style.get("rows", True) if name == "side" else style.get("top_rows", False)
                    face = planked(face, base, t, rng, along_rows, stain=style.get("stain", 0.0) if name == "top" else 0.0)
                    if amount and name == "side":
                        face = rusty_side(face, base, amount * 0.5, t, rng, primer=False)
                        grime = style.get("grime", 0.0)
                        if grime:
                            down = (np.arange(fh) / max(1, fh - 1))[:, None]
                            dirt = (grime * (0.4 + 0.6 * down) * smooth_noise(fh, fw, 3.0 * t, rng))[..., None]
                            face = face * (1 - dirt) + np.array([60.0, 54.0, 44.0]) * dirt
                elif how == "net":
                    face = net_pattern(face, t, rng)
                elif how == "rope":
                    face = rope_pattern(face, t, rng)
                elif how == "tyre":
                    face = tyre_pattern(face, t, rng)
                elif how == "window":
                    face = window_pattern(face, t, rng) if name == "side" else face * 0.8
                y0, x0 = v * t + fy, u * t + fx
                img[y0:y0 + fh, x0:x0 + fw, :3] = np.clip(face, 0, 255)
                img[y0:y0 + fh, x0:x0 + fw, 3] = 255
    return img


def searchlight():
    """About 2 blocks across and 2.5 tall; the lamp looks along -z."""
    base = {"pivot": [0, 0, 0], "cubes": []}
    base["cubes"] += octagon(0, 1.5, 0, 30, 3, "dark", axis="y")
    for x, z in ((12, 12), (-12, 12), (12, -12), (-12, -12)):
        base["cubes"].append(box(x, 0.75, z, 4, 1.5, 4, "dark"))
    turret = {"parent": "base", "pivot": [0, 3, 0], "cubes": []}
    turret["cubes"] += octagon(0, 4, 0, 24, 2, "metal", axis="y")
    for x in (13.5, -13.5):                                   # the yoke
        turret["cubes"].append(box(x, 14, 0, 3, 20, 6, "metal"))
        turret["cubes"].append(box(x, 5.5, 0, 5, 3, 9, "metal"))
    turret["cubes"].append(box(0, 5.5, 0, 25, 2, 5, "metal"))
    # the operator's seat, on the right of the lamp
    turret["cubes"].append(box(-21, 9, 4, 8, 1.5, 8, "seat"))
    turret["cubes"].append(box(-21, 6.5, 4, 2, 4, 2, "dark"))
    turret["cubes"].append(box(-21, 13, 7.8, 8, 7, 1.2, "seat"))
    turret["cubes"].append(box(-17, 5, 4, 8, 1.5, 3, "dark"))
    lamp = {"parent": "turret", "pivot": [0, 20, 0], "cubes": []}
    lamp["cubes"] += octagon(0, 20, 1, 24, 22, "metal")       # the drum
    lamp["cubes"] += octagon(0, 20, -10.8, 25, 2, "dark")     # front rim
    lamp["cubes"] += octagon(0, 20, -12.0, 21, 0.5, "lens")   # the glass, just proud of the rim
    lamp["cubes"] += octagon(0, 20, 12.8, 18, 2, "dark")      # rear cap
    lamp["cubes"].append(box(0, 20, 14.5, 6, 6, 2, "dark"))   # the arc lamp housing
    lamp["cubes"].append(box(0, 33, 3, 6, 4, 8, "dark"))      # vent on top
    lamp["cubes"].append(box(0, 36, 3, 8, 1, 10, "metal"))
    for x in (12.5, -12.5):
        lamp["cubes"].append(box(x, 20, 0, 1.5, 4, 4, "dark"))   # trunnions
    lamp["cubes"].append(box(-8, 14, 12, 1.5, 1.5, 8, "dark"))    # handle
    bones = {"base": base, "turret": turret, "lamp": lamp}
    palette = {"dark": (46, 50, 46), "metal": (96, 104, 92), "seat": (84, 62, 40), "lens": (236, 232, 206)}
    write("searchlight", bones, 256, palette, glow_kinds={"lens": (255, 250, 220)}, seed=3)


def nuclear_bomb():
    """About 3 blocks long, built nose down (the nose at the bottom, the tail fins at the top)."""
    body = {"pivot": [0, 0, 0], "cubes": []}
    body["cubes"] += octagon(0, 22, 0, 14, 30, "drab", axis="y")         # the casing
    body["cubes"] += octagon(0, 5.5, 0, 12, 3, "drab", axis="y")         # nose, tapering
    body["cubes"] += octagon(0, 3, 0, 9, 2, "drab", axis="y")
    body["cubes"] += octagon(0, 1.2, 0, 5, 1.6, "dark", axis="y")
    body["cubes"] += octagon(0, 16, 0, 14.6, 2, "band", axis="y")        # yellow band
    body["cubes"] += octagon(0, 38, 0, 10, 4, "drab", axis="y")          # tapering to the tail
    tail = {"parent": "body", "pivot": [0, 40, 0], "cubes": []}
    tail["cubes"].append(box(0, 46, 0, 16, 10, 16, "dark"))              # box tail
    tail["cubes"].append(box(0, 46, 0, 14, 10.2, 14, "void"))            # hollow look
    for rot in (0, 90):
        tail["cubes"].append(box(0, 44, 0, 20, 12, 1, "drab", rot=(0, rot + 45, 0), pivot=(0, 44, 0)))
    bones = {"body": body, "tail": tail}
    palette = {"drab": (82, 88, 58), "dark": (44, 46, 38), "band": (196, 168, 60), "void": (16, 16, 14)}
    write("nuclear_bomb", bones, 256, palette, seed=5)


# ---------------------------------------------------------------- the Shinsei Maru
# Designed in blocks at full size (x to starboard, y up, z aft: the bow is at -z), built at half size (8 model pixels
# to a block; the renderer draws it twice as big). ShinseiMaruEntity uses the same numbers for its seats, gun, stern,
# ladders and decks.
HALF = 8.0
BEAM = 2.8                       # half her beam
BOW, TRANSOM = -8.6, 7.7         # her ends (16.3 blocks long)
GUN_PIVOT = (0.0, 3.35, 6.25)    # where the machine gun turns
DRIVER_SEAT = (0.0, 4.45, 0.12)  # on the wheelhouse roof, behind the wheel
GUNNER_SEAT = (0.0, 2.35, 5.27)  # on the stern platform, behind the gun, facing aft
LADDER_Z = 3.6                   # the boarding ladders, port and starboard, just aft of the wheelhouse


def hb(x0, y0, z0, x1, y1, z1, kind, rot=None, pivot=None):
    """A box between two corners given in (full-size) blocks."""
    c = [(x0 + x1) / 2 * HALF, (y0 + y1) / 2 * HALF, (z0 + z1) / 2 * HALF]
    size = [abs(x1 - x0) * HALF, abs(y1 - y0) * HALF, abs(z1 - z0) * HALF]
    return box(*c, *size, kind, rot=rot, pivot=[q * HALF for q in pivot] if pivot else None)


def line(a, b, thick, kind):
    """A rope or wire from a to b (full-size blocks) in a fore-and-aft plane (x the same at both ends)."""
    (x, y0, z0), (_, y1, z1) = a, b
    length = math.hypot(y1 - y0, z1 - z0)
    angle = math.degrees(math.atan2(z1 - z0, y1 - y0))      # from straight up, towards +z
    cy, cz = (y0 + y1) / 2, (z0 + z1) / 2
    return hb(x - thick / 2, cy - length / 2, cz - thick / 2, x + thick / 2, cy + length / 2, cz + thick / 2, kind,
              rot=(-angle, 0, 0), pivot=(x, cy, cz))


def athwart(a, b, thick, kind):
    """A rope or wire from a to b (full-size blocks) across her (z the same at both ends): a shroud."""
    (x0, y0, z), (x1, y1, _) = a, b
    length = math.hypot(x1 - x0, y1 - y0)
    angle = math.degrees(math.atan2(x1 - x0, y1 - y0))      # from straight up, towards +x
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return hb(cx - thick / 2, cy - length / 2, z - thick / 2, cx + thick / 2, cy + length / 2, z + thick / 2, kind,
              rot=(0, 0, angle), pivot=(cx, cy, z))


def shinsei_maru():
    """16.3 blocks long, 5.6 across; the wheelhouse roof is 4.45 blocks above the keel, the mast 11. Run down and
    weather-beaten: rust weeping from every fitting, paint flaking off her topsides and wheelhouse, a rust-red
    waterline, old tyres for fenders, patched planking, nets and floats heaped on the foredeck."""
    rng = np.random.default_rng(23)
    boat = {"pivot": [0, 0, 0], "cubes": []}
    c = boat["cubes"]
    B = BEAM
    # the hull: a rounded bottom in steps, the bow narrowing and rising, a flat transom
    c += [hb(-1.6, 0.0, -6.6, 1.6, 0.6, 6.6, "bottom"), hb(-2.3, 0.5, -7.2, 2.3, 1.2, 7.3, "bottom"),
          hb(-B, 1.1, -6.2, B, 2.1, 7.3, "hull"), hb(-2.3, 0.7, -7.4, 2.3, 2.35, -6.2, "hull"),
          hb(-1.6, 1.0, -8.1, 1.6, 2.5, -7.4, "hull"), hb(-0.8, 1.4, -8.6, 0.8, 2.7, -8.1, "hull"),
          hb(-2.6, 1.0, 7.3, 2.6, 2.2, 7.7, "hull")]
    for side in (-1, 1):
        c.append(hb(side * B, 1.02, -6.2, side * (B + 0.02), 1.22, 7.3, "boottop"))                # the waterline
        c.append(hb(side * B, 1.92, -6.2, side * (B + 0.09), 2.06, 7.3, "rail"))                   # rubbing strake
        c.append(hb(side * 2.3, 2.1, -7.4, side * 2.38, 2.3, -6.2, "rail"))
        # rust weeping down her topsides from the scuppers and fittings, and paint flaking off
        for z in np.linspace(-5.8, 6.9, 24) + rng.uniform(-0.25, 0.25, 24):
            w, l = rng.uniform(0.08, 0.2), rng.uniform(0.35, 0.95)
            c.append(hb(side * B, 2.08 - l, z - w / 2, side * (B + 0.025), 2.08, z + w / 2, "streak"))
        for _ in range(14):
            z, y = rng.uniform(-5.8, 6.8), rng.uniform(1.3, 1.85)
            c.append(hb(side * B, y - rng.uniform(0.12, 0.25), z - rng.uniform(0.25, 0.7),
                        side * (B + 0.015), y, z + rng.uniform(0.25, 0.7), "flake"))
        for z in (-4.6, -0.5, 5.2):                                                                   # patched planks
            c.append(hb(side * B, 1.35, z - 0.45, side * (B + 0.03), 1.65, z + 0.45, "patch"))
        # old tyres hung over the side as fenders
        for z in (-4.0, -1.7, 0.6, 2.4, 5.6):
            c += octagon(side * (B + 0.16) * HALF, 1.55 * HALF, z * HALF, 0.62 * HALF, 0.22 * HALF, "tyre", axis="x")
            c.append(hb(side * (B + 0.12), 1.85, z - 0.03, side * (B + 0.18), 2.6, z + 0.03, "rope"))
        # the hawse pipe, weeping rust
        c.append(hb(side * 1.0 - 0.15, 2.05, -8.0, side * 1.0 + 0.15, 2.35, -7.7, "metal"))
        c.append(hb(side * 1.0 - 0.06, 1.35, -7.98, side * 1.0 + 0.06, 2.05, -7.9, "streak"))
    # deck and bulwarks (open just aft of the wheelhouse, where the ladders come up)
    c.append(hb(-2.65, 2.1, -7.4, 2.65, 2.2, 7.3, "deck"))
    for _ in range(26):                                                                              # stains
        x, z = rng.uniform(-2.2, 2.2), rng.uniform(-7.0, 6.8)
        r = rng.uniform(0.2, 0.55)
        c.append(hb(x - r, 2.2, z - r, x + r, 2.205, z + r, "stain"))
    for side in (-1, 1):
        c.append(hb(side * 2.66, 2.2, -6.2, side * 2.78, 2.65, LADDER_Z - 0.55, "hull"))
        c.append(hb(side * 2.62, 2.65, -6.2, side * 2.82, 2.72, LADDER_Z - 0.55, "rail"))           # the cap rail
        c.append(hb(side * 2.0, 2.3, -7.4, side * 2.3, 2.85, -6.2, "hull"))
        for z in np.arange(-5.6, LADDER_Z - 0.6, 1.3):                                               # scuppers
            c.append(hb(side * 2.78, 2.2, z, side * 2.785, 2.3, z + 0.3, "dark"))
        # the boarding ladder: rails and rungs down the hull side into the water
        lx = side * (B + 0.1)
        for dz in (-0.3, 0.3):
            c.append(hb(lx - 0.04, 0.5, LADDER_Z + dz - 0.04, lx + 0.04, 2.95, LADDER_Z + dz + 0.04, "metal"))
        for y in np.arange(0.7, 2.8, 0.35):
            c.append(hb(lx - 0.03, y, LADDER_Z - 0.3, lx + 0.03, y + 0.06, LADDER_Z + 0.3, "metal"))
        c.append(hb(side * 2.62, 2.2, LADDER_Z + 0.55, side * 2.72, 3.1, LADDER_Z + 0.62, "rail"))   # stanchion aft of it
    c.append(hb(-1.6, 2.5, -8.1, 1.6, 2.95, -7.4, "hull"))
    # the wheelhouse: weathered planking, its windows (one boarded up, one smashed), grime under them, the roof
    c.append(hb(-1.9, 2.2, -1.47, 1.9, 4.3, 2.94, "cabin"))
    for x in (-1.1, 0.0, 1.1):
        c.append(hb(x - 0.36, 3.3, -1.53, x + 0.36, 3.95, -1.47, "window" if x != 1.1 else "broken"))
        c.append(hb(x - 0.4, 3.25, -1.55, x + 0.4, 3.3, -1.47, "rail"))                               # sills
        c.append(hb(x - 0.25, 2.6, -1.49, x + 0.25, 3.25, -1.47, "grime"))
    for side in (-1, 1):
        for z in (0.1, 1.7):
            c.append(hb(side * 1.9, 3.3, z - 0.4, side * 1.96, 3.95, z + 0.4, "window"))
            c.append(hb(side * 1.9, 2.7, z - 0.18, side * 1.93, 3.3, z + 0.18, "grime"))
        c.append(hb(side * 1.9, 2.4, -0.9, side * 1.92, 2.9, -0.3, "flake"))
    for dx in (-0.25, 0.25):                                                                          # ladder to the roof
        c.append(hb(-1.2 + dx - 0.04, 2.2, 2.98, -1.2 + dx + 0.04, 4.9, 3.06, "metal"))
    for y in np.arange(2.5, 4.5, 0.35):
        c.append(hb(-1.45, y, 3.0, -0.95, y + 0.06, 3.06, "metal"))
    c.append(hb(-0.4, 2.2, 2.94, 0.4, 3.9, 3.0, "wood"))                                              # the door
    c.append(hb(0.25, 2.95, 3.0, 0.32, 3.05, 3.05, "metal"))
    for dx, dy, sx, sy in ((0, 0.42, 0.9, 0.16), (0, -0.42, 0.9, 0.16), (0.42, 0, 0.16, 0.9), (-0.42, 0, 0.16, 0.9)):
        c.append(hb(1.2 + dx - sx / 2, 3.3 + dy - sy / 2, 2.94, 1.2 + dx + sx / 2, 3.3 + dy + sy / 2, 3.04, "buoy"))  # life ring
    c.append(hb(-2.05, 4.3, -1.65, 2.05, 4.45, 3.1, "roof"))
    c.append(hb(-2.05, 4.1, -1.66, 2.05, 4.3, -1.6, "streak"))
    for side in (-1, 1):                                                                              # rust off the roof
        for z in rng.uniform(-1.3, 2.7, 5):
            l = rng.uniform(0.3, 0.9)
            c.append(hb(side * 1.9, 4.3 - l, z - 0.05, side * 1.915, 4.3, z + 0.05, "streak"))
    # the flying bridge: rails round the roof, the wheel on its stand, a binnacle, the horn
    for side in (-1, 1):
        c.append(hb(side * 1.97, 4.45, -1.6, side * 2.03, 5.2, 3.05, "rail"))
        c.append(hb(side * 1.97, 5.15, -1.6, side * 2.03, 5.25, 3.05, "rail"))
    c.append(hb(-2.03, 4.45, -1.66, 2.03, 5.25, -1.6, "rail"))
    c.append(hb(-0.12, 4.45, -0.95, 0.12, 5.05, -0.75, "wood"))
    c += octagon(0.0, 5.2 * HALF, -1.04 * HALF, 0.95 * HALF, 0.06 * HALF, "wood")                 # the wheel
    c.append(hb(-0.85, 4.45, -1.1, -0.55, 4.95, -0.8, "brass"))
    c.append(hb(0.6, 4.45, -1.3, 0.75, 4.75, -1.15, "metal"))
    c.append(hb(0.55, 4.75, -1.4, 0.8, 4.9, -1.05, "brass"))
    # the funnel, sooty and rusted through at the top
    c.append(hb(1.15, 4.45, 2.3, 1.65, 6.3, 2.8, "metal"))
    c.append(hb(1.1, 6.3, 2.25, 1.7, 6.45, 2.85, "rust"))
    c.append(hb(1.12, 5.6, 2.28, 1.68, 6.3, 2.82, "soot"))
    # the mast, its yard and lamp, stays to the bow and the funnel, the aerial
    mz = -3.0
    c.append(hb(-0.12, 2.2, mz - 0.12, 0.12, 11.0, mz + 0.12, "wood"))
    c.append(hb(-1.4, 9.2, mz - 0.06, 1.4, 9.32, mz + 0.06, "wood"))
    c.append(hb(-0.2, 11.0, mz - 0.2, 0.2, 11.3, mz + 0.2, "lamp"))
    c.append(hb(-0.32, 11.3, mz - 0.32, 0.32, 11.36, mz + 0.32, "metal"))
    c.append(line((0.0, 10.8, mz), (0.0, 2.95, -8.3), 0.05, "rope"))
    c.append(line((0.0, 10.6, mz), (0.0, 6.4, 2.55), 0.04, "rope"))
    for side in (-1, 1):                                                                              # the shrouds
        c.append(athwart((side * 0.1, 10.4, mz), (side * 2.66, 2.72, mz), 0.04, "rope"))
        c.append(athwart((side * 1.35, 9.2, mz), (side * 2.66, 2.72, mz), 0.035, "rope"))
        c.append(hb(side * 2.6, 2.6, mz - 0.08, side * 2.72, 2.78, mz + 0.08, "metal"))               # chainplates
    # a net drying from the port yardarm, a float or two caught in it
    c.append(hb(-1.3, 7.3, mz - 0.03, -0.75, 9.15, mz + 0.03, "net"))
    c.append(hb(-1.2, 7.25, mz - 0.1, -1.0, 7.45, mz + 0.1, "buoy"))
    # a net hung over the starboard bulwark to dry, floats along its head rope
    c.append(hb(2.78, 1.35, -6.0, 2.84, 2.66, -4.5, "net"))
    c.append(hb(2.66, 2.6, -6.05, 2.86, 2.7, -4.45, "rope"))
    for z in (-5.8, -5.25, -4.7):
        c.append(hb(2.84, 2.35, z - 0.12, 2.98, 2.55, z + 0.12, "buoy"))
    # the foredeck: cages, nets heaped up, floats, a coil of rope, the anchor, bollards, the winch, a ventilator
    for x, y, z in ((-1.2, 2.2, -5.1), (1.0, 2.2, -4.9), (-0.1, 3.0, -5.0), (1.1, 2.2, -3.8), (-1.0, 2.2, -3.9)):
        c.append(hb(x - 0.45, y, z - 0.45, x + 0.45, y + 0.8, z + 0.45, "crate"))
    for x, z, sx, sy, sz in ((1.6, -6.4, 1.0, 0.5, 1.3), (1.9, -5.7, 0.7, 0.35, 0.9), (1.3, -6.9, 0.8, 0.3, 0.6)):
        c.append(hb(x - sx / 2, 2.2, z - sz / 2, x + sx / 2, 2.2 + sy, z + sz / 2, "net"))
    for x, z in ((-1.7, -6.3), (-2.0, -5.6), (1.9, -2.6)):
        c += octagon(x * HALF, 2.45 * HALF, z * HALF, 0.5 * HALF, 0.5 * HALF, "buoy", axis="y")
    c += octagon(-0.3 * HALF, 2.32 * HALF, -6.9 * HALF, 0.8 * HALF, 0.24 * HALF, "rope", axis="y")
    c.append(hb(0.5, 2.2, -7.9, 0.62, 2.3, -6.9, "metal"))                                            # anchor: shank
    c.append(hb(0.2, 2.2, -7.95, 0.92, 2.3, -7.83, "metal"))                                          # ...and flukes
    c.append(hb(0.5, 2.2, -7.0, 0.62, 2.45, -6.88, "rust"))
    for x, z in ((-1.4, -7.0), (1.4, -7.0), (-2.1, 6.4), (2.1, 6.4)):
        c.append(hb(x - 0.12, 2.2, z - 0.12, x + 0.12, 2.6, z + 0.12, "metal"))
        c.append(hb(x - 0.18, 2.55, z - 0.18, x + 0.18, 2.62, z + 0.18, "rust"))
    c.append(hb(-0.9, 2.2, -2.5, 0.9, 2.8, -1.9, "metal"))                                            # the winch
    c += octagon(0.0, 2.6 * HALF, -2.2 * HALF, 0.5 * HALF, 2.0 * HALF, "rust", axis="x")
    c.append(hb(-1.6, 2.2, -1.9, -1.35, 3.0, -1.65, "metal"))                                         # ventilator
    c.append(hb(-1.65, 3.0, -2.0, -1.3, 3.3, -1.65, "rust"))
    # the stern platform and its rails
    c.append(hb(-2.5, 2.2, 4.2, 2.5, 2.35, 7.5, "deck"))
    for side in (-1, 1):
        c += [hb(side * 2.45, 2.35, z - 0.05, side * 2.53, 3.1, z + 0.05, "rail") for z in (4.3, 5.9, 7.45)]
        c.append(hb(side * 2.45, 3.0, 4.25, side * 2.53, 3.1, 7.5, "rail"))
    c.append(hb(-2.53, 3.0, 7.45, 2.53, 3.1, 7.55, "rail"))
    # the davit: a bent pipe over the port quarter, with a block hanging from it
    c.append(hb(-2.3, 2.35, 6.65, -2.14, 4.9, 6.81, "metal"))
    c.append(hb(-2.3, 4.8, 6.65, -2.14, 4.96, 7.8, "metal"))
    c.append(hb(-2.3, 4.3, 7.7, -2.14, 4.96, 7.86, "metal"))
    c.append(hb(-2.26, 3.5, 7.73, -2.18, 4.3, 7.81, "rope"))
    c.append(hb(-2.32, 3.3, 7.67, -2.12, 3.5, 7.87, "metal"))
    # the machine gun's pedestal and the gunner's seat; oil drums by the rack
    gx, gy, gz = GUN_PIVOT
    c.append(hb(gx - 0.15, 2.35, gz - 0.15, gx + 0.15, gy - 0.05, gz + 0.15, "metal"))
    c.append(hb(-0.35, 2.35, 4.98, 0.35, 2.55, 5.5, "wood"))
    for x in (-2.0, 2.0):
        c += octagon(x * HALF, 2.65 * HALF, 4.65 * HALF, 0.55 * HALF, 0.85 * HALF, "drum", axis="y")
    bones = {"boat": boat}
    # three recovered naval mines in a rack aft of the wheelhouse (shown as many as are aboard, up to three)
    for i, x in enumerate((-1.05, 0.0, 1.05)):
        mz = 3.7
        mine = {"parent": "boat", "pivot": [x * HALF, 2.65 * HALF, mz * HALF], "cubes": []}
        mine["cubes"] += octagon(x * HALF, 2.65 * HALF, mz * HALF, 0.85 * HALF, 0.85 * HALF, "mine")
        for dx, dy, dz, sx, sy, sz in ((0, 0.5, 0, 0.1, 0.2, 0.1), (0.46, 0, 0, 0.2, 0.1, 0.1), (-0.46, 0, 0, 0.2, 0.1, 0.1),
                                       (0, 0, 0.46, 0.1, 0.1, 0.2), (0, 0, -0.46, 0.1, 0.1, 0.2)):
            mine["cubes"].append(box((x + dx) * HALF, (2.65 + dy) * HALF, (mz + dz) * HALF, sx * HALF, sy * HALF, sz * HALF, "rust"))
        bones["mine%d" % (i + 1)] = mine
    # the Type 93 13 mm machine gun: turns on its pedestal, the barrel tilts; it points aft
    gun = {"parent": "boat", "pivot": [gx * HALF, gy * HALF, gz * HALF], "cubes": []}
    gun["cubes"].append(hb(gx - 0.22, gy - 0.1, gz - 0.2, gx + 0.22, gy + 0.05, gz + 0.2, "metal"))    # the cradle
    barrel = {"parent": "gun", "pivot": [gx * HALF, gy * HALF, gz * HALF], "cubes": []}
    b = barrel["cubes"]
    b.append(hb(gx - 0.17, gy + 0.02, gz - 0.55, gx + 0.17, gy + 0.36, gz + 0.45, "gun"))              # receiver
    b += octagon(gx * HALF, (gy + 0.19) * HALF, (gz + 0.95) * HALF, 0.28 * HALF, 1.0 * HALF, "gun")    # cooling jacket
    b += octagon(gx * HALF, (gy + 0.19) * HALF, (gz + 1.75) * HALF, 0.13 * HALF, 0.6 * HALF, "gun")    # barrel
    b.append(hb(gx - 0.1, gy + 0.09, gz + 2.0, gx + 0.1, gy + 0.29, gz + 2.1, "gun"))                  # muzzle
    b.append(hb(gx - 0.2, gy + 0.36, gz - 0.25, gx + 0.2, gy + 0.62, gz + 0.1, "drab"))                # magazine
    b.append(hb(gx - 0.05, gy - 0.12, gz - 0.85, gx + 0.05, gy + 0.2, gz - 0.55, "metal"))             # spade grips
    b.append(hb(gx - 0.22, gy - 0.12, gz - 0.9, gx + 0.22, gy - 0.02, gz - 0.8, "metal"))
    bones["gun"] = gun
    bones["gun_barrel"] = barrel
    palette = {"bottom": (96, 44, 34), "hull": (52, 46, 42), "boottop": (122, 52, 34), "rail": (88, 66, 48),
               "deck": (104, 88, 66), "stain": (70, 58, 44), "cabin": (112, 108, 96), "window": (26, 34, 40),
               "broken": (12, 14, 16), "grime": (74, 66, 54), "roof": (66, 62, 58), "wood": (88, 66, 44),
               "crate": (128, 102, 70), "rope": (146, 128, 92), "metal": (60, 60, 58), "rust": (116, 62, 34),
               "streak": (110, 56, 30), "flake": (128, 66, 46), "patch": (72, 56, 40), "tyre": (26, 26, 26),
               "net": (66, 74, 52), "buoy": (196, 92, 48), "brass": (150, 122, 62), "soot": (24, 22, 20),
               "dark": (16, 14, 12), "drum": (74, 70, 46), "lamp": (214, 204, 168), "mine": (46, 48, 46),
               "gun": (40, 42, 40), "drab": (78, 84, 56)}
    # painted face by face (paint_boat), two texels to each of the model's pixels (a 2048 sheet for its 1024 UV space)
    styles = {
        "hull": {"how": "painted", "weather": 1.0}, "bottom": {"how": "painted", "weather": 0.9, "algae": 0.9},
        "boottop": {"how": "painted", "weather": 0.7, "algae": 0.5}, "roof": {"how": "painted", "weather": 1.0, "primer": False},
        "rail": {"how": "painted", "weather": 0.75}, "metal": {"how": "painted", "weather": 1.0, "primer": False},
        "drum": {"how": "painted", "weather": 1.0}, "gun": {"how": "painted", "weather": 0.3, "primer": False},
        "mine": {"how": "painted", "weather": 0.8, "primer": False}, "buoy": {"how": "painted", "weather": 0.5, "primer": False},
        "drab": {"how": "painted", "weather": 0.4, "primer": False}, "brass": {"how": "painted", "weather": 0.3, "primer": False},
        "cabin": {"how": "planks", "rows": False, "weather": 1.0, "grime": 0.35}, "deck": {"how": "planks", "rows": True, "top_rows": False, "stain": 0.8},
        "wood": {"how": "planks", "rows": False, "weather": 0.3}, "crate": {"how": "planks", "rows": True, "top_rows": True, "weather": 0.3},
        "patch": {"how": "planks", "rows": True},
        "rope": {"how": "rope"}, "net": {"how": "net"}, "tyre": {"how": "tyre"}, "window": {"how": "window"}, "broken": {"how": "window"},
    }
    write("shinsei_maru", bones, 1024, palette, seed=7, texels=2, bounds=20, styles=styles)


def main():
    searchlight()
    nuclear_bomb()
    shinsei_maru()


if __name__ == "__main__":
    main()
