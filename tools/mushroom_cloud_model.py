"""Alex's Caves' mushroom cloud model (client/model/MushroomCloudModel.java by AlexModGuy, Citadel boxes), as data:
the same parts, pivots, rotations, boxes and texture offsets as client/render/NuclearCloud.java builds, and its
animation (MushroomCloudModel.animateParticle, MushroomCloudParticle.render). Used by tools/preview_cloud.py.

Units are model pixels (1/16 block at scale 1), y down, like any Minecraft entity model.
"""
import math

import numpy as np

TEX_SIZE = 512
BALL_FOR = 10          # ticks of fireball alone before the mushroom shows (MushroomCloudParticle.BALL_FOR)
Q = 0.7854             # 45 degrees, as the source writes it

# name: (parent, pivot, rotation (x, y, z) radians, [boxes (u, v, x, y, z, w, h, d, mirror)])
PARTS = {
    "mushroom_cloud": (None, (0.0, 24.0, 0.0), (0, 0, 0), []),
    "lightBall": ("mushroom_cloud", (0.0, -19.0, 0.0), (0, 0, 0), [(300, 0, -16, -16, -16, 32, 32, 32, False)]),
    "lowerCloud": ("lightBall", (0.0, 5.0, 0.0), (0, 0, 0), [(0, 0, -50, -9, -50, 100, 16, 100, False)]),
    "lowerCloud_planes": ("lowerCloud", (0.0, 0.0, 0.0), (0, 0, 0), []),
    "cube_r1": ("lowerCloud_planes", (-16.0, -20.0, 16.0), (0, Q, 0), [(142, 342, -66, -13, 0, 66, 40, 0, True)]),
    "cube_r2": ("lowerCloud_planes", (16.0, -20.0, -16.0), (0, Q, 0), [(142, 342, 0, -13, 0, 66, 40, 0, False)]),
    "cube_r3": ("lowerCloud_planes", (16.0, -20.0, 16.0), (0, -Q, 0), [(142, 342, 0, -13, 0, 66, 40, 0, False)]),
    "cube_r4": ("lowerCloud_planes", (-16.0, -20.0, -16.0), (0, -Q, 0), [(142, 342, -66, -13, 0, 66, 40, 0, True)]),
    "plume": ("lightBall", (0.0, 9.0, 0.0), (0, 0, 0), []),
    "cube_r5": ("plume", (0.0, -44.8333, 0.0), (0, -Q, 0), [(256, 220, -32, -61, 0, 64, 122, 0, False)]),
    "cube_r6": ("plume", (0.0, -44.8333, 0.0), (0, Q, 0), [(256, 220, -32, -61, 0, 64, 122, 0, False)]),
    "plumeBlast": ("plume", (0.0, -80.8333, 0.0), (0, 0, 0), [(240, 116, -18, 0, -18, 36, 29, 36, False)]),
    "plumeRing": ("plume", (0.0, -35.8333, 0.0), (0, 0, 0), [(296, 440, -36, 0, -36, 72, 0, 72, False)]),
    "upperCloud": ("plume", (0.0, -80.8333, 0.0), (0, 0, 0), [(0, 116, -40, -24, -40, 80, 24, 80, False)]),
    "upperCloud1": ("upperCloud", (0.0, -24.0, 0.0), (0, 0, 0), [(0, 220, -32, -15, -32, 64, 15, 64, False)]),
    "upperCloud_planes": ("upperCloud", (0.0, -12.0, 0.0), (0, 0, 0), []),
    "cube_r7": ("upperCloud_planes", (16.0, 112.0, -16.0), (0, Q, 0), [(0, 299, -23, -152, 0, 71, 69, 0, False)]),
    "cube_r8": ("upperCloud_planes", (16.0, 112.0, 16.0), (0, -Q, 0), [(0, 299, -23, -152, 0, 71, 69, 0, False)]),
    "cube_r9": ("upperCloud_planes", (-16.0, 112.0, 16.0), (0, Q, 0), [(0, 299, -48, -152, 0, 71, 69, 0, True)]),
    "cube_r10": ("upperCloud_planes", (-16.0, 112.0, -16.0), (0, -Q, 0), [(0, 299, -48, -152, 0, 71, 69, 0, True)]),
    "rings": ("upperCloud", (0.0, -55.0, 0.0), (0, 0, 0), []),
    "upperRing": ("rings", (0.0, -4.0, 0.0), (0, 0, 0), [(296, 440, -36, 0, -36, 72, 0, 72, False)]),
    "lowerRing": ("rings", (0.0, 4.0, 0.0), (0, 0, 0), [(296, 440, -36, 0, -36, 72, 0, 72, False)]),
}


def smin(a, b, k):
    """ACMath.smin: a smooth minimum."""
    h = max(k - abs(a - b), 0.0) / k
    return min(a, b) - h * h * k * 0.25


def clamp(x, lo, hi):
    return max(lo, min(hi, x))


def life_at(age, partial, lifetime):
    """MushroomCloudParticle.render's growth: fast at first, then slowing (negative during the fireball)."""
    x = (age - BALL_FOR + partial) / (lifetime - BALL_FOR)
    return math.log(1 + x) * 2.0 if x > -1 else -10.0


