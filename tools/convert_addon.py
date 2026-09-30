"""Converts the Bedrock "Godzilla Minus One" add-on (by GojiDraw) into assets for this Forge mod.

Usage:
    python3 tools/convert_addon.py <path to extracted add-on folder>

The folder must contain the add-on's resource pack (the directory with `models/`,
`textures/`, `animations/` ...). Requirements: Pillow, numpy and imageio-ffmpeg
(pip install pillow numpy imageio-ffmpeg).

What it does:
  * copies the Bedrock geometry files (GeckoLib reads them as-is),
  * cleans the Bedrock animation files so GeckoLib can load them (flattens keyframe
    arrays, renames sound/particle effect keys, lower-cases Molang functions),
  * splits Godzilla's Bedrock "emissive alpha" textures (the add-on's live gojira2 set, kept exactly as they are)
    into an opaque cut-out texture plus a glow mask,
  * converts the audio (which ships as MP4/AAC with .wav/.ogg names) to mono Ogg Vorbis,
  * copies item, particle and boss-bar textures.
"""
import glob
import json
import os
import re
import shutil
import subprocess
import sys

import numpy as np
from PIL import Image

MODID = "godzillaminusone"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MODID)


def find_rp(base):
    for path in glob.glob(os.path.join(base, "**", "manifest.json"), recursive=True):
        rp = os.path.dirname(path)
        if os.path.isdir(os.path.join(rp, "models")) and os.path.isdir(os.path.join(rp, "textures")):
            return rp
    sys.exit("Could not find the add-on resource pack under " + base)


def out(*parts):
    path = os.path.join(ASSETS, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def write_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


# ---------------------------------------------------------------- textures

def to_gray(rgba, keep_blue=True, lift=16.0, gain=1.1):
    """Turns the warm brown/grey skin into a neutral charcoal grey.

    Saturated blue/cyan pixels (eyes, atomic-breath glow) are kept unless keep_blue is False.
    Luminance is preserved (slightly lifted so the result reads as dark grey rather than black).
    """
    a = rgba.astype(float)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    sat = (mx - mn) / np.maximum(mx, 1)
    d = np.maximum(mx - mn, 1e-6)
    hue = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) / 6
    blue = (hue > 0.42) & (hue < 0.72) & (sat > 0.3) & (mx > 60)
    lum = np.clip((0.299 * r + 0.587 * g + 0.114 * b) * gain + lift, 0, 255)
    grey = np.stack([lum - 1, lum, lum + 2], -1)
    mask = ~blue if keep_blue else np.ones_like(blue)
    res = a.copy()
    res[..., :3][mask] = np.clip(grey[mask], 0, 255)
    return res.astype(np.uint8)


def split_emissive(rgba):
    """Bedrock `entity_emissive_alpha`: alpha 0 is cut out, any other alpha is drawn opaque and
    glows by (1 - alpha). Returns (opaque cut-out texture, additive glow mask)."""
    alpha = rgba[..., 3].astype(float) / 255.0
    base = rgba.copy()
    base[..., 3] = np.where(rgba[..., 3] > 0, 255, 0)
    glow_strength = np.where(rgba[..., 3] > 0, 1.0 - alpha, 0.0)
    glow = np.zeros_like(rgba)
    glow[..., :3] = (rgba[..., :3].astype(float) * glow_strength[..., None]).astype(np.uint8)
    glow[..., 3] = np.where(glow_strength > 0.02, 255, 0)
    return base, glow


def load(path):
    return np.array(Image.open(path).convert("RGBA"))


def save(arr, *parts):
    Image.fromarray(arr).save(out(*parts))


