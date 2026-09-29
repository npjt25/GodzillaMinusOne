"""Adds the mod's own changes to the add-on's Godzilla animations.

    python3 tools/edit_animations.py

Run after tools/convert_addon.py. Edits animations/entity/godzilla.animation.json in place (and refuses to run
twice):

  * atomic breath: a 4 second in-breath (head and chest rear back, jaw opens, with its own breathing sound)
    inserted just before the beam, the head angled 10 degrees higher while firing, and a longer, thicker beam,
  * a new "animation.gojira.swim_bite" (a lunging bite in the swimming pose, so he doesn't stand up to attack).
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "animations", "entity",
                    "godzilla.animation.json")

INHALE_AT = 10.42             # charge animation: head reared back, jaw open, just before the beam
INHALE_LENGTH = 4.0
BEAM_START = 10.9167 + INHALE_LENGTH
BEAM_END = 15.1667 + INHALE_LENGTH
HEAD_UP = -10.0               # degrees; negative X pitches the head up
BEAM_LENGTH = 1.25
BEAM_THICKNESS = 1.2
FIRING_SOUND = "godzilla.full_charge"   # its roar peaks 1.2 s in, as the beam fires: it moves with the beam


def val(x):
    if isinstance(x, dict):
        x = x.get("post", x.get("pre", x.get("vector")))
    if isinstance(x, (int, float)):
        return [float(x)] * 3
    return [float(q) if isinstance(q, (int, float)) else 0.0 for q in x]


def keys(channel):
    return sorted((float(k), v) for k, v in channel.items())


def sample(channel, t):
    if not isinstance(channel, dict):
        return val(channel)
    ks = keys(channel)
    if t <= ks[0][0]:
        return val(ks[0][1])
    for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            a, b = val(v0), val(v1)
            f = (t - t0) / max(t1 - t0, 1e-9)
            return [p + (q - p) * f for p, q in zip(a, b)]
    return val(ks[-1][1])


def fmt(t):
    return "%.4f" % t


def shift_after(channel, t0, dt):
    """Pauses a channel at t0 for dt seconds: keys after t0 move later, the value at t0 is held meanwhile."""
    if not isinstance(channel, dict):
        return channel
    ks = keys(channel)
    if ks[-1][0] <= t0:
        return channel
    held = sample(channel, t0)
    res = {}
    for t, v in ks:
        res[fmt(t + dt if t > t0 else t)] = v
    if ks[0][0] < t0:
        res.setdefault(fmt(t0), held)
        res.setdefault(fmt(t0 + dt), held)
    return res


def smooth(x):
    x = min(max(x, 0.0), 1.0)
    return x * x * (3 - 2 * x)


def add_offset(channel, start, end, offset, ramp_in, ramp_out, step=0.05):
    """Adds offset (x, y, z) over [start, end], easing in and out, by resampling the channel."""
    if not isinstance(channel, dict):
        channel = {"0.0": val(channel)}
    ks = keys(channel)
    res = {fmt(t): v for t, v in ks if t < start - 1e-6 or t > end + 1e-6}
    n = int(round((end - start) / step))
    for i in range(n + 1):
        t = start + i * step
        if t < start + ramp_in:
            e = smooth((t - start) / ramp_in)
        elif t > end - ramp_out:
            e = smooth((end - t) / ramp_out)
        else:
            e = 1.0
        base = sample(channel, t)
        res[fmt(t)] = [round(b + o * e, 4) for b, o in zip(base, offset)]
    return res


def edit_charge(anim):
    bones = anim["bones"]
    for bone, channels in bones.items():
        for c in list(channels):
            channels[c] = shift_after(channels[c], INHALE_AT, INHALE_LENGTH)
    def later(t, v):
        return float(t) > INHALE_AT or (isinstance(v, dict) and v.get("effect") == FIRING_SOUND)

    for key in ("sound_effects", "particle_effects"):
        if key in anim:
            anim[key] = {fmt(float(t) + (INHALE_LENGTH if later(t, v) else 0)): v for t, v in anim[key].items()}
    anim["animation_length"] = round(anim["animation_length"] + INHALE_LENGTH, 4)

    # The in-breath: rear further back, open the jaw, then snap forward into the beam.
    start, end = INHALE_AT, INHALE_AT + INHALE_LENGTH
    for bone, offset in (("Head", (-8, 0, 0)), ("neck2", (-6, 0, 0)), ("Neck", (-3, 0, 0)), ("Chest", (-4, 0, 0)),
                         ("Metalbody", (-5, 0, 0)), ("Jew", (10, 0, 0)), ("Right_arm", (-10, 0, 0)),
                         ("Left_arm", (-10, 0, 0))):
        ch = bones.setdefault(bone, {})
        ch["rotation"] = add_offset(ch.get("rotation", [0, 0, 0]), start, end, offset, 3.0, 0.8)
    # The breathing sound lasts exactly as long as the in-breath (tools/make_sounds.py).
    anim.setdefault("sound_effects", {})[fmt(start)] = {"effect": "godzilla.inhale"}
    for key in ("sound_effects", "particle_effects"):
        if key in anim:
            anim[key] = dict(sorted(anim[key].items(), key=lambda kv: float(kv[0])))

    # Head angled 10 degrees up while the beam fires, so the breath reaches further.
    head = bones["Head"]
    head["rotation"] = add_offset(head["rotation"], BEAM_START - 0.15, BEAM_END + 0.25, (HEAD_UP, 0, 0), 0.15, 0.25)

    # Longer, thicker beam that still starts at the mouth: bone B's offset already runs along its own axis,
    # so it grows with the beam's length.
    b = bones["B"]
    b["scale"] = {k: [round(val(v)[0] * BEAM_THICKNESS, 4), round(val(v)[1] * BEAM_THICKNESS, 4),
                      round(val(v)[2] * BEAM_LENGTH, 4) if val(v)[2] > 2 else val(v)[2]] for k, v in b["scale"].items()}
    b["position"] = {k: [round(q * BEAM_LENGTH, 4) for q in val(v)] for k, v in b["position"].items()}


def swim_bite(anims):
    """A lunging bite in the swimming pose (the action controller overrides just the head and neck)."""
    swim = anims["animation.gojira.swim2"]["bones"]
    head0 = sample(swim["Head"]["rotation"], 0.0)
    neck0 = sample(swim["Neck"]["rotation"], 0.0)

    def add(base, d):
        return [round(a + b, 4) for a, b in zip(base, d)]

    # In the swim pose his back is at the surface and the head underwater: rear the head up out of the
    # water with the jaw open, then snap down onto whatever is floating there.
    return {
        "animation_length": 1.1,
        "bones": {
            "Neck": {"rotation": {"0.0": neck0, "0.3": add(neck0, (-30, 0, 0)), "0.55": add(neck0, (-22, 0, 0)),
                                  "1.1": neck0}},
            "neck2": {"rotation": {"0.0": [0, 0, 0], "0.3": [-30, 0, 0], "0.55": [-22, 0, 0], "1.1": [0, 0, 0]}},
            "Head": {"rotation": {"0.0": head0, "0.3": add(head0, (-10, 0, 0)), "0.55": add(head0, (20, 0, 0)),
                                  "1.1": head0}},
            "Jew": {"rotation": {"0.0": [0, 0, 0], "0.3": [38, 0, 0], "0.5": [38, 0, 0], "0.58": [0, 0, 0], "1.1": [0, 0, 0]}},
        },
        "sound_effects": {"0.3000": {"effect": "godzilla.attack"}},
        "particle_effects": {"0.5500": {"effect": "splash", "locator": "y"}},
    }


def main():
    data = json.load(open(PATH))
    anims = data["animations"]
    if "animation.gojira.swim_bite" in anims:
        raise SystemExit("already edited; re-run tools/convert_addon.py first")
    edit_charge(anims["animation.gojira.charge"])
    anims["animation.gojira.swim_bite"] = swim_bite(anims)
    with open(PATH, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")
    print("charge: beam %.2f-%.2f s, length %.2f s" % (BEAM_START, BEAM_END, anims["animation.gojira.charge"]["animation_length"]))


if __name__ == "__main__":
    main()
