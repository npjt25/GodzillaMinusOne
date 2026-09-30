"""Builds the Odo Island Monster from Monster122's Godzillasaurus (the "G-1" Bedrock add-on's support pack):
its model, skins and animations, unchanged, adapted to how the mod drives them.

    python3 tools/make_odo_model.py <the add-on's resource pack folder, G-1(RE)>

  * geometry: "Godzillasaurus by Monster122.geo.json" as it is (87 bones, 395 cubes), renamed,
  * textures: godzillasaurus.png as the skin, its olive brown turned grey and brown (gray_brown below);
    godzillasaurus_burned.png, split into the charred skin and its glowing embers (the add-on's "spider"
    material makes its semi-transparent pixels glow),
  * animations: the add-on's Bedrock controller plays several animations at once and adds them up (idle is
    always playing underneath; "target" opens the jaw while it hunts). GeckoLib lets one animation drive a bone
    at a time, so those sums are baked into single clips here:
      animation.odo.idle        idle
      animation.odo.idle_angry  idle + target
      animation.odo.walk        move + idle
      animation.odo.run         move, played 1.6x as fast, + target (jaw held open)
      animation.odo.bite        attack        (both as in the add-on: the jaws snap shut at 0.75 s)
      animation.odo.bite2       attack2
      animation.odo.stomp       stomp         (the foot comes down at 1.08 s)
      animation.odo.roar        roaring
      animation.odo.burnt       a 1.5 s collapse into the add-on's burnt pose, held
    Their sound keys are renamed to the mod's Odo sounds.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
NAME = "odo_island_monster"
FPS = 20                  # keyframes per second of the baked clips
RUN_SPEED = 1.6           # the run is the walk cycle played this much faster
BURNT_FALL = 1.5          # seconds to collapse into the burnt pose

SOUNDS = {"step": "odo.step", "attack": "odo.bite", "stomp": "odo.stomp", "roaring": "odo.roar",
          "growl": "odo.ambient", "smash": "odo.stomp"}


# ---------------------------------------------------------------- Bedrock keyframes
def _vec(v):
    if isinstance(v, (int, float)):
        return [float(v)] * 3
    if isinstance(v, str):
        raise ValueError("Molang in a baked channel: " + v)
    return [float(q) for q in v]


def _keys(channel):
    """[(time, pre, post, catmullrom)] of a channel (a constant becomes one key)."""
    if not isinstance(channel, dict):
        v = _vec(channel)
        return [(0.0, v, v, False)]
    out = []
    for t, k in sorted(channel.items(), key=lambda kv: float(kv[0])):
        if isinstance(k, dict):
            post = _vec(k.get("post", k.get("pre")))
            pre = _vec(k.get("pre", k.get("post")))
            cat = k.get("lerp_mode") == "catmullrom"
        else:
            pre = post = _vec(k)
            cat = False
        out.append((float(t), pre, post, cat))
    return out


def sample(channel, t):
    """A channel's value at t: linear or (Bedrock's) Catmull-Rom between keys, held past the ends."""
    ks = _keys(channel)
    if t <= ks[0][0]:
        return list(ks[0][1])
    if t >= ks[-1][0]:
        return list(ks[-1][2])
    for i in range(len(ks) - 1):
        (t1, _, p1, c1), (t2, q2, _, c2) = ks[i], ks[i + 1]
        if t1 <= t <= t2:
            f = (t - t1) / max(t2 - t1, 1e-9)
            a, b = np.array(p1), np.array(q2)
            if c1 or c2:
                p0 = np.array(ks[i - 1][2]) if i > 0 else a
                p3 = np.array(ks[i + 2][1]) if i + 2 < len(ks) else b
                f2, f3 = f * f, f * f * f
                v = 0.5 * (2 * a + (-p0 + b) * f + (2 * p0 - 5 * a + 4 * b - p3) * f2 + (-p0 + 3 * a - 3 * b + p3) * f3)
            else:
                v = a + (b - a) * f
            return [float(x) for x in v]
    return list(ks[-1][2])


def bake(layers, length, sounds=None):
    """Adds up animations (rotation and position add, scale multiplies) into one clip of the given length.
    layers: [(animation, speed)], each looping over its own length."""
    bones = sorted({b for anim, _ in layers for b in anim.get("bones", {})})
    frames = int(round(length * FPS))
    out = {}
    for bone in bones:
        chans = {}
        for c in ("rotation", "position", "scale"):
            present = [(a, s) for a, s in layers if c in a.get("bones", {}).get(bone, {})]
            if not present:
                continue
            keys = {}
            for i in range(frames + 1):
                t = i / FPS
                v = np.ones(3) if c == "scale" else np.zeros(3)
                for anim, speed in present:
                    period = float(anim.get("animation_length") or 0.0)
                    at = (t * speed) % period if period > 0 else 0.0
                    x = np.array(sample(anim["bones"][bone][c], at))
                    v = v * x if c == "scale" else v + x
                keys["%.4f" % t] = [round(float(q), 4) for q in v]
            chans[c] = keys
        out[bone] = chans
    clip = {"loop": True, "animation_length": length, "bones": out}
    if sounds:
        clip["sound_effects"] = sounds
    return clip


def sounds_of(anim, speed=1.0, repeat_every=None, length=None):
    res = {}
    for t, e in anim.get("sound_effects", {}).items():
        entries = e if isinstance(e, list) else [e]
        for entry in entries:
            name = SOUNDS.get(entry.get("effect"))
            if not name:
                continue
            times = [float(t) / speed]
            if repeat_every:
                times = [x + k * repeat_every for x in times for k in range(int(round(length / repeat_every)))]
            for x in times:
                while "%.4f" % x in res:
                    x += 0.001
                res["%.4f" % x] = {"effect": name}
    return dict(sorted(res.items(), key=lambda kv: float(kv[0])))


def as_is(anim, loop=False, particles=None):
    """An add-on animation for GeckoLib: its sound keys renamed, Molang's Math lower-cased, played once."""
    def clean(v):
        if isinstance(v, str):
            return v.replace("Math.", "math.")
        if isinstance(v, list):
            return [clean(x) for x in v]
        if isinstance(v, dict):
            return {k: clean(x) for k, x in v.items()}
        return v
    clip = {"loop": loop, "animation_length": float(anim["animation_length"]),
            "bones": {b: {c: clean(v) for c, v in ch.items() if c in ("rotation", "position", "scale")}
                      for b, ch in anim["bones"].items()}}
    s = sounds_of(anim)
    if s:
        clip["sound_effects"] = s
    if particles:
        clip["particle_effects"] = particles
    return clip


