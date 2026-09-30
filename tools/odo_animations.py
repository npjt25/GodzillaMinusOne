"""Animations of the Odo Island Monster (written by tools/make_odo_model.py).

Rotations are Bedrock degrees added to each bone's rest pose: negative x lifts the front (-z) end of a bone up,
which swings a downward-pointing limb forward.
"""
import json


def key(values, smooth=True):
    """A keyframe map {time: [x, y, z]}, catmull-rom smoothed for looping motion."""
    out = {}
    for t, v in values:
        v = [round(float(q), 3) for q in v]
        out["%.4f" % t] = {"post": v, "lerp_mode": "catmullrom"} if smooth else v
    return out


def cycle(length, phases, offset=0.0):
    """Keyframes for a loop: phases are (fraction of the cycle, value); shifted by offset (fraction)."""
    pts = []
    for f, v in phases:
        t = ((f + offset) % 1.0) * length
        pts.append((t, v))
    pts.sort()
    # close the loop: values at 0 and at the end must match
    if pts[0][0] > 0:
        # interpolate the value at t=0 from the neighbours across the wrap
        (t_last, v_last), (t_first, v_first) = pts[-1], pts[0]
        span = length - t_last + t_first
        a = (length - t_last) / span
        first = [p + (q - p) * a for p, q in zip(v_last, v_first)]
        pts.insert(0, (0.0, first))
    pts.append((length, pts[0][1]))
    return key(pts)


def rot(x=0.0, y=0.0, z=0.0):
    return [x, y, z]


# Leg poses (thigh, shin, ankle, foot) at foot contact, mid-stance, push-off and mid-swing, solved so that the
# planted foot stays on the ground and travels straight back under the body (stride of 22 / 24 px).
WALK_LEG = {"contact": (-8, -27, 23, 7), "mid": (2, -3, -1, 2), "push": (4, 33, -13, -2), "swing": (-30, 48, -7, 4)}
RUN_LEG = {"contact": (-20, -30, 20, 13), "mid": (-14, -3, 14, -9), "push": (0, 33, -34, 11), "swing": (-38, 48, -19, 12)}


def leg_cycle(length, offset, table):
    phases = (("contact", 0.0), ("mid", 0.25), ("push", 0.5), ("swing", 0.75))
    return {part: cycle(length, [(f, rot(table[name][i])) for name, f in phases], offset)
            for i, part in enumerate(("thigh", "shin", "ankle", "foot"))}


def locomotion(length, table, lean, step_sound, bob):
    bones = {}
    for side, offset in (("left", 0.0), ("right", 0.5)):
        for part, frames in leg_cycle(length, offset, table).items():
            bones["%s_%s" % (part, side)] = {"rotation": frames}
    bones["body"] = {
        "rotation": cycle(length, [(0.0, rot(lean, 2, 2)), (0.25, rot(lean, 0, 0)), (0.5, rot(lean, -2, -2)), (0.75, rot(lean, 0, 0))]),
        "position": cycle(length, [(0.0, [0, -bob, 0]), (0.25, [0, bob * 0.3, 0]), (0.5, [0, -bob, 0]), (0.75, [0, bob * 0.3, 0])]),
    }
    bones["neck"] = {"rotation": cycle(length, [(0.0, rot(-lean * 0.5 + 3)), (0.25, rot(-lean * 0.5 - 2)),
                                                (0.5, rot(-lean * 0.5 + 3)), (0.75, rot(-lean * 0.5 - 2))])}
    bones["head"] = {"rotation": cycle(length, [(0.0, rot(-lean * 0.4 - 2)), (0.25, rot(-lean * 0.4 + 2)),
                                                (0.5, rot(-lean * 0.4 - 2)), (0.75, rot(-lean * 0.4 + 2))])}
    for i, amp in enumerate((4, 5, 6, 7, 8)):
        bones["tail%d" % (i + 1)] = {"rotation": cycle(length, [(0.0, rot(-lean * 0.3 if i == 0 else 0, -amp)),
                                                               (0.5, rot(-lean * 0.3 if i == 0 else 0, amp))], 0.12 * (i + 1))}
    for side, sign in (("left", 1), ("right", -1)):
        bones["arm_" + side] = {"rotation": cycle(length, [(0.0, rot(8)), (0.5, rot(-8))], 0.0 if sign > 0 else 0.5)}
    return {
        "loop": True,
        "animation_length": length,
        "bones": bones,
        "sound_effects": {"%.4f" % 0.02: {"effect": step_sound}, "%.4f" % (length / 2 + 0.02): {"effect": step_sound}},
    }


