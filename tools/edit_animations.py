"""Adds the mod's own changes to the add-on's Godzilla animations.

    python3 tools/edit_animations.py

Run after tools/convert_addon.py. Edits animations/entity/godzilla.animation.json in place (and refuses to run
twice):

  * atomic breath: a 4 second in-breath (head and chest rear back, jaw opens, with its own breathing sound)
    inserted just before the beam, the head angled 10 degrees higher while firing, a longer, thicker beam
    that comes straight out of his open mouth, and the dorsal plates slamming back down the instant it fires
    (snap_plates),
  * a new "animation.gojira.swim_bite" (a lunging bite in the swimming pose, so he doesn't stand up to attack),
  * his melee combo: "animation.gojira.swipe_right" and "swipe_left" (claw swipes, the torso twisting into
    them) and "animation.gojira.spin_whip" (a heavy spin of his whole body, the tail trailing behind it and
    then snapping through in a wave from its base to its tip),
  * "animation.gojira.swim_peek": while he swims, his head rises out of the water (eyes just above the surface,
    the snout level) and glares ahead for 4 seconds, then sinks back,
  * five held poses for the thumbnail statues (entity.GodzillaStatueEntity), after the film's posters:
    "animation.gojira.pose_charging_roar" (roaring, one arm flung out, the glowing tail raised high round his
    side), "pose_tail_whip" (the tail swung round in front of him, its tip rising), "pose_sky_roar" (roaring
    up at the sky, arms spread), "pose_poster_snarl" (the 70th anniversary poster: braced and snarling, one arm
    reaching forward, the tail rising behind him in a tall J) and "pose_tail_hook" (turned three-quarters away,
    growling, the tail swept round in front of him in a broad open hook), and "pose_hunched_snarl" (square on,
    hunched forward, head low between his shoulders, snarling). The tails were solved for with a small
    search (see the tail waypoints), and the last two arms likewise, from where their elbows, wrists and fingers
    should be; their Root is turned so that the statue, which turns to face you, shows the poster's angle, and
    lowered so that the bent legs still stand on the ground.
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_preview as rp  # noqa: E402  (GeckoLib's bone maths)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
PATH = os.path.join(ASSETS, "animations", "entity", "godzilla.animation.json")
GEO = os.path.join(ASSETS, "geo", "entity", "godzilla.geo.json")

INHALE_AT = 10.42             # charge animation: head reared back, jaw open, just before the beam
INHALE_LENGTH = 4.0
BEAM_START = 10.9167 + INHALE_LENGTH
BEAM_END = 15.1667 + INHALE_LENGTH
HEAD_UP = -10.0               # degrees; negative X pitches the head up
BEAM_LENGTH = 1.25
BEAM_THICKNESS = 1.2
FIRING_SOUND = "godzilla.full_charge"
# Straightening up to fire (degrees): back and chest up, neck up, head down again so the beam keeps its angle;
# measured so that his mouth is above his chest, ~36 blocks up.
FIRE_RAISE = (("Metalbody", (-10, 0, 0)), ("Chest", (-20, 0, 0)), ("Neck", (-30, 0, 0)), ("Head", (60, 0, 0)))   # its roar peaks 1.2 s in, as the beam fires: it moves with the beam


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


PLATE_SNAP = 0.0833           # seconds the raised plates take to slam back down as the beam fires
PLATE_RECOIL = 0.15           # ...overshooting by this much of their rise, before settling (another 0.125 s)


def is_plate(bone):
    return bone == "T" or (bone.startswith("T") and bone[1:].isdigit())


def snap_plates(anim):
    """The dorsal plates, raised one after another through the charge, stay up until the beam fires and then all
    slam back down at once (in the add-on they slid down over a quarter of a second, starting before the beam)."""
    for bone, channels in anim["bones"].items():
        pos = channels.get("position")
        if not is_plate(bone) or not isinstance(pos, dict):
            continue
        lo, hi = BEAM_START - 0.49, BEAM_START + 0.3
        raised = sample(pos, lo)
        rest = sample(pos, hi)
        if max(abs(a - b) for a, b in zip(raised, rest)) < 1e-3:
            continue
        res = {fmt(t): v for t, v in keys(pos) if t < lo or t > hi}
        recoil = [round(r - (u - r) * PLATE_RECOIL, 4) for u, r in zip(raised, rest)]
        res[fmt(BEAM_START)] = raised
        res[fmt(BEAM_START + PLATE_SNAP)] = recoil
        res[fmt(BEAM_START + PLATE_SNAP + 0.125)] = rest
        channels["position"] = dict(sorted(res.items(), key=lambda kv: float(kv[0])))


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
    # ...and he straightens up to fire, head held high: in the add-on's pose it sank to chest height, in front of
    # his chest, so that seen from the ground the beam seemed to come out of his chest.
    for bone, offset in FIRE_RAISE:
        ch = bones.setdefault(bone, {})
        ch["rotation"] = add_offset(ch.get("rotation", [0, 0, 0]), BEAM_START - 0.5, BEAM_END + 0.5, offset, 0.5, 0.5)

    snap_plates(anim)

    # Longer, thicker beam that still starts at the mouth: bone B's offset already runs along its own axis,
    # so it grows with the beam's length.
    b = bones["B"]
    b["scale"] = {k: [round(val(v)[0] * BEAM_THICKNESS, 4), round(val(v)[1] * BEAM_THICKNESS, 4),
                      round(val(v)[2] * BEAM_LENGTH, 4) if val(v)[2] > 2 else val(v)[2]] for k, v in b["scale"].items()}


def beam_from_mouth(anim):
    """Bone B is a 2 pixel cube that the charge animation stretches (scale z) into the beam. Its keyframes put
    the beam's back end right inside the open mouth, between the jaws, pointing out along the middle of the
    gape, at every keyframe (so it stays in the mouth as it flickers and thins out at the end)."""
    bones_geo, _, _ = rp.load_geo(GEO)
    geo = {b["name"]: b for b in bones_geo}
    bones = anim["bones"]
    firing = BEAM_START + 1.0
    pose = {name: {c: sample(ch, firing) for c, ch in chans.items()} for name, chans in bones.items()}
    mats = rp.bone_matrices(bones_geo, pose)
    head_inv = np.linalg.inv(mats["Head"])
    jaw_in_head = head_inv @ mats["Jew"]

    def gl(p):   # Bedrock pixel coordinates -> GeckoLib's (x mirrored)
        return np.array([-p[0], p[1], p[2], 1.0])

    upper = gl((0.04, 72.75, -33.0))[:3]                     # the palate behind the upper front teeth
    lower = (jaw_in_head @ gl((0.04, 74.2, -32.0)))[:3]     # the tongue, behind the lower front teeth
    forward_upper = np.array([0.0, 0.0, -1.0])
    forward_lower = jaw_in_head[:3, :3] @ forward_upper
    d = forward_upper + forward_lower
    d[0] = 0.0
    d /= np.linalg.norm(d)
    mouth = (upper + lower) / 2 - d * 1.5                     # a little way back inside the mouth

    b = geo["B"]
    piv = gl(b["pivot"])[:3]
    rest = b.get("rotation", [0, 0, 0])[0]
    # the cube's front (its -z end) must point along d: GeckoLib turns it by -(rest + key) degrees about x
    theta = math.asin(d[1])
    key_x = -math.degrees(theta) - rest
    channel = bones["B"]
    channel["rotation"] = {k: ([round(key_x, 4), 0, 0] if val(v)[2] > 2 else [0, 0, 0])
                           for k, v in ((k, channel["scale"][k]) for k in channel["scale"])}
    positions = {}
    for k, v in channel["scale"].items():
        sz = val(v)[2]
        if sz <= 2:
            positions[k] = [0.0, 0.0, 0.0]
            continue
        centre = mouth + d * sz                               # its back end (centre - sz * d) at the mouth
        t = centre - piv
        positions[k] = [round(-t[0], 4), round(t[1], 4), round(t[2], 4)]
    channel["position"] = positions
    return mouth, d


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


def bite():
    """His bite on land (the "attack" animation he came with is mostly a claw swing): he rears his head back with his
    jaws gaping, lunges, head and neck thrust forward and down, and snaps them shut half a second in (when the bite
    lands: GodzillaEntity.Attack.BITE and PlayerGodzilla's bite), then draws back. Only the body, neck, head and jaw
    move, so it plays over walking too."""
    return {
        "animation_length": 1.0,
        "bones": {
            "Body": {"rotation": keyed({0.0: [0, 0, 0], 0.3: [-3, 0, 0], 0.5: [7, 0, 0], 0.7: [5, 0, 0], 1.0: [0, 0, 0]})},
            "Chest": {"rotation": keyed({0.0: [0, 0, 0], 0.3: [-6, 0, 0], 0.5: [12, 0, 0], 0.7: [9, 0, 0], 1.0: [0, 0, 0]})},
            "Neck": {"rotation": keyed({0.0: [0, 0, 0], 0.3: [-14, 0, 0], 0.5: [26, 0, 0], 0.7: [20, 0, 0], 1.0: [0, 0, 0]})},
            "Head": {"rotation": keyed({0.0: [-5, 0, 0], 0.3: [-22, 0, 0], 0.5: [6, 0, 0], 0.7: [2, 0, 0], 1.0: [-5, 0, 0]})},
            "Jew": {"rotation": keyed({0.0: [0, 0, 0], 0.25: [34, 0, 0], 0.42: [42, 0, 0], 0.5: [0, 0, 0], 0.62: [6, 0, 0],
                                       0.75: [0, 0, 0], 1.0: [0, 0, 0]})},
        },
        "sound_effects": {"0.1000": {"effect": "godzilla.attack"}},
    }


# ---------------------------------------------------------------- melee combo

TAIL = ("bone11", "bone13", "bone14", "bone16", "bone17", "bone18")


def keyed(frames):
    """{time: [x, y, z]} with the times formatted; values rounded."""
    return {fmt(t): [round(q, 3) for q in v] for t, v in sorted(frames.items())}


def swipe(anims, side):
    """A claw swipe: the arm is raised up and out, then slashes down and across the body as the torso twists into
    it. side 1 is his right arm, -1 his left (Bedrock x is his left, so the sideways rotations mirror)."""
    idle = anims["animation.gojira.idle"]["bones"]
    arm, forearm = ("Right_arm", "bone") if side > 0 else ("Left_arm", "bone4")
    s = side
    frames = {
        arm: {0.0: [0, 0, 0], 0.32: [-72, 12 * s, 48 * s], 0.42: [-78, 16 * s, 52 * s], 0.56: [8, -18 * s, -38 * s],
              0.72: [14, -20 * s, -30 * s], 1.1: [0, 0, 0]},
        forearm: {0.0: [0, 0, 0], 0.32: [-28, 0, 0], 0.42: [-32, 0, 0], 0.56: [10, 0, 0], 0.72: [14, 0, 0], 1.1: [0, 0, 0]},
        "Metalbody": {0.0: [0, 0, 0], 0.32: [-6, 16 * s, 0], 0.42: [-7, 20 * s, 0], 0.56: [6, -18 * s, 0], 0.72: [6, -16 * s, 0],
                      1.1: [0, 0, 0]},
        "Chest": {0.0: [0, 0, 0], 0.32: [-4, 8 * s, 0], 0.42: [-4, 10 * s, 0], 0.56: [5, -10 * s, 0], 0.72: [4, -9 * s, 0],
                  1.1: [0, 0, 0]},
        "Neck": {0.0: [0, 0, 0], 0.32: [0, -8 * s, 0], 0.56: [6, 6 * s, 0], 1.1: [0, 0, 0]},
        "Head": {0.0: [-5, 0, 0], 0.32: [-12, -6 * s, 0], 0.56: [4, 8 * s, 0], 1.1: [-5, 0, 0]},
        "Jew": {0.0: [0, 0, 0], 0.3: [22, 0, 0], 0.5: [26, 0, 0], 0.62: [4, 0, 0], 1.1: [0, 0, 0]},
    }
    bones = {name: {"rotation": keyed(k)} for name, k in frames.items()}
    # the tail swings the other way to balance him
    for i, name in enumerate(TAIL):
        base = sample(idle[name]["rotation"], 0.0)
        swing = -10 * s * (0.5 + 0.2 * i)
        bones[name] = {"rotation": keyed({0.0: base, 0.42: [base[0], base[1] - swing * 0.5, 0], 0.66: [base[0], base[1] + swing, 0],
                                          1.1: base})}
    return {
        "animation_length": 1.1,
        "bones": bones,
        "sound_effects": {"0.2800": {"effect": "godzilla.attack"}},
    }


def spin_whip(anims):
    """A heavy spin of his whole body, the tail trailing behind and then snapping through in a wave."""
    idle = anims["animation.gojira.idle"]["bones"]
    length, dt = 2.2, 1.0 / 240

    def spin(t):
        # wind up a little the other way, spin a full turn with a heavy start and a hard stop
        if t < 0.35:
            return -25.0 * smooth(t / 0.35)
        if t < 1.15:
            return -25.0 + 385.0 * smooth((t - 0.35) / 0.8)
        return 360.0

    # the tail: a chain of damped springs, each segment pulled towards the angle of the one before it
    k, c = 220.0, 9.0
    n = len(TAIL)
    ang = [0.0] * n
    vel = [0.0] * n
    samples = {}
    t = 0.0
    prev_root = spin(0.0)
    while t <= length + 1e-9:
        root = spin(t)
        root_vel = (root - prev_root) / dt
        prev_root = root
        for i in range(n):
            parent = root if i == 0 else ang[i - 1]
            parent_vel = root_vel if i == 0 else vel[i - 1]
            acc = k * (parent - ang[i]) + c * (parent_vel - vel[i]) - 1.2 * vel[i]
            vel[i] += acc * dt
            ang[i] += vel[i] * dt
        step = round(t / 0.05)
        if abs(t - step * 0.05) < dt / 2:
            samples[round(step * 0.05, 4)] = (root, list(ang))
        t += dt

    bones = {"Root": {"rotation": keyed({tt: [0, v[0], 0] for tt, v in samples.items()})}}
    for i, name in enumerate(TAIL):
        base = sample(idle[name]["rotation"], 0.0)
        frames = {}
        for tt, (root, a) in samples.items():
            parent = root if i == 0 else a[i - 1]
            rel = max(-45.0, min(45.0, a[i] - parent))
            # the snap also cracks up and down, travelling from the base to the tip
            snap = 0.0
            ts = tt - 1.05 - 0.06 * i
            if 0.0 < ts < 0.6:
                snap = -14.0 * math.sin(ts / 0.6 * 2 * math.pi) * (1 - ts / 0.6) * (0.6 + 0.2 * i)
            frames[tt] = [base[0] + snap, base[1] + rel, 0]
        bones[name] = {"rotation": keyed(frames)}
    # he hunches into the spin, arms tucked in, then straightens
    hunch = {0.0: [0, 0, 0], 0.35: [6, 0, 0], 0.9: [8, 0, 0], 1.3: [4, 0, 0], 1.8: [0, 0, 0]}
    bones["Body"] = {"rotation": keyed(hunch)}
    bones["Metalbody"] = {"rotation": keyed({0.0: [0, 0, 0], 0.35: [4, -12, 0], 0.9: [6, 14, 0], 1.3: [3, 6, 0], 1.8: [0, 0, 0]})}
    for arm, s in (("Right_arm", 1), ("Left_arm", -1)):
        bones[arm] = {"rotation": keyed({0.0: [0, 0, 0], 0.35: [-20, 0, -10 * s], 1.3: [-20, 0, -10 * s], 1.8: [0, 0, 0]})}
    bones["Head"] = {"rotation": keyed({0.0: [-5, 0, 0], 0.35: [-2, 10, 0], 1.0: [-8, -12, 0], 1.4: [-12, 0, 0], 2.2: [-5, 0, 0]})}
    bones["Jew"] = {"rotation": keyed({0.0: [0, 0, 0], 1.1: [0, 0, 0], 1.3: [24, 0, 0], 1.6: [20, 0, 0], 1.9: [0, 0, 0]})}
    return {
        "animation_length": length,
        "bones": bones,
        "sound_effects": {"1.2000": {"effect": "godzilla.attack"}},
        "particle_effects": {"0.8500": {"effect": "tail_sweep", "locator": ""}, "1.3000": {"effect": "tail_snap", "locator": ""}},
    }


def swim_peek(anims):
    """Head up out of the water, glaring ahead, then back under: measured so that his eyes end up ~1.2 blocks
    above the surface with the snout level (the jaw stays under water)."""
    swim = anims["animation.gojira.swim2"]["bones"]
    head0 = sample(swim["Head"]["rotation"], 0.0)
    neck0 = sample(swim["Neck"]["rotation"], 0.0)
    rise, hold = 0.7, 4.0
    down = rise + hold

    def over(base, d):
        return [round(a + b, 4) for a, b in zip(base, d)]

    up_neck, up_neck2, up_head = (-55, 0, 0), (-10, 0, 0), (55, 0, 0)
    head_frames = {0.0: head0, rise: over(head0, up_head)}
    # the glare: he slowly looks from side to side, then fixes straight ahead
    for i, (t, yaw) in enumerate(((1.5, -7), (2.5, 6), (3.3, 0))):
        head_frames[t] = over(head0, (up_head[0], yaw, -head0[2]))
    head_frames[down] = over(head0, (up_head[0], 0, -head0[2]))
    head_frames[down + 0.8] = head0
    return {
        "animation_length": round(down + 0.8, 4),
        "bones": {
            "Neck": {"rotation": keyed({0.0: neck0, rise: over(neck0, up_neck), down: over(neck0, up_neck), down + 0.8: neck0})},
            "neck2": {"rotation": keyed({0.0: [0, 0, 0], rise: list(up_neck2), down: list(up_neck2), down + 0.8: [0, 0, 0]})},
            "Head": {"rotation": keyed(head_frames)},
            "Jew": {"rotation": keyed({0.0: [0, 0, 0], rise: [4, 0, 0], down: [4, 0, 0], down + 0.8: [0, 0, 0]})},
        },
        "sound_effects": {fmt(rise + 0.2): {"effect": "godzilla.ambient"}},
        "particle_effects": {fmt(rise * 0.6): {"effect": "splash", "locator": "y"}, fmt(down + 0.4): {"effect": "splash", "locator": "y"}},
    }


POSES = {
    "pose_charging_roar": {
        "bone11": [6, -30, 0], "bone13": [10, -26, 0], "bone14": [24, -16, 0], "bone16": [30, 0, 0], "bone17": [26, 12, 0],
        "bone18": [16, 16, 0],
        "Metalbody": [-4, 10, 0], "Chest": [-6, 6, 0], "Neck": [-14, 8, 0], "Head": [-20, 12, 4], "Jew": [40, 0, 0],
        "Right_arm": [-38, 0, 6], "bone": [-22, 0, 0], "Left_arm": [-12, 0, -52], "bone4": [18, 0, 0],
        "Leg": [-12, 0, 0], "Leg_bone": [8, 0, 0], "Leg2": [8, 0, 0],
    },
    # the tail: low round his right side and in front of his legs, then rising (waypoints, in blocks right/up/forward:
    # (4,5,-12) (12,3,-18) (22,3,-12) (25,7,0) (21,17,9), tip (15,32,14))
    "pose_tail_whip": {
        "bone11": [14.3, -27.2, 0], "bone13": [-20.7, -17.6, 0], "bone14": [-20.7, -58.9, 0], "bone16": [6.8, -60, 0],
        "bone17": [32, -25.3, 0], "bone18": [21.6, -5.5, 0],
        "Metalbody": [2, -14, 0], "Chest": [0, -8, 0], "Neck": [-6, 6, 0], "Head": [-2, 8, 0], "Jew": [18, 0, 0],
        "Right_arm": [-48, 0, 12], "bone": [-25, 0, 0], "Left_arm": [-42, 0, -14], "bone4": [-22, 0, 0],
        "Leg": [-18, 0, 0], "Leg_bone": [8, 0, 0], "Leg2": [14, 0, 0],
    },
    "pose_sky_roar": {
        "Body": [-4, 0, 0], "Metalbody": [-10, 0, 0], "Chest": [-14, 0, 0],
        "Neck": [-38, 0, 0], "neck2": [-18, 0, 0], "Head": [-28, 0, 0], "Jew": [42, 0, 0],
        "Right_arm": [5, 0, 42], "bone": [-15, 0, 0], "Left_arm": [5, 0, -42], "bone4": [-15, 0, 0],
        "bone11": [0, 12, 0], "bone13": [0, 16, 0], "bone14": [0, 22, 0], "bone16": [0, 18, 0], "bone17": [0, 10, 0],
        "bone18": [0, -12, 0],
        "Leg": [-6, 0, 0], "Leg2": [4, 0, 0],
    },
    # Seen from in front (where the statue faces), he stands turned 30 degrees to his right, so that you see him
    # from his front left, from low down, as on the poster. The tail (waypoints, in blocks right/up/forward:
    # (0,14,-10) (-2,16,-20) (-6,24,-27) (-8,36,-29) (-8,47,-29), tip (-7,59,-28)) runs back from his hips, out
    # towards his left, then rises steeply, higher than his head. The left hand reaches forward at the bottom of his
    # chest, its claws turned up towards you; the right hangs out to the side, lower. Mouth and plates are lit
    # (GodzillaStatueEntity.Glow.CHARGED).
    "pose_poster_snarl": {
        "Root": {"rotation": [0, 30, 0], "position": [0, -2.3, 0]},
        "Leg2": [-14, -4, 0], "Leg_bone2": [12, 0, 0], "Foot2": [2, 0, 0],
        "Leg": [8, 0, 4], "Leg_bone": [10, 0, 0], "Foot": [-18, 0, -4],
        "Body": [-2, 0, 0], "Metalbody": [-6, -8, 0], "Chest": [-8, -8, 0],
        "Neck": [32, 0, 0], "neck2": [16, -15, 0], "Head": [0, -25, 6], "Jew": [24, 0, 0],
        "Left_arm": [1.3, 13.4, -18.9], "bone4": [-16.7, 10.1, -4.1], "bone5": [-80, 40, 40], "Finger2": [15, 0, 0],
        "Right_arm": [2.7, -12.8, 24.2], "bone": [18.7, -0.9, 6.4], "bone2": [10, 0, -10], "Finger": [20, 0, 0],
        "bone11": [40.8, -0.7, 0], "bone13": [-8.5, 15.8, 0], "bone14": [6.3, 7.7, 0], "bone16": [17.8, 0, 0],
        "bone17": [21.1, -2.7, 0], "bone18": [21.4, -3, 0],
    },
    # Turned 55 degrees to his right, so that from in front you see his left side three-quarters on, his head to
    # your left. Knees bent, hunched forward, growling; the near (left) arm bent in front of his chest, the hand
    # hanging from the wrist, the far one lower. The tail (waypoints, as above: (-11,16,-2) (-16,14,6) (-12,12,16)
    # (-3,16,24) (4,23,28), tip (8,35,28)) comes round his left side and across in front of his legs, then curls up
    # on your left, the tip turning back in. No glow.
    "pose_tail_hook": {
        "Root": {"rotation": [0, 55, 0], "position": [0, -4.5, 0]},
        "Leg2": [-20, 0, 0], "Leg_bone2": [30, 0, 0], "Foot2": [-10, 0, 0],
        "Leg": [-10, 0, 4], "Leg_bone": [28, 0, 0], "Foot": [-18, 0, -4],
        "Body": [6, 0, 0], "Metalbody": [6, 6, 0], "Chest": [8, 4, 0],
        "Neck": [18, 0, 0], "Head": [-10, 0, 0], "Jew": [20, 0, 0],
        "Left_arm": [-29.2, 2.2, -2.1], "bone4": [-13, 8.2, 17.5], "bone5": [40, 0, 0], "Finger2": [25, 0, 0],
        "Right_arm": [-8.8, -0.7, 4.5], "bone": [-20.3, -8.3, -13.7], "bone2": [30, 0, 0], "Finger": [20, 0, 0],
        "bone11": [49.3, 79.3, 0], "bone13": [-29.9, 70.6, 0], "bone14": [-11.1, 53.3, 0], "bone16": [12.2, 22.4, 0],
        "bone17": [36.4, 5.3, 0], "bone18": [47.6, 0.7, 0],
    },
    # Almost square on to you, the upper body leaning well forward (Body, Metalbody, Chest pitched 9, 11, 13 degrees),
    # the neck bent down (Neck 42, neck2 16) so that the head hangs low in front of the shoulders, between their high
    # armour pads, then tipped back up (Head -52) so the snout points a little down and the face and eyes look at a
    # camera below his chest; jaw a little open (6) in a snarl. Joint rotations only, added to the model's rest
    # rotations (nothing moved, no proportions changed; the root only goes down so his feet stay on the ground). His
    # right arm (on your left: in GeckoLib's space his right is +x and he faces -z) bends out at the elbow and brings
    # its claw forward to the bottom of his chest, fingers curled; his left (on your right) hangs lower and further
    # out, the elbow a little bent, the claw open and angled down. His left foot is a step forward, knees bent; the
    # tail trails behind. The arms were solved (solve_limb, a small optimiser over each arm's two joints) from where
    # its elbow, wrist and knuckles should be in his chest's frame (x his right, y up, z back), all within the arm's
    # reach: right (21,56,-13) (15.5,54,-21.5) (14.5,52,-24), left (-21.5,56.5,-10) (-27.5,47.5,-12.5)
    # (-28.6,45.2,-13.2). No glow.
    "pose_hunched_snarl": {
        "Root": {"rotation": [0, 0, 0], "position": [0, -2.857, 0]},
        "Leg2": [-14, 0, 0], "Leg_bone2": [16, 0, 0], "Foot2": [-2, 0, 0],
        "Leg": [6, 0, 3], "Leg_bone": [12, 0, 0], "Foot": [-18, 0, -3],
        "Body": [9, 0, 0], "Metalbody": [11, 0, 0], "Chest": [13, 0, 0],
        "Neck": [42, 0, 0], "neck2": [16, 0, 0], "Head": [-52, 0, 0], "Jew": [6, 0, 0],
        "Right_arm": [-17.9, -3.8, 27.4], "bone": [-37.8, -29.2, -24.0],
        "bone2": [25, -10, 0], "Finger": [60, 0, 0], "bone3": [35, 0, 0],
        "Left_arm": [-2.7, 8.8, -29.9], "bone4": [20.7, 3.2, 8.6],
        "bone5": [-15, 0, 0], "Finger2": [-12, 0, 0], "bone6": [-10, 0, 0],
        "bone11": [-6, 4, 0], "bone13": [2, 8, 0], "bone14": [6, 10, 0], "bone16": [8, 8, 0], "bone17": [6, 4, 0],
        "bone18": [2, -4, 0],
    },
}


def poses(anims):
    for name, pose in POSES.items():
        bones = {}
        for bone, value in pose.items():
            channels = value if isinstance(value, dict) else {"rotation": value}
            bones[bone] = {channel: [round(float(q), 3) for q in v] for channel, v in channels.items()}
        anims["animation.gojira." + name] = {"loop": True, "animation_length": 1.0, "bones": bones}


def main():
    data = json.load(open(PATH))
    anims = data["animations"]
    if "animation.gojira.swim_bite" in anims:
        raise SystemExit("already edited; re-run tools/convert_addon.py first")
    edit_charge(anims["animation.gojira.charge"])
    mouth, d = beam_from_mouth(anims["animation.gojira.charge"])
    print("beam leaves the mouth at %s px (head space), pointing %.1f degrees below the snout"
          % (np.round(mouth, 2), math.degrees(math.asin(-d[1]))))
    anims["animation.gojira.swim_bite"] = swim_bite(anims)
    anims["animation.gojira.swipe_right"] = swipe(anims, 1)
    anims["animation.gojira.swipe_left"] = swipe(anims, -1)
    anims["animation.gojira.spin_whip"] = spin_whip(anims)
    anims["animation.gojira.swim_peek"] = swim_peek(anims)
    anims["animation.gojira.bite"] = bite()
    poses(anims)
    with open(PATH, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")
    print("charge: beam %.2f-%.2f s, length %.2f s" % (BEAM_START, BEAM_END, anims["animation.gojira.charge"]["animation_length"]))


if __name__ == "__main__":
    main()