def convert_textures(rp):
    t = os.path.join(rp, "textures")
    g2 = os.path.join(t, "entity", "gojira2")

    # The add-on draws default.png (cut out), then no_powered.png or, charged, powered.png over it with the
    # emissive-alpha material. no_powered.png is default.png plus the dorsal plates, so it is his skin.
    no_powered = load(os.path.join(g2, "no_powered.png"))
    base, glow = split_emissive(no_powered)
    save(base, "textures", "entity", "godzilla.png")
    save(glow, "textures", "entity", "godzilla_glow.png")

    # Charged, the plates turn icy cyan (powered.png differs from no_powered.png only there): mostly glowing,
    # nearly transparent pixels, plus a few opaque cyan ones. The renderer adds this over each plate as the
    # charge animation punches it up.
    powered = load(os.path.join(g2, "powered.png"))
    _, glow = split_emissive(powered)
    solid = (np.abs(powered.astype(int) - no_powered.astype(int)).sum(-1) > 0) & (powered[..., 3] == 255)
    glow[solid] = powered[solid]
    save(glow, "textures", "entity", "godzilla_charged_glow.png")

    dead, _ = split_emissive(load(os.path.join(g2, "dead.png")))
    save(dead, "textures", "entity", "godzilla_dead.png")

    # Core colour of the atomic breath beam (bone "B"); not part of the add-on, which used a Bedrock material.
    Image.new("RGBA", (16, 16), (170, 235, 255, 255)).save(out("textures", "entity", "godzilla_beam.png"))

    # Shinden uses Bedrock's alpha-test material (cut out below 50% alpha).
    shinden = load(os.path.join(t, "entity", "goji_kyushu_j7w1.png"))
    shinden[..., 3] = np.where(shinden[..., 3] >= 128, 255, 0)
    save(shinden, "textures", "entity", "shinden.png")

    base, glow = split_emissive(load(os.path.join(t, "entity", "mine.png")))
    save(base, "textures", "entity", "naval_mine.png")

    items = os.path.join(t, "items")
    shutil.copy(os.path.join(items, "goji_torrid.png"), out("textures", "item", "torrid.png"))
    shutil.copy(os.path.join(items, "mine.png"), out("textures", "item", "naval_mine_spawn_egg.png"))
    shutil.copy(os.path.join(items, "goji_kyushu_j7w1.png"), out("textures", "item", "shinden_spawn_egg.png"))
    save(to_gray(load(os.path.join(items, "gojira_egg.png"))), "textures", "item", "godzilla_spawn_egg.png")

    for name in ["black_rain", "blue_flame", "glow", "gunlight", "mushroom", "shockwave", "smoke"]:
        img = Image.open(os.path.join(t, "particles", name + ".png")).convert("RGBA")
        if name == "smoke" and img.size != (16, 16):  # 16x15 in the add-on; the particle atlas prefers square sprites
            sq = Image.new("RGBA", (16, 16))
            sq.paste(img, (0, 0))
            img = sq
        img.save(out("textures", "particle", name + ".png"))

    bar = os.path.join(t, "ui", "bossbars", "gojira")
    for name in ["health_empty", "health_full", "center_mark", "gojira_text", "shadow"]:
        shutil.copy(os.path.join(bar, name + ".png"), out("textures", "gui", "godzilla_bar", name + ".png"))


# ---------------------------------------------------------------- geometry

def level_shinden(geo):
    """The add-on's plane sits nose-up: its root bone is tilted 5 degrees and the nose gear is the longer leg.
    Level the fuselage and even out the gear (nose leg 4 px shorter, main legs 4 px longer), so it stands
    straight with both wheels on the ground."""
    shift = {"Will2": 4.0, "Will": -4.0, "bone3": -4.0}
    for bone in geo["bones"]:
        if bone["name"] == "bone":
            bone.pop("rotation", None)
        dy = shift.get(bone["name"])
        if dy is None:
            continue
        bone["pivot"][1] += dy
        for cube in bone.get("cubes", []):
            cube["origin"][1] += dy
            if "pivot" in cube:
                cube["pivot"][1] += dy