def fall_into(pose, length):
    """From the rest pose into a held pose over length seconds (easing in, like a collapse)."""
    out = {}
    for bone, chans in pose["bones"].items():
        out[bone] = {}
        for c, v in chans.items():
            target = _vec(v)
            rest = [1.0, 1.0, 1.0] if c == "scale" else [0.0, 0.0, 0.0]
            mid = [r + (x - r) * 0.35 for r, x in zip(rest, target)]
            out[bone][c] = {"0.0000": rest, "%.4f" % (length * 0.4): {"post": mid, "lerp_mode": "catmullrom"},
                            "%.4f" % length: target}
    return {"loop": False, "animation_length": length, "bones": out,
            "sound_effects": {"0.0000": {"effect": "odo.death"}}}


def build_animations(src):
    a = json.load(open(src))["animations"]
    g = lambda n: a["animation.godzillasaurus." + n]  # noqa: E731
    move, idle, target = g("move"), g("idle"), g("target")
    walk_len = 4.0                      # two strides of move over one breath of idle
    run_len = round(float(move["animation_length"]) / RUN_SPEED, 4)
    jaw_open = {"bones": {"bone5": {"rotation": [16.25, 0, 0]}}, "animation_length": 0}   # target's mean
    return {
        "animation.odo.idle": bake([(idle, 1.0)], 4.0),
        "animation.odo.idle_angry": bake([(idle, 1.0), (target, 1.0)], 4.0),
        "animation.odo.walk": bake([(move, 1.0), (idle, 1.0)], walk_len,
                                   sounds_of(move, 1.0, float(move["animation_length"]), walk_len)),
        "animation.odo.run": bake([(move, RUN_SPEED), (jaw_open, 1.0)], run_len, sounds_of(move, RUN_SPEED)),
        "animation.odo.bite": as_is(g("attack")),
        "animation.odo.bite2": as_is(g("attack2")),
        "animation.odo.stomp": as_is(g("stomp"), particles={"1.0833": {"effect": "odo_stomp", "locator": "foot_left"}}),
        "animation.odo.roar": as_is(g("roaring")),
        "animation.odo.burnt": fall_into(g("burnt"), BURNT_FALL),
    }