def idle():
    length = 3.0
    bones = {
        "chest": {"rotation": cycle(length, [(0.0, rot(0)), (0.5, rot(-2.5))])},
        "neck": {"rotation": cycle(length, [(0.0, rot(0, 0)), (0.3, rot(-2, 6)), (0.7, rot(1, -6))])},
        "head": {"rotation": cycle(length, [(0.0, rot(0)), (0.5, rot(-3))])},
        "jaw": {"rotation": cycle(length, [(0.0, rot(0)), (0.45, rot(0)), (0.55, rot(6)), (0.65, rot(0))])},
        "body": {"position": cycle(length, [(0.0, [0, 0, 0]), (0.5, [0, 0.5, 0])])},
    }
    for i, amp in enumerate((2, 3, 4, 5, 6)):
        bones["tail%d" % (i + 1)] = {"rotation": cycle(length, [(0.0, rot(0, -amp)), (0.5, rot(0, amp))], 0.1 * (i + 1))}
    return {"loop": True, "animation_length": length, "bones": bones}


def bite():
    t = [0.0, 0.3, 0.42, 0.55, 0.9]
    return {
        "animation_length": 0.9,
        "bones": {
            "body": {"position": key(zip(t, [[0, 0, 0], [0, 0, 1.5], [0, 0.3, -4], [0, 0.3, -4], [0, 0, 0]])),
                     "rotation": key(zip(t, [rot(0), rot(-4), rot(6), rot(6), rot(0)]))},
            "neck": {"rotation": key(zip(t, [rot(0), rot(-12), rot(18), rot(16), rot(0)]))},
            "neck2": {"rotation": key(zip(t, [rot(0), rot(-6), rot(10), rot(8), rot(0)]))},
            "head": {"rotation": key(zip(t, [rot(0), rot(-18), rot(4), rot(2), rot(0)]))},
            "jaw": {"rotation": key(zip(t, [rot(0), rot(38), rot(0), rot(0), rot(0)]))},
        },
        "sound_effects": {"0.2000": {"effect": "odo.bite"}},
    }


def stomp():
    t = [0.0, 0.5, 0.72, 0.8, 1.3]
    return {
        "animation_length": 1.3,
        "bones": {
            "body": {"rotation": key(zip(t, [rot(0), rot(-12, 0, -4), rot(6, 0, 3), rot(5, 0, 2), rot(0)])),
                     "position": key(zip(t, [[0, 0, 0], [0, 2.5, 0], [0, 1.2, 0], [0, 1.0, 0], [0, 0, 0]]))},
            "thigh_left": {"rotation": key(zip(t, [rot(0), rot(-55), rot(-5), rot(-5), rot(0)]))},
            "shin_left": {"rotation": key(zip(t, [rot(0), rot(55), rot(0), rot(0), rot(0)]))},
            "ankle_left": {"rotation": key(zip(t, [rot(0), rot(-35), rot(5), rot(5), rot(0)]))},
            "neck": {"rotation": key(zip(t, [rot(0), rot(-15), rot(12), rot(10), rot(0)]))},
            "head": {"rotation": key(zip(t, [rot(0), rot(-8), rot(8), rot(6), rot(0)]))},
            "jaw": {"rotation": key(zip(t, [rot(0), rot(20), rot(30), rot(10), rot(0)]))},
            "tail1": {"rotation": key(zip(t, [rot(0), rot(8), rot(-6), rot(-4), rot(0)]))},
        },
        "sound_effects": {"0.7200": {"effect": "odo.stomp"}},
        "particle_effects": {"0.7200": {"effect": "odo_stomp", "locator": "foot_left"}},
    }