def pose(age, partial, lifetime):
    """Per-part (scale (x, y, z), extra y rotation, visible) at a moment: animateParticle + hideFireball."""
    life = life_at(age, partial, lifetime)
    lf = smin(life, 1.0, 0.5)
    lerped = age + partial
    base = lf + lf * lf
    upper = clamp(lf + 0.3, 0.4, 1.5)
    plume_ring = clamp(lf, 0.0, 0.5) * 45
    lower_ring = clamp(lf, 0.1, 0.65) * 20
    upper_ring = clamp(lf, 0.2, 0.65) * 15
    shown = age >= BALL_FOR
    p = {name: [(1.0, 1.0, 1.0), 0.0, True] for name in PARTS}
    p["plume"] = [(1.0, lf * 1.5, 1.0), 0.0, shown]
    p["upperCloud"] = [(upper, 1.0, upper), 0.0, shown]
    p["plumeRing"] = [(plume_ring, 1.0, plume_ring), lerped * 0.1, True]
    p["lowerRing"] = [(lower_ring, 1.0, lower_ring), -lerped * 0.2, True]
    p["upperRing"] = [(upper_ring, 1.0, upper_ring), lerped * 0.1, True]
    p["lowerCloud"] = [(base, base, base), 0.0, shown]
    life_now = life
    glow = 1.0 - life_now if life_now < 1.0 else 0.0
    return p, life, glow


def _rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def matrices(p):
    """Each part's 4x4 transform (model pixels in, blocks at scale 1 out, y still down), and whether it's shown."""
    out = {}

    def build(name):
        if name in out:
            return out[name]
        parent, pivot, rot, _ = PARTS[name]
        scale, extra_y, shown = p[name]
        M = np.eye(4)
        M[:3, 3] = np.array(pivot) / 16.0
        R = np.eye(4)
        R[:3, :3] = _rot(rot[0], rot[1] + extra_y, rot[2])
        S = np.diag([scale[0], scale[1], scale[2], 1.0])
        local = M @ R @ S
        if parent:
            pm, pshown = build(parent)
            out[name] = (pm @ local, pshown and shown)
        else:
            out[name] = (local, shown)
        return out[name]

    for n in PARTS:
        build(n)
    return out


def faces(box):
    """The six faces of a box (Minecraft's cube UV layout): [(corners (4, 3) in pixels, uvs (4, 2) in texels, normal)]."""
    u, v, x, y, z, w, h, d, mirror = box
    x0, y0, z0, x1, y1, z1 = x, y, z, x + w, y + h, z + d
    if mirror:
        x0, x1 = x1, x0
    P = {k: np.array(c, dtype=float) for k, c in {
        "a": (x0, y0, z0), "b": (x1, y0, z0), "c": (x1, y1, z0), "d": (x0, y1, z0),
        "e": (x0, y0, z1), "f": (x1, y0, z1), "g": (x1, y1, z1), "h": (x0, y1, z1)}.items()}
    U = [u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w]
    V = [v, v + d, v + d + h]
    # (vertices, u0, v0, u1, v1, normal): vanilla ModelPart.Cube's polygons
    defs = [
        (("f", "e", "a", "b"), U[1], V[0], U[2], V[1], (0, -1, 0)),   # down (the top, once the model is turned up)
        (("c", "d", "h", "g"), U[2], V[1], U[3], V[0], (0, 1, 0)),    # up
        (("a", "e", "h", "d"), U[0], V[1], U[1], V[2], (-1, 0, 0)),   # west
        (("b", "a", "d", "c"), U[1], V[1], U[2], V[2], (0, 0, -1)),   # north
        (("f", "b", "c", "g"), U[2], V[1], U[4], V[2], (1, 0, 0)),    # east
        (("e", "f", "g", "h"), U[4], V[1], U[5], V[2], (0, 0, 1)),    # south
    ]
    out = []
    for names, u0, v0, u1, v1, n in defs:
        corners = np.array([P[k] for k in names])
        uvs = np.array([[u1, v0], [u0, v0], [u0, v1], [u1, v1]], dtype=float)
        normal = np.array(n, dtype=float)
        if mirror:
            corners = corners[::-1]
            uvs = uvs[::-1]
            normal = normal * np.array([-1, 1, 1])
        out.append((corners, uvs, normal))
    return out


def quads(age, partial, lifetime):
    """Every face of the cloud at a moment, in model blocks (scale 1, y down): [(corners, uvs, normal, part)]."""
    p, life, glow = pose(age, partial, lifetime)
    mats = matrices(p)
    out = []
    for name, (_, _, _, boxes) in PARTS.items():
        M, shown = mats[name]
        if not shown:
            continue
        for box in boxes:
            for corners, uvs, normal in faces(box):
                c = (M @ np.c_[corners / 16.0, np.ones(4)].T).T[:, :3]
                n = M[:3, :3] @ normal
                n = n / max(np.linalg.norm(n), 1e-9)
                out.append((c, uvs, n, name))
    return out, life, glow