# ---------------------------------------------------------------- textures
def gray_brown(rgba):
    """Monster122's olive-brown hide made grey and brown: the lighter scales go an ashen, stony grey, the darker
    grooves between them a warmer brown. The red mouth, the eyes and anything else that isn't olive/brown stay."""
    rgb = rgba[..., :3].astype(float) / 255.0
    mx, mn = rgb.max(-1), rgb.min(-1)
    val, sat = mx, np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0.0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    d = np.maximum(mx - mn, 1e-6)
    hue = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) * 60.0
    hide = (rgba[..., 3] > 0) & (hue >= 15) & (hue <= 75) & (sat < 0.65)
    # how light the pixel is among the hide's pixels: 0 in the grooves, 1 on the lightest scales
    lo, hi = np.percentile(val[hide], 10), np.percentile(val[hide], 90)
    light = np.clip((val - lo) / max(hi - lo, 1e-6), 0.0, 1.0)
    grey = light * 0.8                                   # share of grey: most on the light scales
    new_hue = 28.0                                       # brown, a little warmer than the olive
    new_sat = sat * (1.0 - grey) * 0.95
    new_val = val * (1.0 + 0.1 * light)                  # the grey scales a touch paler
    c = new_val * new_sat
    x = c * (1 - abs((new_hue / 60.0) % 2 - 1))
    m = new_val - c
    out = np.stack([c + m, x + m, m + np.zeros_like(c)], -1)   # hue 28 lies between red and yellow: (c, x, 0)
    res = rgba.copy()
    res[..., :3] = np.where(hide[..., None], np.clip(out * 255.0 + 0.5, 0, 255), rgba[..., :3]).astype(np.uint8)
    return res


def split_emissive(rgba):
    """Bedrock's alpha-emissive materials: alpha 0 is cut out, other pixels are drawn opaque and glow by
    (1 - alpha). Returns (opaque skin, glow)."""
    alpha = rgba[..., 3].astype(float) / 255.0
    base = rgba.copy()
    base[..., 3] = np.where(rgba[..., 3] > 0, 255, 0)
    strength = np.where(rgba[..., 3] > 0, 1.0 - alpha, 0.0)
    glow = np.zeros_like(rgba)
    glow[..., :3] = (rgba[..., :3].astype(float) * strength[..., None]).astype(np.uint8)
    glow[..., 3] = np.where(strength > 0.02, 255, 0)
    return base, glow


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    rp = sys.argv[1]
    geo = json.load(open(os.path.join(rp, "models", "entity", "Godzillasaurus by Monster122.geo.json")))
    geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + NAME
    with open(os.path.join(ASSETS, "geo", "entity", NAME + ".geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
        f.write("\n")

    tex = os.path.join(rp, "textures", "entity", "godzilla_minus_one")
    out = os.path.join(ASSETS, "textures", "entity")
    skin = np.array(Image.open(os.path.join(tex, "godzillasaurus.png")).convert("RGBA"))
    base, _ = split_emissive(gray_brown(skin))
    Image.fromarray(base).save(os.path.join(out, NAME + ".png"))
    burnt = np.array(Image.open(os.path.join(tex, "godzillasaurus_burned.png")).convert("RGBA"))
    base, glow = split_emissive(burnt)
    Image.fromarray(base).save(os.path.join(out, NAME + "_burnt.png"))
    Image.fromarray(glow).save(os.path.join(out, NAME + "_burnt_glow.png"))
    stale = os.path.join(out, NAME + "_glow.png")      # (its eyes don't glow)
    if os.path.exists(stale):
        os.remove(stale)

    anims = build_animations(os.path.join(rp, "animations", "godzillasaurus.animation.json"))
    with open(os.path.join(ASSETS, "animations", "entity", NAME + ".animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=1)
        f.write("\n")
    bones = geo["minecraft:geometry"][0]["bones"]
    print("wrote the Odo: %d bones, %d cubes, %d animations" % (len(bones), sum(len(b.get("cubes", [])) for b in bones), len(anims)))


if __name__ == "__main__":
    main()