def roar():
    t = [0.0, 0.45, 0.7, 1.4, 2.0, 2.6]
    shake = [rot(0), rot(-18), rot(-20, 4), rot(-20, -4), rot(-17), rot(0)]
    return {
        "animation_length": 2.6,
        "bones": {
            "body": {"rotation": key(zip(t, [rot(0), rot(-8), rot(-9), rot(-9), rot(-8), rot(0)])),
                     "position": key(zip(t, [[0, 0, 0], [0, -1.1, 0], [0, -1.2, 0], [0, -1.2, 0], [0, -1.1, 0], [0, 0, 0]]))},
            "neck": {"rotation": key(zip(t, shake))},
            "neck2": {"rotation": key(zip(t, [rot(0), rot(-6), rot(-8), rot(-8), rot(-6), rot(0)]))},
            "head": {"rotation": key(zip(t, [rot(0), rot(-12), rot(-14), rot(-14), rot(-12), rot(0)]))},
            "jaw": {"rotation": key(zip(t, [rot(0), rot(40), rot(48), rot(48), rot(40), rot(0)]))},
            "arm_left": {"rotation": key(zip(t, [rot(0), rot(-20, 0, -25), rot(-22, 0, -28), rot(-22, 0, -28), rot(-20, 0, -25), rot(0)]))},
            "arm_right": {"rotation": key(zip(t, [rot(0), rot(-20, 0, 25), rot(-22, 0, 28), rot(-22, 0, 28), rot(-20, 0, 25), rot(0)]))},
            "tail1": {"rotation": key(zip(t, [rot(0), rot(10), rot(12), rot(12), rot(10), rot(0)]))},
            "tail2": {"rotation": key(zip(t, [rot(0), rot(4), rot(5), rot(5), rot(4), rot(0)]))},
        },
        "sound_effects": {"0.3000": {"effect": "odo.roar"}},
    }


def burnt():
    """Collapses onto its side and lies there, scorched (held at the end)."""
    t = [0.0, 0.6, 1.5]
    return {
        "animation_length": 1.5,
        "bones": {
            "root": {"rotation": key(zip(t, [rot(0), rot(0, 0, 30), rot(0, 0, 84)])),
                     "position": key(zip(t, [[0, 0, 0], [0, -2, 0], [0, 16.7, 0]]))},
            "body": {"position": key(zip(t, [[0, 0, 0], [0, -8, 0], [0, -21, 0]]))},
            "neck": {"rotation": key(zip(t, [rot(0), rot(10), rot(24, 0, -10)]))},
            "neck2": {"rotation": key(zip(t, [rot(0), rot(6), rot(12)]))},
            "head": {"rotation": key(zip(t, [rot(0), rot(-10), rot(-4)]))},
            "jaw": {"rotation": key(zip(t, [rot(0), rot(25), rot(14)]))},
            "thigh_left": {"rotation": key(zip(t, [rot(0), rot(-30), rot(-50, 0, 8)]))},
            "shin_left": {"rotation": key(zip(t, [rot(0), rot(30), rot(55)]))},
            "thigh_right": {"rotation": key(zip(t, [rot(0), rot(-15), rot(-30, 0, -6)]))},
            "shin_right": {"rotation": key(zip(t, [rot(0), rot(20), rot(40)]))},
            "arm_left": {"rotation": key(zip(t, [rot(0), rot(-20), rot(-40)]))},
            "arm_right": {"rotation": key(zip(t, [rot(0), rot(-10), rot(-30)]))},
            "tail1": {"rotation": key(zip(t, [rot(0), rot(4), rot(6, -6)]))},
            "tail2": {"rotation": key(zip(t, [rot(0), rot(4), rot(4, -8)]))},
            "tail3": {"rotation": key(zip(t, [rot(0), rot(2), rot(2, -8)]))},
        },
        "sound_effects": {"0.0000": {"effect": "odo.death"}},
    }


def write(path):
    anims = {
        "animation.odo.idle": idle(),
        "animation.odo.walk": locomotion(1.6, WALK_LEG, 0.0, "odo.step", 0.8),
        "animation.odo.run": locomotion(1.1, RUN_LEG, 12.0, "odo.step", 1.4),
        "animation.odo.bite": bite(),
        "animation.odo.stomp": stomp(),
        "animation.odo.roar": roar(),
        "animation.odo.burnt": burnt(),
    }
    with open(path, "w") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=1)
        f.write("\n")