def convert_models(rp):
    m = os.path.join(rp, "models", "entity")
    for src, dst in [("goji_gojira", "godzilla"), ("goji_kyushu_j7w1", "shinden"), ("mine", "naval_mine")]:
        data = json.load(open(os.path.join(m, src + ".json")))
        data["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + dst
        if dst == "shinden":
            level_shinden(data["minecraft:geometry"][0])
        write_json(out("geo", "entity", dst + ".geo.json"), data)


# ---------------------------------------------------------------- animations

MOLANG_FIX = re.compile(r"\bMath\.", re.IGNORECASE)

# Bedrock client-entity effect keys -> the names our Java keyframe handlers understand.
SOUND_KEYS = {
    "step": "godzilla.step",
    "attack": "godzilla.attack",
    "attack1": "godzilla.stomp",
    "charge": "godzilla.charge",
    "cs": "godzilla.full_charge",
    "spawn": "godzilla.roar",
    "spawned": "godzilla.emerge",
    "roar2": "godzilla.roar2",
    "dead": "godzilla.death",
    "fly": "shinden.engine",
    "fire": "shinden.gun",
}
PARTICLE_KEYS = {
    "step": "step_dust",
    "st": "step_ring",
    "stattack": "stomp_ring",
    "stattackdust": "stomp_dust",
    "shockwave": "roar_ring",
    "beemsmoke": "beam_smoke",
    "water": "splash",
    "fire": "gun_flash",
    "fire2": "gun_flash",
}


def clean_value(v):
    if isinstance(v, str):
        return MOLANG_FIX.sub("math.", v)
    if isinstance(v, list):
        return [clean_value(x) for x in v]
    if isinstance(v, dict):
        return {k: clean_value(x) for k, x in v.items()}
    return v


def flatten_effects(effects, key_map, drop_unknown=True):
    """GeckoLib expects one effect object per timestamp. Bedrock allows arrays; spread them 1ms apart."""
    res = {}
    for time, value in effects.items():
        entries = value if isinstance(value, list) else [value]
        t = float(time)
        for entry in entries:
            name = entry.get("effect", "")
            if name not in key_map:
                if drop_unknown:
                    continue
            else:
                entry = dict(entry, effect=key_map[name])
            while "%.4f" % t in res:
                t += 0.001
            res["%.4f" % t] = entry
    return res


def convert_animation_file(src, dst, renames=None, extra=None):
    data = json.load(open(src))
    anims = {}
    for name, anim in data["animations"].items():
        new = {}
        for key, value in anim.items():
            if key == "bones":
                bones = {}
                for bone, channels in value.items():
                    channels = {c: clean_value(v) for c, v in channels.items() if c in ("rotation", "position", "scale")}
                    for c in ("scale",):
                        if isinstance(channels.get(c), (int, float)):
                            s = channels[c]
                            channels[c] = [s, s, s]
                    bones[bone] = channels
                new["bones"] = bones
            elif key == "sound_effects":
                effects = flatten_effects(value, SOUND_KEYS)
                if effects:
                    new[key] = effects
            elif key == "particle_effects":
                effects = flatten_effects(value, PARTICLE_KEYS)
                if effects:
                    new[key] = effects
            elif key == "timeline":
                continue
            else:
                new[key] = value
        if new.get("loop") and "animation_length" not in new:
            # Static looping poses have no length in Bedrock; GeckoLib needs a non-zero one.
            new["animation_length"] = 1.0
        anims[(renames or {}).get(name, name)] = new
    anims.update(extra or {})
    write_json(dst, {"format_version": "1.8.0", "animations": anims})


def steady_shinden(path):
    """The add-on rocks the plane from side to side the whole time it flies, and rolls it by a Molang
    "turning" animation; here it flies level and the renderer banks it into turns instead."""
    data = json.load(open(path))
    anims = data["animations"]
    anims["animation.kplane.fly"]["bones"].pop("bone", None)
    anims.pop("animation.kplane.turning", None)
    write_json(path, data)


def convert_animations(rp):
    a = os.path.join(rp, "animations")
    convert_animation_file(os.path.join(a, "gojira.animation.json"), out("animations", "entity", "godzilla.animation.json"))
    convert_animation_file(os.path.join(a, "kplane.animation.json"), out("animations", "entity", "shinden.animation.json"))
    steady_shinden(out("animations", "entity", "shinden.animation.json"))
    # The add-on references "animation.mine.idle" but never ships it; this is a gentle bob/spin.
    mine = {
        "animation.mine.idle": {
            "loop": True,
            "animation_length": 4.0,
            "bones": {
                "Root": {
                    "position": {"0.0": [0, 0, 0], "2.0": [0, 1.5, 0], "4.0": [0, 0, 0]},
                    "rotation": {"0.0": [0, 0, 0], "4.0": [0, 20, 0]},
                }
            },
        }
    }
    write_json(out("animations", "entity", "naval_mine.animation.json"), {"format_version": "1.8.0", "animations": mine})


# ---------------------------------------------------------------- sounds

SOUNDS = {
    # event: (files, stream). Hearing range is set by the volume the code plays them at (16 blocks per 1.0),
    # like vanilla, so the add-on's min_distance values (200-900) become volumes of 12-24 (roars: see ATTENUATION).
    "godzilla.ambient": (["gojira/gojira_stand", "gojira/gojira_stand2", "gojira/gojira_stand3"], False),
    "godzilla.roar": (["gojira/gojira_roar"], True),
    "godzilla.roar2": (["gojira/gojira_stand"], False),
    "godzilla.emerge": (["gojira/gojira_spawn2"], True),
    "godzilla.death": (["gojira/gojira_roar"], True),
    "godzilla.step": (["gojira/gojira_step"], False),
    "godzilla.stomp": (["gojira/attack1"], False),
    "godzilla.attack": (["gojira/gojira_attack"], False),
    "godzilla.hurt": (["gojira/gojira_hurts"], False),
    "godzilla.radiation": (["gojira/gojira_radiation"], False),
    "godzilla.charge": (["gojira/charge1", "gojira/charge2"], False),
    "godzilla.full_charge": (["gojira/full_charge"], True),
    "godzilla.atomic_blast": (["gojira/gojira_nuke"], True),
    "shinden.engine": (["kplane/fly"], False),
    "shinden.gun": (["kplane/fire"], False),
    "naval_mine.explode": (["naval_mine/naval_mine"], False),
    # "@name": made by tools/make_sounds.py, not converted from the add-on.
    "godzilla.inhale": (["@godzilla_inhale"], False),
    "nuke.alarm": (["@nuke_alarm"], True),
    # The Odo Island Monster, a younger, smaller Godzilla: his sounds, pitched up ("@": already converted above).
    "odo.ambient": (["@godzilla_stand", "@godzilla_stand2", "@godzilla_stand3"], False, 1.3),
    "odo.roar": (["@godzilla_roar"], True, 1.3),
    "odo.bite": (["@godzilla_attack"], False, 1.35),
    "odo.stomp": (["@godzilla_attack1"], False, 1.3),
    "odo.step": (["@godzilla_step"], False, 1.45),
    "odo.hurt": (["@godzilla_hurts"], False, 1.35),
    "odo.death": (["@godzilla_roar"], True, 1.1),
}


# Sounds heard further than their volume would carry them: blocks to fade out over (linearly). Godzilla's roars
# carry 3000 blocks: the server sends them to everyone in range (entity/GodzillaEntity.java, roar()).
ATTENUATION = {"godzilla.roar": 3000, "godzilla.roar2": 3000}


def ffmpeg_exe():
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return shutil.which("ffmpeg") or sys.exit("ffmpeg not found (pip install imageio-ffmpeg)")


def convert_sounds(rp):
    ff = ffmpeg_exe()
    src_dir = os.path.join(rp, "sounds", "mob")
    done = set()
    defs = {}
    for event, (files, stream, *pitch) in SOUNDS.items():
        entries = []
        for f in files:
            name = f.replace("/", "_").replace("gojira_gojira_", "godzilla_").replace("gojira_", "godzilla_")
            if f.startswith("@"):
                name = f[1:]
            elif f not in done:
                src = next(p for p in glob.glob(os.path.join(src_dir, f + ".*")))
                dst = out("sounds", name + ".ogg")
                subprocess.run([ff, "-y", "-loglevel", "error", "-i", src, "-vn", "-ac", "1", "-ar", "44100",
                                "-c:a", "libvorbis", "-q:a", "5", dst], check=True)
                done.add(f)
            entry = {"name": MODID + ":" + name}
            if stream:
                entry["stream"] = True
            if pitch:
                entry["pitch"] = pitch[0]
            if event in ATTENUATION:
                entry["attenuation_distance"] = ATTENUATION[event]
            entries.append(entry)
        defs[event] = {"subtitle": "subtitles." + MODID + "." + event, "sounds": entries}
    write_json(out("sounds.json"), defs)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    rp = find_rp(sys.argv[1])
    convert_textures(rp)
    convert_models(rp)
    convert_animations(rp)
    convert_sounds(rp)
    print("Assets written to", ASSETS)


if __name__ == "__main__":
    main()
