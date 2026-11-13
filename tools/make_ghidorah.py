#!/usr/bin/env python3
"""
Builds King Ghidorah: his GeckoLib model, texture and animations, all the mod's own.

    python3 tools/make_ghidorah.py                 (writes the three files)
    python3 tools/make_ghidorah.py --preview DIR   (and pictures of him there: idle all round, and each animation)

An enormous, ancient three-headed dragon in angular pixel style: a broad armoured chest with massive shoulders and
three long segmented necks, a narrower waist, powerful hindquarters on two muscular hind legs with three-taloned feet,
two huge bat wings for forelimbs (hooked claws at the wrists, no arms), and two long tails ending in spiked maces.
His proportions follow "King Ghidorah" by I HATE YOU WINDOWS XP (Sketchfab, CC-BY 4.0), studied for its skeleton
and silhouette (nothing of it is copied: every cube, pixel and keyframe here is generated below).

Bedrock geometry: the front is -z, his right -x (named _r; _l is its mirror image), y up, in model units
(1/16 of a block before the renderer's scale). Nearly every bone is authored in place, in his bind pose (wings
spread, necks curved), with no bind rotation, so an animation's rotations turn about the world axes at each joint;
the heads, jaws and the wing membranes' bones are the exceptions (see head() and web()).

Texture: per-face UV, 2 texels per model unit on every face (512 x 512): the same density as Godzilla's skins
(256-unit sheets at 512 px), so the two read alike side by side. Cubes of the same shape and kind share their
texels, and the left side's are the right side's, mirrored.

Animations (animation.king_ghidorah.*): idle, walk, roar, bite_c / bite_r / bite_l (one head lunges, the others
stay busy), gravity_beam (necks brace, heads aim, staggered anticipation and recoil). mouth_c / mouth_r / mouth_l
are empty bones at the tips of his snouts, where his gravity beams come from.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_preview as rp  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'godzillaminusone')
GEO = os.path.join(ASSETS, 'geo', 'entity', 'king_ghidorah.geo.json')
TEXTURE = os.path.join(ASSETS, 'textures', 'entity', 'king_ghidorah.png')
ANIMS = os.path.join(ASSETS, 'animations', 'entity', 'king_ghidorah.animation.json')

TEX = 512          # the texture, square
DENSITY = 2        # texels per model unit
WEB_Z = 9.0        # the wings' plane (spread)

F = np.diag([-1.0, 1.0, 1.0])


def geo_rot(rx, ry, rz):
    """A Bedrock rotation (degrees) as a matrix in geo space (GeckoLib's: Rz Ry Rx, with x mirrored)."""
    return F @ rp.rot_matrix(math.radians(-rx), math.radians(-ry), math.radians(rz)) @ F


def euler(R):
    """The Bedrock rotation (degrees) of a rotation matrix in geo space."""
    G = F @ R @ F
    b = -math.asin(max(-1.0, min(1.0, G[2, 0])))
    a = math.atan2(G[2, 1], G[2, 2])
    c = math.atan2(G[1, 0], G[0, 0])
    return [round(-math.degrees(a), 3), round(-math.degrees(b), 3), round(math.degrees(c), 3)]


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


# ---------------------------------------------------------------- the model
class Model:
    def __init__(self):
        self.bones = []
        self.by_name = {}

    def bone(self, name, parent, pivot, rotation=None):
        b = {'name': name, 'pivot': [round(float(v), 3) for v in pivot], 'cubes': []}
        if parent:
            b['parent'] = parent
        if rotation and any(abs(r) > 1e-6 for r in rotation):
            b['rotation'] = [round(float(r), 3) for r in rotation]
        self.bones.append(b)
        self.by_name[name] = b
        return name

    def box(self, bone, origin, size, kind, rotation=None, pivot=None, grad=None, flat=None):
        """An axis-aligned box (origin = its least corner), or turned by rotation about pivot (its centre)."""
        c = {'origin': [round(float(v), 3) for v in origin], 'size': [round(float(v), 3) for v in size], 'kind': kind}
        if rotation and any(abs(r) > 1e-6 for r in rotation):
            c['rotation'] = [round(float(r), 3) for r in rotation]
            c['pivot'] = [round(float(v), 3) for v in (pivot if pivot is not None else
                                                     np.asarray(origin) + np.asarray(size) / 2)]
        if grad:
            c['grad'] = grad
        if flat:
            c['flat'] = flat
        self.by_name[bone]['cubes'].append(c)
        return c

    def cbox(self, bone, center, size, kind, rotation=None, **kw):
        center, size = np.asarray(center, float), np.asarray(size, float)
        return self.box(bone, center - size / 2, size, kind, rotation, center, **kw)

    def rod(self, bone, a, b, w, h=None, kind='hide', ext=0.0, across=(1.0, 0.0, 0.0), grad='z'):
        """A box from point a to point b (w wide along `across`, h thick), its length along its own z."""
        h = w if h is None else h
        a, b = np.asarray(a, float), np.asarray(b, float)
        d = unit(b - a)
        a, b = a - d * ext, b + d * ext
        x = np.asarray(across, float) - np.dot(across, d) * d
        if np.linalg.norm(x) < 1e-6:
            x = np.array([0.0, 0.0, 1.0]) - d[2] * d
        x = unit(x)
        y = np.cross(d, x)
        R = np.stack([x, y, d], 1)
        length = float(np.linalg.norm(b - a))
        return self.box(bone, a + np.array([-w / 2, -h / 2, 0.0]), (w, h, length), kind, euler(R), a, grad=grad)

    def spike(self, bone, base, tip, w, kind='spike', steps=3, h=None):
        """A tapering spike, stepped: a few rods from the base, each thinner and reaching further."""
        base, tip = np.asarray(base, float), np.asarray(tip, float)
        h = w if h is None else h
        fr = [(0.0, 0.62, 1.0), (0.3, 0.86, 0.62), (0.58, 1.0, 0.3)][:steps] if steps == 3 else \
            [(0.0, 0.7, 1.0), (0.4, 1.0, 0.5)]
        if steps == 1:
            fr = [(0.0, 1.0, 1.0)]
        for f0, f1, s in fr:
            self.rod(bone, base + (tip - base) * f0, base + (tip - base) * f1, w * s, h * s, kind,
                     ext=0.15 if f0 > 0 else 0.0, grad='spike')

    def mirror(self):
        """Every bone of his right side (_r) again on his left (_l), mirrored."""
        out = []
        for b in list(self.bones):
            n = b['name']
            if not (n.endswith('_r') or '_r_' in n):
                continue
            m = {'name': swap(n), 'pivot': [-b['pivot'][0], b['pivot'][1], b['pivot'][2]],
                 'cubes': [mirror_cube(c) for c in b['cubes']]}
            if 'parent' in b:
                m['parent'] = swap(b['parent'])
            if 'rotation' in b:
                rx, ry, rz = b['rotation']
                m['rotation'] = [rx, -ry, -rz]
            out.append(m)
        for m in out:
            self.bones.append(m)
            self.by_name[m['name']] = m

    def shift(self, dy):
        for b in self.bones:
            b['pivot'][1] = round(b['pivot'][1] + dy, 3)
            for c in b['cubes']:
                c['origin'][1] = round(c['origin'][1] + dy, 3)
                if 'pivot' in c:
                    c['pivot'] = [c['pivot'][0], round(c['pivot'][1] + dy, 3), c['pivot'][2]]


def swap(name):
    parts = name.split('_')
    return '_'.join('l' if p == 'r' else 'r' if p == 'l' else p for p in parts)


def chain(start, steps):
    """Points along a chain: steps = [(length, direction)]."""
    pts = [np.asarray(start, float)]
    for length, d in steps:
        pts.append(pts[-1] + unit(d) * length)
    return pts


def angles_dir(fwd, side=0.0, out=-1.0):
    """A direction tilted fwd degrees forward (-z) from straight up, and side degrees outwards (out: -x for _r)."""
    a, b = math.radians(fwd), math.radians(side)
    return unit((out * math.sin(b), math.cos(a) * math.cos(b), -math.sin(a) * math.cos(b)))


def flat_dir(pitch, yaw, back=1.0, out=-1.0):
    """A direction pitch degrees above level, yaw degrees outwards from straight back (+z)."""
    p, y = math.radians(pitch), math.radians(yaw)
    return unit((out * math.cos(p) * math.sin(y), math.sin(p), back * math.cos(p) * math.cos(y)))


# ---------------------------------------------------------------- his parts
NECKS = {
    # base, segment length, forward tilt per segment (degrees from upright), outward lean, segment widths
    'c': ((0.0, 57.0, -3.0), 5.9, [38, 31, 21, 9, -3, -8, 2, 24], [0] * 8),
    'r': ((-9.5, 55.0, -1.0), 5.7, [42, 33, 22, 10, -2, -7, 5, 26], [30, 25, 19, 12, 6, 2, -2, -6]),
}
HEAD_PITCH = 10.0
JAW_HINGE = (0.0, -1.0, -5.5)
MOUTH = (0.0, -1.0, -19.6)


def neck_points(side):
    base, length, fwd, lean = NECKS[side]
    return chain(base, [(length, angles_dir(f, s)) for f, s in zip(fwd, lean)])


def neck(m, side, parent):
    pts = neck_points(side)
    name = None
    for i in range(len(pts) - 1):
        name = m.bone('neck_%s_%d' % (side, i + 1), parent, pts[i])
        a, b = pts[i], pts[i + 1]
        d = unit(b - a)
        w = 7.8 - i * 0.36
        m.rod(name, a, b, w, w * 0.94, 'hide', ext=0.7)
        front = unit(np.cross(d, (1.0, 0.0, 0.0)))     # towards his throat
        mid = (a + b) / 2
        # overlapping golden throat plates, each a little wider than the neck below it
        m.rod(name, a + front * (w * 0.47 - 0.1), b + front * (w * 0.47 - 0.1) + d * 0.6, w * 0.82, 1.0, 'plate',
              grad='plate')
        # a scale ridge round the back of each segment, and a small dorsal spike on it
        back = -front
        m.rod(name, a + back * (w * 0.3), a + back * (w * 0.3) + d * (b - a).dot(d) * 0.55, w * 0.7, w * 0.72,
              'armor', ext=0.0)
        m.spike(name, mid + back * (w * 0.5), mid + back * (w * 0.5 + 2.2 - i * 0.12) + d * 1.6, 1.3, steps=2)
        parent = name
    return name, pts[-1], unit(pts[-1] - pts[-2])


def head(m, side, parent, at):
    """A head at the top of a neck, authored level and facing forward, then pitched by its bone; its jaw hinges
    open below it."""
    hx, hy, hz = at
    h = m.bone('head_%s' % side, parent, at, (HEAD_PITCH, 0, 0))
    P = np.array(at, float)

    def B(o, s, kind='hide', rot=None, **kw):
        o = np.asarray(o, float)
        c = m.box(h, P + o, s, kind, rot, P + o + np.asarray(s) / 2 if rot else None, **kw)
        return c

    # cranium, stepped down into the snout; the back of the skull into the neck
    B((-3.6, -1.6, -8.2), (7.2, 6.6, 8.6))
    B((-3.0, -1.2, 0.2), (6.0, 5.2, 2.6), 'armor')
    B((-2.7, -0.6, -17.0), (5.4, 3.8, 9.2))                       # upper snout
    B((-2.1, 2.9, -16.0), (4.2, 1.3, 8.4), 'armor')               # its bridge
    B((-2.25, -0.35, -20.2), (4.5, 3.2, 3.4))                     # snout tip
    B((-1.0, 3.9, -15.4), (2.0, 0.9, 7.0), 'armor', (-4, 0, 0))   # nose ridge
    B((-3.4, -1.3, -12.6), (6.8, 2.0, 5.6), 'armor')              # upper lip, widening into the cheeks
    B((-4.3, -1.7, -7.6), (8.6, 4.2, 5.0))                        # cheeks
    B((-2.3, -0.75, -19.6), (4.6, 0.3, 12.2), 'mouth')            # palate (seen when he opens up)
    # heavy sloping brows, a crest between them, sunken amber eyes
    B((-4.4, 3.4, -11.6), (3.2, 2.2, 6.6), 'armor', (12, -6, 0))
    B((-1.3, 4.6, -9.2), (2.6, 1.3, 8.4), 'armor')
    B((-3.75, 1.4, -10.4), (1.0, 1.7, 2.4), 'socket')
    B((-3.95, 1.9, -10.0), (0.45, 0.8, 1.5), 'eye')
    # nostrils
    B((-1.7, 1.9, -20.35), (0.8, 0.6, 0.9), 'nostril')
    # two backward-sweeping horns
    m.spike(h, P + (-2.3, 4.4, -3.0), P + (-3.4, 8.0, 8.5), 2.3, 'horn')
    m.spike(h, P + (-3.1, 6.9, 5.2), P + (-4.2, 12.4, 14.5), 1.4, 'horn', steps=2)
    # crown spikes, along the back of the skull and the brow
    m.spike(h, P + (-0.0, 5.2, -5.0), P + (0.0, 9.0, -0.5), 1.5, 'horn')
    m.spike(h, P + (-1.4, 5.0, -1.0), P + (-1.8, 8.0, 3.5), 1.2, 'horn')
    m.spike(h, P + (-3.3, 4.6, -6.5), P + (-5.2, 7.2, -3.5), 1.0, 'horn', steps=2)
    m.spike(h, P + (-3.6, 3.8, 0.0), P + (-6.4, 5.4, 3.6), 1.1, 'horn', steps=2)
    # cheek projections
    m.spike(h, P + (-4.1, 0.6, -5.4), P + (-7.6, -0.6, -0.6), 1.6, 'horn')
    m.spike(h, P + (-3.8, -1.0, -3.6), P + (-6.2, -2.8, 0.6), 1.1, 'horn', steps=2)
    # teeth: along the upper lip, and two at the front
    for k, z in enumerate((-18.2, -15.6, -13.0, -10.4)):
        m.rod(h, P + (-2.05, -0.5, z), P + (-2.1, -2.0 + 0.1 * k, z + 0.35), 0.75, kind='tooth', grad='spike')
    m.rod(h, P + (-0.9, -0.4, -19.8), P + (-0.9, -1.9, -19.6), 0.7, kind='tooth', grad='spike')
    # his jaw: it hinges below the cheeks
    J = P + np.asarray(JAW_HINGE)
    j = m.bone('jaw_%s' % side, h, J)

    def JB(o, s, kind='hide', rot=None):
        o = np.asarray(o, float)
        return m.box(j, P + o, s, kind, rot, P + o + np.asarray(s) / 2 if rot else None)

    JB((-2.4, -3.5, -19.6), (4.8, 2.4, 13.8))                     # the lower jaw
    JB((-3.5, -3.9, -8.4), (7.0, 3.0, 5.6))                       # wider at the back, under the cheeks
    JB((-1.9, -1.3, -18.8), (3.8, 0.3, 11.6), 'mouth')            # the floor of the mouth
    JB((-1.6, -4.3, -18.6), (3.2, 1.0, 7.2), 'armor')             # chin
    for k, z in enumerate((-17.4, -14.8, -12.2, -9.6)):
        m.rod(j, P + (-1.95, -1.25, z), P + (-1.9, 0.15 - 0.1 * k, z - 0.3), 0.65, kind='tooth', grad='spike')
    m.rod(j, P + (-0.7, -1.2, -19.0), P + (-0.7, 0.2, -19.2), 0.65, kind='tooth', grad='spike')
    # a jagged beard of spikes under the jaw
    m.spike(j, P + (-1.0, -4.2, -16.0), P + (-1.3, -7.0, -13.2), 1.2)
    m.spike(j, P + (-1.9, -4.2, -11.5), P + (-2.6, -8.4, -8.0), 1.4)
    m.spike(j, P + (-2.9, -4.4, -7.0), P + (-3.8, -8.8, -2.6), 1.5)
    m.spike(j, P + (0.0, -4.6, -13.6), P + (0.0, -7.6, -10.6), 1.2)
    m.spike(j, P + (-1.0, -4.5, -8.0), P + (-1.1, -9.6, -4.4), 1.3)
    m.bone('mouth_%s' % side, h, P + np.asarray(MOUTH))
    return h


def mirror_cube(c, cx=0.0):
    """A cube mirrored about x = cx (its texels: the original's, mirrored)."""
    ox, oy, oz = c['origin']
    mc = dict(c)
    mc['origin'] = [round(2 * cx - (ox + c['size'][0]), 3), oy, oz]
    if 'rotation' in c:
        rx, ry, rz = c['rotation']
        mc['rotation'] = [rx, round(-ry, 3), round(-rz, 3)]
        px, py, pz = c['pivot']
        mc['pivot'] = [round(2 * cx - px, 3), py, pz]
    mc['mirror_of'] = c
    return mc


def symmetric(m, bone, cx):
    """Mirror a bone's right-hand cubes (those right of x = cx) onto its left."""
    b = m.by_name[bone]
    extra = []
    for c in b['cubes']:
        mid = c['pivot'][0] if 'rotation' in c else c['origin'][0] + c['size'][0] / 2
        if mid < cx - 0.3:
            extra.append(mirror_cube(c, cx))
    b['cubes'] += extra


# the wings, spread (points in the wings' plane, z = WEB_Z)
SHOULDER = (-13.0, 56.0, 7.0)
ELBOW = (-38.0, 66.0, WEB_Z)
WRIST = (-66.0, 77.0, WEB_Z)
TIPS = [(-110.0, 63.0, WEB_Z), (-98.0, 37.0, WEB_Z), (-74.0, 27.0, WEB_Z)]
TRAIL = (-42.0, 28.0, WEB_Z)        # the trailing edge below his elbow
FLANK = (-15.0, 33.0, WEB_Z)        # where the membrane meets his side


def scallops(a, b, n, depth):
    """A scalloped edge from a to b (2-D): n arcs bowing in by depth (towards +y, the wing's inside)."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    d = b - a
    normal = unit((-d[1], d[0]))
    pts = []
    for i in range(n):
        for k in range(1, 7):
            f = (i + k / 7) / n
            s = math.sin(math.pi * k / 7)
            pts.append(a + d * f + normal * depth * s)
        pts.append(a + d * (i + 1) / n)
    return pts


def web(m, name, parent, pivot, polygon):
    """A membrane panel: a flat, double-sided sheet, authored where it lies with the wing spread, on two bones
    pivoting at `pivot`: wing_webN (turned and scaled) holding wing_webN_skin (turned), so that any wing pose can
    stretch it, exactly, between the bones it hangs from (solve_webs)."""
    outer = m.bone(name, parent, pivot)
    skin = m.bone(name.replace('_r', '_skin_r'), outer, pivot)
    xs, ys = [q[0] for q in polygon], [q[1] for q in polygon]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    c = m.box(skin, (x0, y0, WEB_Z), (x1 - x0, y1 - y0, 0.0), 'membrane')
    c['polygon'] = [(q[0], q[1]) for q in polygon]
    return outer


def wing(m, parent):
    S, E, W = map(np.array, (SHOULDER, ELBOW, WRIST))
    sh = m.bone('wing_shoulder_r', parent, S)
    m.cbox(sh, S + (-1.0, 0.5, 0.5), (8.0, 8.0, 9.0), 'armor')
    m.cbox(sh, S + (-2.0, 3.0, 0.0), (7.0, 3.0, 10.0), 'armor', (0, 0, -10))
    up = m.bone('wing_upper_r', sh, S)
    Se = np.array([S[0], S[1], WEB_Z])
    m.rod(up, S, E, 5.2, 4.6, 'bone', ext=1.0)
    m.rod(up, S + (0, 2.4, 0), E + (0, 2.6, 0), 3.4, 3.0, 'armor', ext=0.5)            # armoured leading edge
    for f in (0.25, 0.55, 0.85):                                                      # its plates
        p = S + (E - S) * f + (0, 3.6, 0)
        m.rod(up, p, p + unit(E - S) * 6.0 + (0, 0.6, 0), 2.6, 1.6, 'armor')
    fo = m.bone('wing_fore_r', up, E)
    m.cbox(fo, E, (5.2, 5.2, 5.2), 'armor')
    m.rod(fo, E, W, 4.0, 3.8, 'bone', ext=0.6)
    m.rod(fo, E + (0, 2.2, 0), W + (0, 2.2, 0), 2.6, 2.4, 'armor', ext=0.3)
    m.spike(fo, E + (0, 1.0, 1.2), E + (4.0, 2.0, 5.5), 1.3, 'claw', steps=2)          # a spur at the elbow
    ha = m.bone('wing_hand_r', fo, W)
    m.cbox(ha, W, (5.4, 5.0, 5.0), 'armor')
    cl = m.bone('wing_claw_r', ha, W + (0, 2.0, -1.5))
    # the hooked claw at the wrist: up and forward, then hooked down
    k1 = W + (-1.0, 6.0, -3.5)
    m.rod(cl, W + (0, 2.0, -1.5), k1, 2.0, 1.8, 'claw')
    m.spike(cl, k1, k1 + (-0.6, 1.0, -3.6), 1.6, 'claw', steps=2)
    m.spike(cl, k1 + (-0.5, 0.9, -3.3), k1 + (-0.8, -1.6, -5.6), 1.0, 'claw', steps=2)
    fingers = []
    for i, T in enumerate(TIPS):
        T = np.array(T)
        f = m.bone('wing_finger%d_r' % (i + 1), ha, W)
        m.rod(f, W, W + (T - W) * 0.5, 2.8, 2.6, 'bone', ext=0.5)
        m.rod(f, W + (T - W) * 0.45, W + (T - W) * 0.85, 2.0, 1.9, 'bone')
        m.rod(f, W + (T - W) * 0.8, T, 1.3, 1.2, 'bone')
        m.cbox(f, W + (T - W) * 0.5, (2.9, 2.9, 2.9), 'armor')                        # a knuckle
        m.spike(f, T - unit(T - W) * 1.5, T + unit(T - W) * 3.0, 1.3, 'claw', steps=2)
        fingers.append(f)
    # the membranes: finger to finger, the last finger to the elbow, and the arm to his flank
    T1, T2, T3 = (np.array(t[:2]) for t in TIPS)
    W2, E2, S2 = W[:2], E[:2], np.array(SHOULDER[:2])
    web(m, 'wing_web1_r', ha, W, [W2, T1] + scallops(T1, T2, 2, 5.0))
    web(m, 'wing_web2_r', ha, W, [W2, T2] + scallops(T2, T3, 2, 4.5))
    web(m, 'wing_web3_r', ha, W, [W2, T3] + scallops(T3, np.array(TRAIL[:2]), 2, 3.8) + [E2])
    web(m, 'wing_web4_r', parent, Se, [S2, E2, np.array(TRAIL[:2])] + scallops(np.array(TRAIL[:2]), np.array(FLANK[:2]), 2, 3.4))


def leg(m, parent):
    hip, knee, ankle, ball = map(np.array, ((-9.5, 30.0, 8.0), (-11.5, 17.0, 0.5), (-11.5, 8.0, 7.5),
                                           (-11.5, 2.6, 2.0)))
    th = m.bone('leg_thigh_r', parent, hip)
    m.rod(th, hip + (0, 2.5, 0), knee, 10.0, 11.0, 'hide', ext=1.5)
    m.rod(th, hip + (-4.6, 1.0, 0.0), knee + (-4.2, 3.0, 0.5), 9.0, 2.4, 'armor', across=(0, 0, 1))  # outer plate
    m.rod(th, hip + (-1.0, 0.5, -4.6), knee + (0, 1.5, -2.6), 7.0, 3.0, 'armor')       # its front
    sh = m.bone('leg_shin_r', th, knee)
    m.cbox(sh, knee, (7.0, 6.5, 7.0), 'armor')
    m.rod(sh, knee, ankle, 6.6, 6.2, 'hide', ext=1.0)
    m.rod(sh, knee + (0, 0, -2.4), ankle + (0, 0.5, -2.0), 4.0, 2.0, 'armor')
    ft = m.bone('leg_foot_r', sh, ankle)
    m.rod(ft, ankle, ball, 5.2, 5.0, 'hide', ext=1.0)
    m.box(ft, ball + (-3.8, -2.6, -3.5), (7.6, 3.8, 9.0), 'hide')
    m.spike(ft, ball + (0, -1.4, 3.8), ball + (0, -2.2, 8.4), 1.6, 'claw', steps=2)    # the spur behind
    to = m.bone('leg_toes_r', ft, ball + (0, -1.0, -3.0))
    for dx, spread in ((-2.6, -14.0), (0.0, 0.0), (2.6, 14.0)):
        base = ball + (dx, -1.2, -3.0)
        d = np.array([math.sin(math.radians(spread)), 0.0, -math.cos(math.radians(spread))])
        end = base + d * 6.2 + (0, -0.2, 0)
        m.rod(to, base, end, 2.5, 2.3, 'hide', ext=0.6)
        m.rod(to, base + d * 2.0 + (0, 0.9, 0), end + (0, 0.7, 0), 2.0, 0.9, 'armor')
        m.spike(to, end - d * 0.8 + (0, 0.2, 0), end + d * 3.4 + (0, -1.6, 0), 1.7, 'claw', steps=2)


TAIL_L = [8.0, 8.0, 7.5, 7.5, 7.0, 7.0, 6.5, 6.0, 6.0, 5.5]
TAIL_PITCH = [-40, -32, -24, -16, -10, -6, -3, -1, 1, 3]
TAIL_YAW = [8, 10, 12, 13, 14, 15, 15, 14, 12, 10]


def tail(m, parent):
    pts = chain((-3.6, 27.0, 13.0), [(L, flat_dir(p, y)) for L, p, y in zip(TAIL_L, TAIL_PITCH, TAIL_YAW)])
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        name = m.bone('tail_r_%d' % (i + 1), parent, a)
        w = 8.4 - i * 0.55
        d = unit(b - a)
        up = unit(np.cross(d, (1.0, 0.0, 0.0)))
        m.rod(name, a, b, w, w * 0.88, 'hide', ext=0.8)
        m.rod(name, a + up * (w * 0.3), a + up * (w * 0.3) + d * TAIL_L[i] * 0.6, w * 0.74, w * 0.6, 'armor')
        m.rod(name, a - up * (w * 0.42), b - up * (w * 0.42), w * 0.66, 0.9, 'plate', grad='plate')
        mid = (a + b) / 2
        m.spike(name, mid + up * (w * 0.42), mid + up * (w * 0.42 + 2.6 - i * 0.12) + d * 2.0, 1.5 - i * 0.05,
                steps=2)
        parent = name
    # the mace at its tip, and its spikes
    a, b = pts[-2], pts[-1]
    d = unit(b - a)
    tip = m.bone('tail_r_tip', parent, b)
    up = np.array([0.0, 1.0, 0.0])
    side = unit(np.cross(up, d))
    m.rod(tip, b - d * 0.5, b + d * 5.4, 5.0, 4.2, 'armor')
    m.rod(tip, b + d * 0.5, b + d * 4.4, 6.0, 2.6, 'hide')
    m.spike(tip, b + d * 2.6 + up * 1.8, b + d * 4.6 + up * 6.8, 1.9)
    m.spike(tip, b + d * 2.4 + side * 2.6, b + d * 4.0 + side * 7.2 + up * 0.6, 1.8)
    m.spike(tip, b + d * 2.4 - side * 2.6, b + d * 4.0 - side * 7.2 + up * 0.6, 1.8)
    m.spike(tip, b + d * 5.0, b + d * 10.4 + up * 0.8, 2.0)
    m.spike(tip, b + d * 1.6 - up * 1.6, b + d * 3.6 - up * 5.2, 1.4, steps=2)


def body(m):
    root = m.bone('root', None, (0, 0, 0))
    pelvis = m.bone('pelvis', root, (0, 30, 7))
    m.box(pelvis, (-9.5, 23.0, -1.0), (19.0, 13.0, 16.0), 'hide')
    m.box(pelvis, (-11.0, 26.0, 2.0), (22.0, 9.0, 11.0), 'armor')                      # haunches
    m.cbox(pelvis, (0, 31.0, 14.5), (14.0, 9.0, 6.0), 'armor', (-20, 0, 0))            # over the tails' roots
    m.cbox(pelvis, (-5.5, 28.0, 15.5), (6.0, 7.0, 7.0), 'hide', (-30, 0, 0))
    symmetric(m, pelvis, 0.0)
    for k in range(3):                                                                  # plates down his back
        m.cbox(pelvis, (0, 36.0 - k * 0.6, 3.0 + k * 4.2), (12.0 - k, 2.2, 5.2), 'armor', (-12 - k * 4, 0, 0))
    torso = m.bone('torso', pelvis, (0, 34, 6))
    m.cbox(torso, (0, 39.5, 4.5), (19.0, 12.0, 15.0), 'hide', (10, 0, 0))
    m.cbox(torso, (0, 38.5, -3.3), (14.0, 11.0, 2.0), 'plate', (10, 0, 0), grad='belly')  # belly plates
    for k in range(3):
        m.cbox(torso, (0, 45.0 - k * 4.0, 11.2 + k * 0.5), (14.0, 2.4, 6.0), 'armor', (-28, 0, 0))
    chest = m.bone('chest', torso, (0, 44, 4))
    m.cbox(chest, (0, 50.0, 2.0), (31.0, 15.0, 19.0), 'hide', (14, 0, 0))
    m.cbox(chest, (0, 47.5, -8.2), (20.0, 13.0, 5.0), 'hide', (14, 0, 0))                # the breast
    m.cbox(chest, (0, 47.0, -10.9), (16.0, 12.0, 1.2), 'plate', (14, 0, 0), grad='belly')
    m.cbox(chest, (0, 54.5, 9.0), (22.0, 6.0, 14.0), 'armor', (8, 0, 0))                # the hump of his back
    m.cbox(chest, (-16.5, 52.5, 2.5), (10.0, 12.0, 15.0), 'hide', (14, 0, -6))          # shoulders
    m.cbox(chest, (-18.0, 57.0, 2.5), (9.0, 4.0, 16.0), 'armor', (14, 0, -16))         # their plates
    m.cbox(chest, (-20.6, 53.0, 2.5), (5.0, 6.0, 14.0), 'armor', (14, 0, -32))
    m.cbox(chest, (-11.0, 44.0, -3.0), (6.0, 8.0, 9.0), 'armor', (14, 0, 8))           # the pectorals' edge
    for k in range(4):                                                                  # overlapping back plates
        m.cbox(chest, (-6.0, 56.5 - k * 3.4, 12.0 + k * 1.6), (8.0, 2.4, 8.0), 'armor', (-36, 0, -10))
    symmetric(m, chest, 0.0)
    # collars where his necks meet him
    m.cbox(chest, (0, 56.0, -2.4), (10.0, 5.0, 10.0), 'armor', (24, 0, 0))
    m.cbox(chest, (-9.0, 54.0, -0.8), (9.6, 5.0, 9.6), 'armor', (24, 0, 12))
    m.cbox(chest, (9.0, 54.0, -0.8), (9.6, 5.0, 9.6), 'armor', (24, 0, -12))
    # the cluster of spear spikes on his upper back, between and behind his necks (not quite symmetrical)
    sp = m.bone('back_spikes', chest, (0, 56, 6))
    for base, tip, w in (((0.0, 57.5, 4.0), (0.6, 75.0, 14.5), 3.0),
                         ((-4.2, 57.0, 5.5), (-8.0, 72.5, 15.0), 2.7),
                         ((4.4, 57.0, 5.0), (8.6, 74.0, 13.5), 2.8),
                         ((-1.8, 56.5, 8.6), (-3.6, 69.0, 21.0), 2.5),
                         ((2.2, 56.2, 9.2), (4.4, 67.5, 22.0), 2.4),
                         ((-7.4, 55.6, 8.0), (-14.0, 64.5, 17.6), 2.2),
                         ((7.6, 55.4, 8.6), (13.4, 63.0, 18.6), 2.1),
                         ((0.0, 55.2, 12.0), (-0.8, 63.0, 23.5), 2.2),
                         ((-3.0, 54.0, 13.4), (-5.4, 59.6, 23.0), 1.7),
                         ((3.4, 53.6, 13.0), (6.6, 59.0, 22.4), 1.6)):
        m.spike(sp, base, tip, w)
    return pelvis, torso, chest


def build():
    m = Model()
    pelvis, torso, chest = body(m)
    for side in ('c', 'r'):
        top, at, d = neck(m, side, chest)
        head(m, side, top, at)
    symmetric(m, 'head_c', 0.0)
    symmetric(m, 'jaw_c', 0.0)
    symmetric(m, 'head_r', m.by_name['head_r']['pivot'][0])
    symmetric(m, 'jaw_r', m.by_name['head_r']['pivot'][0])
    wing(m, chest)
    leg(m, pelvis)
    tail(m, pelvis)
    m.mirror()
    # lowest point to the ground
    return m


def world_points(m, pose=None):
    """Every cube corner, posed, in geo space (for bounds)."""
    mats = rp.bone_matrices(m.bones, pose)
    pts = []
    for b in m.bones:
        M = mats[b['name']]
        for c in b['cubes']:
            C = M
            if 'rotation' in c:
                px, py, pz = c['pivot']
                rx, ry, rz = c['rotation']
                C = M @ rp.about_pivot([-px, py, pz], rp.rot_matrix(math.radians(-rx), math.radians(-ry),
                                                                      math.radians(rz)))
            ox, oy, oz = c['origin']
            w, h, d = c['size']
            for cx in (ox, ox + w):
                for cy in (oy, oy + h):
                    for cz in (oz, oz + d):
                        p = C @ np.array([-cx, cy, cz, 1.0])
                        pts.append((-p[0], p[1], p[2]))
    return np.array(pts)


# ---------------------------------------------------------------- texture
# (geo space) the corners of each face: top-left, top-right, bottom-right, bottom-left, as GeckoLib maps its UVs
def face_corners(c):
    (ox, oy, oz), (w, h, d) = c['origin'], c['size']
    X0, X1, Y0, Y1, Z0, Z1 = ox, ox + w, oy, oy + h, oz, oz + d
    return {
        'north': [(X0, Y1, Z0), (X1, Y1, Z0), (X1, Y0, Z0), (X0, Y0, Z0)],
        'south': [(X1, Y1, Z1), (X0, Y1, Z1), (X0, Y0, Z1), (X1, Y0, Z1)],
        'east': [(X0, Y1, Z1), (X0, Y1, Z0), (X0, Y0, Z0), (X0, Y0, Z1)],
        'west': [(X1, Y1, Z0), (X1, Y1, Z1), (X1, Y0, Z1), (X1, Y0, Z0)],
        'up': [(X0, Y1, Z1), (X1, Y1, Z1), (X1, Y1, Z0), (X0, Y1, Z0)],
        'down': [(X0, Y0, Z0), (X1, Y0, Z0), (X1, Y0, Z1), (X0, Y0, Z1)],
    }


NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (-1, 0, 0), 'west': (1, 0, 0), 'up': (0, 1, 0),
           'down': (0, -1, 0)}


def texels(n):
    return max(1, int(round(n * DENSITY)))


def face_sizes(c):
    w, h, d = c['size']
    if d == 0:
        return {'north': (texels(w), texels(h))}
    return {'north': (texels(w), texels(h)), 'south': (texels(w), texels(h)), 'east': (texels(d), texels(h)),
            'west': (texels(d), texels(h)), 'up': (texels(w), texels(d)), 'down': (texels(w), texels(d))}


def original(c):
    """The cube whose texels a cube uses, and whether they're mirrored."""
    flip = False
    while 'mirror_of' in c:
        c, flip = c['mirror_of'], not flip
    return c, flip


def share_key(c):
    if c['kind'] == 'membrane':
        return id(c)
    return (c['kind'], c.get('grad'), tuple(round(v, 2) for v in c['size']))


def layout(m):
    """Give every original cube's faces a place on the sheet (shelves, tallest first); shapes repeat share."""
    shared, rects = {}, []
    for b in m.bones:
        for c in b['cubes']:
            if 'mirror_of' in c:
                continue
            k = share_key(c)
            if k in shared:
                c['uv_of'] = shared[k]
                continue
            shared[k] = c
            for face, (w, h) in face_sizes(c).items():
                rects.append((h, w, c, face))
    rects.sort(key=lambda r: (-r[0], -r[1]))
    x = y = shelf = 0
    for h, w, c, face in rects:
        if x + w > TEX:
            x, y, shelf = 0, y + shelf, 0
        if y + h > TEX:
            raise SystemExit('the texture is full (at y %d of %d)' % (y, TEX))
        c.setdefault('rects', {})[face] = (x, y, w, h)
        x += w
        shelf = max(shelf, h)
    return y + shelf


def uv_of(c):
    """A cube's per-face UV (its texels, or its original's, mirrored for a mirrored cube)."""
    o, flip = original(c)
    o = o.get('uv_of', o)
    out = {}
    for face in face_sizes(c):
        src = face
        if flip and face in ('east', 'west'):
            src = 'west' if face == 'east' else 'east'
        if src == 'south' and 'south' not in o['rects']:          # a membrane's back: its front, mirrored
            x, y, w, h = o['rects']['north']
            out[face] = {'uv': [x + w, y], 'uv_size': [-w, h]}
            continue
        x, y, w, h = o['rects'][src]
        if flip:
            out[face] = {'uv': [x + w, y], 'uv_size': [-w, h]}
        else:
            out[face] = {'uv': [x, y], 'uv_size': [w, h]}
    if c['kind'] == 'membrane':
        out['south'] = {'uv': [out['north']['uv'][0] + out['north']['uv_size'][0], out['north']['uv'][1]],
                        'uv_size': [-out['north']['uv_size'][0], out['north']['uv_size'][1]]}
    return out


def vnoise(p, scale, seed):
    """Smooth value noise in [0, 1] at points p (..., 3), features about `scale` units apart."""
    q = np.asarray(p, float) / scale
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)

    def h(ix, iy, iz):
        n = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
        n = (n ^ (n >> 13)) * 1274126177
        return ((n ^ (n >> 16)) & 0xffff) / 65535.0

    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                wgt = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * \
                      (f[..., 2] if dz else 1 - f[..., 2])
                out = out + wgt * h(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz)
    return out


def hexc(s):
    return np.array([int(s[i:i + 2], 16) for i in (1, 3, 5)], float) / 255.0


GOLD_HI, GOLD, OCHRE, BRONZE, OLIVE, CHAR = (hexc(c) for c in ('#e3c062', '#c19637', '#a8782c', '#8a632c',
                                                               '#6a5a2c', '#2b241d'))
IVORY, HORN_TIP = hexc('#e8dcb6'), hexc('#c9b27a')


def mix(a, b, t):
    t = np.asarray(t, float)[..., None]
    return a * (1 - t) + b * t


def palette_skin(wp, seed):
    """Weathered gold, tarnished bronze, ochre and olive-brown, in blotches over him (world space)."""
    n1 = vnoise(wp, 11.0, seed)
    n2 = vnoise(wp, 3.5, seed + 1)
    n3 = vnoise(wp, 6.0, seed + 2)
    col = mix(BRONZE, OCHRE, np.clip(n1 * 1.7 - 0.35, 0, 1))
    col = mix(col, GOLD, np.clip(n2 * 1.6 - 0.75, 0, 1) * 0.7)
    col = mix(col, OLIVE, np.clip(0.62 - n3, 0, 1) * 1.3)
    col = mix(col, CHAR, np.clip(0.3 - n2, 0, 1) * 0.8)
    return col


def paint_face(c, face, rect, world, normal, img, alpha, rng):
    x, y, w, h = rect
    TL, TR, BR, BL = (np.array(p, float) for p in face_corners(c)[face])
    a = (np.arange(w) + 0.5) / w
    b = (np.arange(h) + 0.5) / h
    A, Bm = np.meshgrid(a, b)
    lp = TL + A[..., None] * (TR - TL) + Bm[..., None] * (BL - TL)      # local points (geo, unturned)
    wp = world(lp)                                                    # in the bind pose
    (ox, oy, oz), (sw, sh, sd) = c['origin'], c['size']
    rel = (lp - np.array([ox, oy, oz])) / np.maximum(np.array([sw, sh, sd]), 1e-6)
    kind, grad = c['kind'], c.get('grad')
    seed = 7
    ny = normal[1]
    light = 0.8 + 0.2 * max(0.0, ny) - 0.25 * max(0.0, -ny)
    jit = rng.random((h, w))
    # texel rows/columns of the face, for pixel patterns
    I, J = np.meshgrid(np.arange(w), np.arange(h))
    if kind == 'membrane':
        col = mix(hexc('#3c2219'), hexc('#552a25'), vnoise(wp, 7.0, 3))
        col = mix(col, hexc('#29160f'), np.clip(vnoise(wp, 2.2, 4) - 0.45, 0, 1) * 1.2)
        # veins, radiating from the wing's bones, and the folds between them
        poly = np.array(c['polygon'])
        piv = poly[0]
        ang = np.arctan2(lp[..., 1] - piv[1], lp[..., 0] - piv[0])
        dist = np.hypot(lp[..., 1] - piv[1], lp[..., 0] - piv[0])
        wav = np.sin(ang * 26 + np.sin(dist * 0.25) * 0.8)
        col = mix(col, hexc('#22100f'), np.clip(np.abs(wav) < 0.12, 0, 1) * 0.7)
        col = mix(col, hexc('#6d3330'), np.clip(wav - 0.7, 0, 1) * 1.3)
        ring = np.sin(dist * 0.7) * 0.5 + 0.5
        col *= (0.9 + 0.12 * ring)[..., None]
        inside, edge = polygon_mask(poly, lp[..., 0], lp[..., 1])
        col = mix(col, hexc('#1c0d0c'), np.clip(1.6 - edge, 0, 1) * 0.8)   # a dark hem
        img[y:y + h, x:x + w] = col
        alpha[y:y + h, x:x + w] = inside
        return
    if kind in ('spike', 'horn', 'claw', 'tooth'):
        t = rel[..., 2] if grad == 'spike' or grad == 'z' else rel[..., 1]
        streak = vnoise(np.stack([lp[..., 0] * 3, lp[..., 1] * 3, lp[..., 2] * 0.3], -1), 1.0, 11)
        if kind == 'spike':
            col = mix(mix(CHAR, BRONZE, np.clip(t * 2.2, 0, 1)), GOLD, np.clip(t * 1.6 - 0.6, 0, 1))
        elif kind == 'horn':
            col = mix(mix(CHAR * 1.2, BRONZE, np.clip(t * 2.4, 0, 1)), HORN_TIP, np.clip(t * 1.4 - 0.55, 0, 1))
        elif kind == 'claw':
            col = mix(hexc('#5a4632'), hexc('#1f1a15'), np.clip(t * 1.3, 0, 1))
        else:
            col = mix(hexc('#b89a5c'), IVORY, np.clip(t * 2.0, 0, 1))
        col *= (0.88 + 0.22 * streak)[..., None]
        col *= light
    elif kind in ('eye',):
        col = np.broadcast_to(hexc('#f2a51c'), (h, w, 3)).copy()
        col[h // 2, w // 2] = hexc('#ffe27a')
        if w > 2:
            col[:, 0] = hexc('#8a4a0c')
    elif kind == 'nostril':
        col = np.broadcast_to(hexc('#3a2616'), (h, w, 3)).copy()
    elif kind == 'socket':
        col = np.broadcast_to(hexc('#1d1712'), (h, w, 3)).copy() * (0.9 + 0.2 * jit[..., None])
    elif kind == 'mouth':
        col = mix(hexc('#5c1a17'), hexc('#7d2b24'), vnoise(wp, 1.5, 5))
        col *= (0.85 + 0.2 * jit)[..., None]
    else:
        col = palette_skin(wp, seed)
        fid = (x * 7919 + y * 104729) & 0xffff
        if kind == 'bone':
            col = mix(col, OLIVE, 0.4)
        if kind in ('hide', 'bone'):
            # small scales: 4 x 3 texels, rows offset by half a scale, each its own tone, lit at its top-left
            # corner and shadowed along its foot
            cy = J // 3
            cx = (I + (cy % 2) * 2) // 4
            tone = cell_hash(cx, cy, fid)
            col *= (0.84 + 0.3 * tone)[..., None]
            lx, ly = (I + (cy % 2) * 2) % 4, J % 3
            col = mix(col, GOLD_HI, ((ly == 0) & (lx == 0)) * 0.35)
            col = mix(col, CHAR, (ly == 2) * 0.32 + ((ly == 2) & (lx == 3)) * 0.25)
        if kind == 'armor':
            # overlapping plates, 6 x 4 texels: a lighter raised edge along each one's top, a charcoal seam at
            # its foot, each its own shade of gold and bronze
            cy = J // 4
            cx = (I + (cy % 2) * 3) // 6
            tone = cell_hash(cx, cy, fid + 1)
            col = mix(col, mix(OCHRE, GOLD, tone), 0.45) * (0.86 + 0.24 * tone)[..., None]
            lx, ly = (I + (cy % 2) * 3) % 6, J % 4
            col = mix(col, GOLD_HI, (ly == 0) * 0.5)
            col = mix(col, CHAR, (ly == 3) * 0.6 + ((lx == 5) & (ly > 0)) * 0.3)
            col = mix(col, CHAR, (jit > 0.95) * 0.4)
        if kind == 'plate':
            # segmented throat and belly plates: pale gold bands across his length, dark seams between
            if grad == 'belly':
                t = rel[..., 1]
                n_seg = max(2, int(round(c['size'][1] / 2.0)))
            else:
                t = rel[..., 2]
                n_seg = max(2, int(round(c['size'][2] / 2.0)))
            s = (t * n_seg) % 1.0
            col = mix(col, mix(GOLD, GOLD_HI, 0.5), 0.55)
            col *= (0.82 + 0.3 * (1 - s))[..., None]
            col = mix(col, CHAR, (s > 0.8) * 0.65)
            col = mix(col, OCHRE, np.clip(np.abs(rel[..., 0] - 0.5) * 2 - 0.5, 0, 1) * 0.6)
        # charcoal in the hollows: along each face's foot and sides; light on its top edge
        col = mix(col, CHAR, ((J == h - 1) * 0.4 + ((I == 0) | (I == w - 1)) * 0.22) * (h > 2))
        if kind in ('armor', 'plate'):
            col = mix(col, GOLD_HI, (J == 0) * 0.25)
        col *= light
        col *= (0.95 + 0.1 * jit)[..., None]
    img[y:y + h, x:x + w] = np.clip(col, 0, 1)
    alpha[y:y + h, x:x + w] = 1.0


def cell_hash(cx, cy, seed):
    n = (np.asarray(cx, np.int64) * 374761393 + np.asarray(cy, np.int64) * 668265263 + seed * 2246822519) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def polygon_mask(poly, X, Y):
    """Inside a polygon (even-odd), and each point's distance to its edge (units)."""
    inside = np.zeros(X.shape, bool)
    dist = np.full(X.shape, 1e9)
    n = len(poly)
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
        cond = ((y1 > Y) != (y2 > Y)) & (X < (x2 - x1) * (Y - y1) / ((y2 - y1) if y2 != y1 else 1e-9) + x1)
        inside ^= cond
        dx, dy = x2 - x1, y2 - y1
        L2 = dx * dx + dy * dy or 1e-9
        t = np.clip(((X - x1) * dx + (Y - y1) * dy) / L2, 0, 1)
        dist = np.minimum(dist, np.hypot(X - (x1 + t * dx), Y - (y1 + t * dy)))
    return inside.astype(float), dist


def paint(m):
    img = np.zeros((TEX, TEX, 3))
    alpha = np.zeros((TEX, TEX))
    mats = rp.bone_matrices(m.bones)
    rng = np.random.default_rng(4)
    for b in m.bones:
        M = mats[b['name']]
        for c in b['cubes']:
            if 'rects' not in c:
                continue
            C = M
            if 'rotation' in c:
                px, py, pz = c['pivot']
                rx, ry, rz = c['rotation']
                C = M @ rp.about_pivot([-px, py, pz], rp.rot_matrix(math.radians(-rx), math.radians(-ry),
                                                                      math.radians(rz)))

            def world(lp, C=C):
                q = np.concatenate([lp * (-1, 1, 1), np.ones(lp.shape[:-1] + (1,))], -1)
                r = q @ C.T
                return r[..., :3] * (-1, 1, 1)

            for face, rect in c['rects'].items():
                nrm = (F @ C[:3, :3] @ F) @ np.array(NORMALS[face], float)
                paint_face(c, face, rect, world, nrm, img, alpha, rng)
    out = np.concatenate([np.clip(img, 0, 1), alpha[..., None]], -1)
    return Image.fromarray((out * 255).round().astype('uint8'), 'RGBA')


def geo_json(m):
    bones = []
    for b in m.bones:
        nb = {k: b[k] for k in ('name', 'parent', 'pivot', 'rotation') if k in b}
        cubes = []
        for c in b['cubes']:
            nc = {'origin': c['origin'], 'size': c['size']}
            for k in ('pivot', 'rotation'):
                if k in c:
                    nc[k] = c[k]
            nc['uv'] = uv_of(c)
            cubes.append(nc)
        if cubes:
            nb['cubes'] = cubes
        bones.append(nb)
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.king_ghidorah', 'texture_width': TEX, 'texture_height': TEX,
                        'visible_bounds_width': 16, 'visible_bounds_height': 9, 'visible_bounds_offset': [0, 4, 0]},
        'bones': bones}]}


# ---------------------------------------------------------------- animations
SIDES = ('r', 'l')
HEADS = ('c', 'r', 'l')
NECK_SEGS = 8
TAIL_SEGS = 10
TAU = 2 * math.pi


class Pose(dict):
    """{bone: {channel: [x, y, z]}}; add() sums into it, sym() sets a right-hand bone and its mirror."""

    def add(self, bone, channel, v):
        cur = self.setdefault(bone, {}).get(channel)
        if channel == 'scale':
            self[bone][channel] = [a * b for a, b in zip(cur, v)] if cur else list(v)
        else:
            self[bone][channel] = [a + b for a, b in zip(cur, v)] if cur else list(v)

    def rot(self, bone, x=0.0, y=0.0, z=0.0):
        self.add(bone, 'rotation', [x, y, z])

    def sym(self, bone, x=0.0, y=0.0, z=0.0, channel='rotation'):
        """bone (no side) on his right, and mirrored on his left."""
        if channel == 'rotation':
            self.add(bone + '_r', channel, [x, y, z])
            self.add(bone + '_l', channel, [x, -y, -z])
        elif channel == 'position':
            self.add(bone + '_r', channel, [x, y, z])
            self.add(bone + '_l', channel, [-x, y, z])
        else:
            self.add(bone + '_r', channel, [x, y, z])
            self.add(bone + '_l', channel, [x, y, z])


def smooth(t):
    t = min(1.0, max(0.0, t))
    return t * t * (3 - 2 * t)


def window(t, a, b, c, d):
    """0 before a, easing up to 1 by b, 1 until c, easing down to 0 by d."""
    if t <= a or t >= d:
        return 0.0
    if t < b:
        return smooth((t - a) / (b - a))
    if t <= c:
        return 1.0
    return 1.0 - smooth((t - c) / (d - c))


# ---------------------------------------------------------------- wings and their membranes
# The wing's poses, each a rotation per bone of his right wing (the left mirrors it). His wings are never folded but
# while he digs himself out of the ground (TUCK, the entrance only): he stands with them raised and half spread
# (STANCE, after the first reference picture), spreads them right out for a roar or a beam, and beats them flying.
TUCK = {'wing_shoulder': (-8, 66, 4), 'wing_upper': (0, 0, -40), 'wing_fore': (0, 0, 128), 'wing_hand': (0, 0, -170),
        'wing_finger1': (0, 0, -4), 'wing_finger2': (0, 0, 27), 'wing_finger3': (0, 0, 55), 'wing_claw': (0, 0, 85)}
# (STANCE: his wings planted like front legs, as in the first reference picture: each wrist claw on the ground out to
# his side and ahead of his feet, the forearm rising from it to the elbow, the fingers swept up and back from the wrist
# into a sail; found by a search over the wing's joints for the claw's tip on the ground)
STANCE = {'wing_shoulder': (-61, -2, -33), 'wing_upper': (0, 0, -42), 'wing_fore': (0, 0, -43), 'wing_hand': (0, 0, -144),
          'wing_finger1': (0, 0, 8), 'wing_finger2': (0, 0, 0), 'wing_finger3': (0, 0, -8), 'wing_claw': (0, 0, 66)}
SPREAD = {'wing_shoulder': (0, 6, 10), 'wing_upper': (0, 0, 4), 'wing_fore': (0, 0, 2), 'wing_hand': (0, 0, 2),
          'wing_finger1': (0, 0, 4), 'wing_finger2': (0, 0, 0), 'wing_finger3': (0, 0, -4)}


def wing_pose(p, pose, weight=1.0):
    for bone, (x, y, z) in pose.items():
        p.sym(bone, x * weight, y * weight, z * weight)


def blend_wings(p, poses):
    """[(pose, weight)]: the wings somewhere between poses."""
    for pose, weight in poses:
        if weight:
            wing_pose(p, pose, weight)


def flap(p, phase, amp=1.0, row=1.0):
    """A wingbeat (phase in radians, 0 at the top of the upstroke): the shoulder drives it, each joint further out a
    beat behind the one before, so the wing ripples down and up rather than swinging stiffly."""
    s = math.sin(phase)
    p.sym('wing_shoulder', 0, -9 * row * math.sin(phase + 0.6) * amp, 32 * s * amp)
    p.sym('wing_upper', 0, 0, 8 * math.sin(phase - 0.5) * amp)
    p.sym('wing_fore', 0, 0, -14 * math.sin(phase - 0.9) * amp)
    p.sym('wing_hand', 0, 0, -10 * math.sin(phase - 1.3) * amp)
    for i, lag in enumerate((1.6, 1.8, 2.0)):
        p.sym('wing_finger%d' % (i + 1), 0, 0, -5 * math.sin(phase - lag) * amp)


# Each membrane is pinned between three points: its pivot, and two points on the bones it spans; the bones it hangs
# on (wing_webN, wing_webN_skin) are turned and scaled so that it reaches all three, whatever the wing does.
_S, _E, _W = np.array(SHOULDER), np.array(ELBOW), np.array(WRIST)
_T = [np.array(t) for t in TIPS]
WEBS = {
    1: ('wing_hand', _W, ('wing_finger1', _T[0]), ('wing_finger2', _T[1])),
    2: ('wing_hand', _W, ('wing_finger2', _T[1]), ('wing_finger3', _T[2])),
    3: ('wing_hand', _W, ('wing_finger3', _T[2]), ('wing_upper', _E)),
    4: ('chest', np.array([_S[0], _S[1], WEB_Z]), ('wing_upper', _E), ('chest', np.array(FLANK))),
}
G4 = np.diag([-1.0, 1.0, 1.0, 1.0])
SHIFT = np.zeros(3)     # (how far the built model was raised to stand on y = 0: main())


def sided(bone, side):
    return bone if bone == 'chest' else '%s_%s' % (bone, side)


def solve_webs(bones, pose, memory):
    """Set every membrane's two bones in `pose` so the membrane spans its anchors (memory keeps the last frame's
    decomposition per membrane, so that it changes smoothly from frame to frame)."""
    mats = {k: G4 @ M @ G4 for k, M in rp.bone_matrices(bones, pose).items()}

    def at(bone, point):
        return (mats[bone] @ np.append(point, 1.0))[:3]

    for side in SIDES:
        flip = np.array([1.0 if side == 'r' else -1.0, 1.0, 1.0])
        for k, (parent, pivot, (bone_a, point_a), (bone_b, point_b)) in WEBS.items():
            P, A, B = pivot * flip + SHIFT, point_a * flip + SHIFT, point_b * flip + SHIFT
            Q = mats[sided(parent, side)]
            Pw = at(sided(parent, side), P)
            Aw, Bw = at(sided(bone_a, side), A), at(sided(bone_b, side), B)
            Ql = Q[:3, :3]
            a = np.linalg.solve(Ql, Aw - Pw)
            b = np.linalg.solve(Ql, Bw - Pw)
            dA, dB = A - P, B - P
            n0 = unit(np.cross(dA, dB))
            n = unit(np.cross(a, b))
            L = np.stack([a, b, n], 1) @ np.linalg.inv(np.stack([dA, dB, n0], 1))
            U, sv, Vt = np.linalg.svd(L)
            key = (side, k)
            Up, prev_euler = memory.get(key, (np.eye(3), None))
            U, sv, Vt = align_svd(U, sv, Vt, Up)
            outer, inner = '%s_web%d_%s' % ('wing', k, side), 'wing_web%d_skin_%s' % (k, side)
            e1, e2 = euler(U), euler(Vt)
            if prev_euler is not None:
                e1 = unwrap(e1, prev_euler[0])
                e2 = unwrap(e2, prev_euler[1])
            memory[key] = (U, (e1, e2))
            pose.setdefault(outer, {})['rotation'] = list(e1)
            pose[outer]['scale'] = [float(v) for v in sv]
            pose.setdefault(inner, {})['rotation'] = list(e2)


def align_svd(U, sv, Vt, Up):
    import itertools
    best = None
    for perm in itertools.permutations(range(3)):
        Uq = U[:, perm]
        score = sum(abs(Uq[:, i] @ Up[:, i]) for i in range(3))
        if best is None or score > best[0]:
            best = (score, perm)
    perm = list(best[1])
    U, sv, Vt = U[:, perm].copy(), sv[perm].copy(), Vt[perm, :].copy()
    for i in range(3):
        if U[:, i] @ Up[:, i] < 0:
            U[:, i] *= -1
            Vt[i, :] *= -1
    if np.linalg.det(U) < 0:
        i = int(np.argmin([abs(U[:, j] @ Up[:, j]) for j in range(3)]))
        U[:, i] *= -1
        Vt[i, :] *= -1
    return U, sv, Vt


def unwrap(e, prev):
    return [v + 360.0 * round((pv - v) / 360.0) for v, pv in zip(e, prev)]


# ---------------------------------------------------------------- his body's poses
def neck_curl(p, head, amounts, yaw=0.0, roll=0.0):
    """Pitch a neck's segments (degrees each, from its base), and turn it (spread along it)."""
    for i, a in enumerate(amounts):
        p.rot('neck_%s_%d' % (head, i + 1), a, yaw / NECK_SEGS, roll / NECK_SEGS)


def head_look(p, head, pitch=0.0, yaw=0.0, roll=0.0, jaw=0.0):
    p.rot('head_' + head, pitch, yaw, roll)
    if jaw:
        p.rot('jaw_' + head, jaw)


# each head's own rhythm: (pitch, yaw) frequencies in cycles per loop, phases, and how far it wanders
MINDS = {'c': (1, 2, 0.0, 0.35, 1.0), 'r': (2, 1, 0.55, 0.1, 1.15), 'l': (1, 3, 0.8, 0.7, 1.1)}
SIDE = {'c': 0.0, 'r': -1.0, 'l': 1.0}


def wander(p, head, t, length, amount=1.0):
    """A head minding its own business: looking about, now and then up, at its own pace."""
    fp, fy, php, phy, k = MINDS[head]
    a = amount * k
    u = t / length
    pitch = 5 * math.sin(TAU * (fp * u + php)) * a
    yaw = 12 * math.sin(TAU * (fy * u + phy)) * a + 4 * math.sin(TAU * (3 * u + php)) * a
    spread = SIDE[head] * 10.0 * a      # the outer heads look outwards
    for i in range(NECK_SEGS):
        f = (i + 1) / NECK_SEGS
        p.rot('neck_%s_%d' % (head, i + 1), pitch * 0.25 * f, (yaw + spread) * 0.07 * f, 0)
    head_look(p, head, -pitch * 0.6, yaw * 0.5 + spread * 0.5, yaw * 0.12)


def tails(p, t, length, amp=3.0, cycles=1, lift=0.0, low=0.0):
    """Both tails swaying, a wave running down each, the two out of step; low: lowered along the ground."""
    for k, s in enumerate(SIDES):
        sign = 1 if s == 'r' else -1
        for i in range(TAIL_SEGS):
            ph = TAU * (cycles * t / length) - i * 0.45 + k * 2.1
            p.rot('tail_%s_%d' % (s, i + 1), lift * (1 if i < 3 else 0) - low * (1.0 if i < 2 else -0.25)
                  + 1.2 * math.sin(ph + 1.0), sign * amp * math.sin(ph), 0)


def breathe(p, t, length, cycles=2, depth=1.0):
    b = math.sin(TAU * cycles * t / length)
    p.add('chest', 'scale', [1 + 0.018 * b * depth, 1 + 0.01 * b * depth, 1 + 0.022 * b * depth])
    p.rot('chest', -0.8 * b * depth)
    p.add('torso', 'position', [0, 0.25 * b * depth, 0])


CROUCH_DROP = -4.0      # (set so his feet stay on the ground: main() checks)


def crouch(p, amount=1.0):
    """The stance of the first reference picture: hunched low and forward on bent legs, necks reared up from it."""
    # (a thigh's positive pitch swings its knee back, a shin's its foot back: knees forward, feet back under him)
    p.add('pelvis', 'position', [0, CROUCH_DROP * amount, -1.5 * amount])
    p.sym('leg_thigh', -26 * amount)
    p.sym('leg_shin', 40 * amount)
    p.sym('leg_foot', -14 * amount)
    p.rot('pelvis', 6 * amount)
    p.rot('torso', 14 * amount)
    p.rot('chest', 10 * amount)
    for h in HEADS:
        neck_curl(p, h, [-10 * amount, -8 * amount, -5 * amount, -2 * amount, 0, 2 * amount, 3 * amount, 4 * amount])


# How the planted wrist (and its claw) is moved by the wing's joints, from the stance: offsets (degrees) to the
# shoulder's x, y, z and the upper arm's and forearm's z that move it 12 units back, or lift it 6 units (worked out
# from how each joint moves it there: the smallest turns that do it)
ARM_BACK = (7.9, 8.7, -2.7, 6.0, -9.3)
ARM_LIFT = (0.4, 5.6, 7.9, 8.5, -28.9)


def arm_step(p, side, back, lift):
    """A planted wing-arm stepping: its wrist `back` units back (negative: forward), `lift` units up."""
    k1, k2 = back / 12.0, lift / 6.0
    d = [k1 * a + k2 * b for a, b in zip(ARM_BACK, ARM_LIFT)]
    m = 1 if side == 'r' else -1
    p.rot('wing_shoulder_' + side, d[0], m * d[1], m * d[2])
    p.rot('wing_upper_' + side, 0, 0, m * d[3])
    p.rot('wing_fore_' + side, 0, 0, m * d[4])


def stance(p, t, length, wings=1.0, heads=1.0):
    crouch(p)
    wing_pose(p, STANCE, wings)
    # (his sails breathe with him; his claws stay where they're planted)
    b = math.sin(TAU * 2 * t / length)
    p.sym('wing_finger1', 0, 0, 1.5 * b)
    p.sym('wing_finger3', 0, 0, -1.5 * b)
    breathe(p, t, length, 2)
    for h in HEADS:
        wander(p, h, t, length, heads)


IDLE_LEN = 6.0


def idle(t):
    p = Pose()
    stance(p, t, IDLE_LEN)
    tails(p, t, IDLE_LEN, 3.0, 1, low=4.0)
    return p


WALK_LEN = 2.4


def walk(t):
    # (each hind foot comes down a quarter (right) and three quarters (left) of the way through his stride: his
    # footsteps are sounded there, KingGhidorahEntity.WALK_FOOTFALLS, by a clock his walk is played by in the game)
    p = Pose()
    crouch(p)
    wing_pose(p, STANCE)
    ph = TAU * t / WALK_LEN
    for s, off in (('r', 0.0), ('l', math.pi)):
        a = ph + off
        swing = 22 * math.sin(a)                    # + forward
        lift = max(0.0, math.cos(a))                # the foot's up while the leg swings forward
        thigh = -swing - 8 * lift
        knee = 30 * lift + 2
        p.rot('leg_thigh_' + s, thigh, 0, 0)
        p.rot('leg_shin_' + s, knee, 0, 0)
        p.rot('leg_foot_' + s, -(thigh + knee) * 0.85, 0, 0)
        p.rot('leg_toes_' + s, -14 * lift * math.sin(a) if math.sin(a) < 0 else 8 * lift, 0, 0)
    # his weight going from foot to foot: the hips drop at each footfall, roll and turn with the stride
    p.add('pelvis', 'position', [1.4 * math.sin(ph), -1.6 * abs(math.sin(ph)) + 0.4, 0])
    p.rot('pelvis', 0, 5 * math.sin(ph), 3.5 * math.cos(ph))
    p.rot('torso', 2 + 1.5 * math.cos(2 * ph), -3.5 * math.sin(ph), -2.5 * math.cos(ph))
    p.rot('chest', 1.0 * math.cos(2 * ph - 0.6), -2 * math.sin(ph), -1.5 * math.cos(ph))
    # his planted wings walk like front legs, each with the hind leg on the other side (a diagonal gait): planted and
    # pushed back while that hind foot is down, lifted and swung forward while it swings
    for s, off in (('r', math.pi), ('l', 0.0)):
        a = ph + off
        arm_step(p, s, -10.0 * math.sin(a), 1.8 + 6.0 * max(0.0, math.cos(a)))     # (1.8: over his hips' dip)
    for k, h in enumerate(HEADS):
        bob = math.sin(2 * ph - 1.2 - 0.4 * k)
        neck_curl(p, h, [1.2 * bob] * 4 + [-1.0 * bob] * 4, yaw=4 * math.sin(ph + k), roll=0)
        head_look(p, h, -1.5 * bob, -3 * math.sin(ph + k * 2.0))
        wander(p, h, t, WALK_LEN, 0.4)
    tails(p, t, WALK_LEN, 5.0, 1, lift=1.0, low=3.0)
    return p


ROAR_LEN = 3.2
ROAR_AT = {'c': 0.35, 'r': 0.55, 'l': 0.75}


def roar(t):
    p = Pose()
    crouch(p, 1.0 - 0.5 * window(t, 0.2, 0.8, 2.3, 3.0))
    spread = window(t, 0.25, 0.85, 2.3, 3.0)
    blend_wings(p, [(STANCE, 1.0 - spread), (SPREAD, spread)])
    p.sym('wing_shoulder', 0, 0, 5 * spread * math.sin(TAU * t * 2.2))
    breathe(p, t, ROAR_LEN, 1, 0.6)
    p.rot('torso', -4 * spread)
    p.rot('chest', -5 * spread)
    for h in HEADS:
        roar_head(p, h, t, ROAR_AT[h])
    tails(p, t, ROAR_LEN, 4.0 + 4 * spread, 2, low=3.0)
    return p


def roar_head(p, h, t, t0, hold=1.6):
    rear = window(t, t0 - 0.35, t0, t0, t0 + 0.35)              # draws back
    up = window(t, t0, t0 + 0.3, t0 + hold, t0 + hold + 0.5)    # throws its head up and roars
    tremble = math.sin(TAU * 9 * t + ord(h)) * up
    side = SIDE[h]
    neck_curl(p, h, [-6 * rear - 4 * up, -5 * rear - 3 * up, -3 * rear, 0, 2 * rear + 3 * up,
                     3 * rear + 5 * up, 4 * rear + 6 * up, 2 * rear + 4 * up],
              yaw=side * 16 * up, roll=side * 10 * up)
    head_look(p, h, 14 * rear - 34 * up + 1.5 * tremble, side * 10 * up, 0, jaw=8 * rear + 42 * up + 2 * tremble)
    return up


BITE_LEN = 1.3
BITE_SNAP = 0.62


def bite_of(head):
    def bite(t):
        p = Pose()
        crouch(p)
        wing_pose(p, STANCE)
        p.sym('wing_shoulder', 0, 0, 5 * window(t, 0.3, 0.55, 0.7, 1.1))
        breathe(p, t, BITE_LEN, 1, 0.4)
        draw = window(t, 0.0, 0.35, 0.35, BITE_SNAP)                  # draws back
        lunge = window(t, 0.35, BITE_SNAP, BITE_SNAP + 0.12, 1.25)   # strikes, and comes back
        jaw = window(t, 0.2, 0.5, 0.5, BITE_SNAP) * 38               # gapes, and snaps shut
        side = SIDE[head]
        neck_curl(p, head, [-7 * draw + 16 * lunge, -6 * draw + 12 * lunge, -4 * draw + 8 * lunge,
                            -2 * draw + 4 * lunge, 2 * draw - 4 * lunge, 5 * draw - 6 * lunge,
                            6 * draw - 2 * lunge, 4 * draw + 4 * lunge],
                  yaw=-side * 10 * lunge)
        head_look(p, head, 16 * draw - 6 * lunge, 0, 0, jaw)
        p.rot('chest', 4 * lunge, -side * 4 * lunge)
        p.rot('torso', 3 * lunge)
        for other in HEADS:
            if other == head:
                continue
            # the others keep busy: rear away from the strike, snarl, and look on
            wander(p, other, t + 0.7 * HEADS.index(other), BITE_LEN * 4, 0.6)
            away = SIDE[other] if other != 'c' else -SIDE[head]
            neck_curl(p, other, [-2 * lunge] * 3 + [2 * lunge] * 5, yaw=away * 8 * lunge)
            head_look(p, other, -6 * lunge, away * -10 * lunge, 0, jaw=10 * window(t, 0.3, 0.6, 0.8, 1.2))
        tails(p, t, BITE_LEN, 3.0, 1, low=3.0)
        return p
    return bite


STOMP_LEN = 1.6
STOMP_HIT = 0.85


def stomp(t):
    """His right foot raised high, and brought down."""
    p = Pose()
    crouch(p)
    wing_pose(p, STANCE)
    raise_ = window(t, 0.0, 0.6, 0.6, STOMP_HIT)
    down = window(t, 0.6, STOMP_HIT, STOMP_HIT + 0.1, STOMP_LEN)
    p.rot('leg_thigh_r', -52 * raise_ + 6 * down)
    p.rot('leg_shin_r', 70 * raise_ - 6 * down)
    p.rot('leg_foot_r', -18 * raise_)
    p.rot('leg_toes_r', -16 * raise_)
    p.add('pelvis', 'position', [2.2 * raise_, 1.5 * raise_ - 1.2 * down, 0])
    p.rot('pelvis', 0, 0, -4 * raise_)
    p.rot('torso', -6 * raise_ + 10 * down)
    p.rot('chest', -3 * raise_ + 5 * down)
    p.sym('wing_shoulder', 0, -6 * raise_, 8 * raise_ + 6 * down)
    p.sym('wing_fore', 0, 0, 8 * raise_)
    for k, h in enumerate(HEADS):
        neck_curl(p, h, [-4 * raise_ + 5 * down] * 4 + [2 * raise_ + 3 * down] * 4)
        head_look(p, h, -8 * raise_ + 12 * down, SIDE[h] * 8 * raise_, 0, jaw=18 * window(t, 0.5, 0.75, 1.0, 1.4))
        wander(p, h, t + k, STOMP_LEN * 3, 0.3)
    tails(p, t, STOMP_LEN, 4.0, 1, lift=6 * raise_, low=3.0)
    return p


WHIP_LEN = 1.8
WHIP_HIT = 0.75


def tail_whip(t):
    """Both tails swung round behind him, his hips and body twisting into it."""
    p = Pose()
    crouch(p)
    wing_pose(p, STANCE)
    wind = window(t, 0.0, 0.5, 0.5, 0.85)
    whip = window(t, 0.5, 0.85, 0.95, WHIP_LEN)
    p.rot('pelvis', 0, 22 * wind - 34 * whip, 0)
    p.rot('torso', 0, -10 * wind + 14 * whip, 0)
    p.rot('chest', 0, -8 * wind + 12 * whip, 0)
    p.sym('wing_shoulder', 0, 0, 6 * wind + 10 * whip)
    for s in SIDES:
        for i in range(TAIL_SEGS):
            lag = min(1.0, max(0.0, (t - 0.5 - i * 0.025) / 0.3))
            sweep = smooth(lag) * window(t, 0.5, 0.6, 1.0, WHIP_LEN)
            p.rot('tail_%s_%d' % (s, i + 1), 2 * wind + (3 if i < 3 else -1) * whip, 9 * wind - 16 * sweep, 0)
    for k, h in enumerate(HEADS):
        neck_curl(p, h, [0, 0, 0, 0, 2 * whip, 2 * whip, 0, 0], yaw=-20 * wind + 26 * whip)
        head_look(p, h, -4 * whip, -10 * wind + 18 * whip, 0, jaw=14 * whip)
        wander(p, h, t + k, WHIP_LEN * 3, 0.3)
    return p


# (his beams fire as long as his gravity beam sound roars, about 5.6 s, and stop as it fades)
BEAM_LEN = 7.8
BEAM_FIRE = {'c': 1.2, 'r': 1.35, 'l': 1.5}
BEAM_END = 6.8


def gravity_beam(t, lift=False):
    p = Pose()
    brace = window(t, 0.0, 0.9, BEAM_END, BEAM_LEN)
    crouch(p, 1.0 + 0.3 * brace)
    blend_wings(p, [(STANCE, 1.0 - 0.6 * brace), (SPREAD, 0.6 * brace)])
    p.sym('wing_shoulder', 0, 0, 6 * brace * math.sin(TAU * t * 1.4))
    p.add('pelvis', 'position', [0, -1.0 * brace, 0])
    p.rot('torso', 4 * brace)
    breathe(p, t, BEAM_LEN, 1, 0.5)
    for k, h in enumerate(HEADS):
        t0 = BEAM_FIRE[h]
        coil = window(t, 0.1 + 0.12 * k, t0 - 0.1, t0 - 0.1, t0 + 0.1)        # draws back, aiming
        fire = window(t, t0 - 0.05, t0 + 0.08, BEAM_END - 0.1 * k, BEAM_END + 0.5)
        kick = math.exp(-((t - t0) / 0.09) ** 2) if t > t0 - 0.3 else 0.0      # the recoil
        shake = math.sin(TAU * 11 * t + k * 1.7) * fire
        side = SIDE[h]
        raise_ = window(t, 1.7, 3.1, BEAM_END, BEAM_END + 0.5) if lift else 0.0  # lifting what it holds
        neck_curl(p, h, [-8 * coil - 4 * fire, -5 * coil - 3 * fire, -2 * coil - 3 * raise_, 2 * coil + 1 * fire,
                         4 * coil + 3 * fire - 4 * raise_, 4 * coil + 4 * fire - 4 * raise_, 2 * coil + 2 * fire - 6 * kick,
                         -2 * coil - 4 * kick],
                  yaw=-side * 8 * (coil + fire))
        head_look(p, h, -10 * (coil + fire) * 0.8 - 12 * kick + 0.8 * shake - 30 * raise_, side * 4 * fire, 0,
                  jaw=20 * coil + 34 * fire + 8 * kick + 1.5 * shake)
    tails(p, t, BEAM_LEN, 3.0 + 3 * brace, 2, lift=4 * brace, low=3.0)
    return p


def gravity_lift(t):
    return gravity_beam(t, lift=True)


FLY_LEN = 1.2


def fly(t, carrying=False):
    """Flying (the third reference picture): his body pitched forward into the air, his wings beating hard,
    his legs drawn up under him (reaching down, talons clenched, when he's carrying something), tails streaming."""
    p = Pose()
    ph = TAU * t / FLY_LEN
    p.rot('root', 28)
    flap(p, ph, 1.0)
    wing_pose(p, SPREAD)
    for h in HEADS:
        neck_curl(p, h, [-5, -5, -4, -2, 0, 2, 3, 3], yaw=SIDE[h] * 6)
        head_look(p, h, -10 + 2 * math.sin(ph + 1.0), SIDE[h] * 8)
        wander(p, h, t, FLY_LEN * 5, 0.5)
    bob = math.sin(ph - 1.0)
    if carrying:
        # legs reaching down and forward, talons clenched round what they hold
        p.sym('leg_thigh', -12 - 2 * bob)
        p.sym('leg_shin', 10)
        p.sym('leg_foot', -28)
        p.sym('leg_toes', 45)
    else:
        p.sym('leg_thigh', -48 - 3 * bob)
        p.sym('leg_shin', 70)
        p.sym('leg_foot', -40)
        p.sym('leg_toes', 25)
    for k, s in enumerate(SIDES):
        sign = 1 if s == 'r' else -1
        for i in range(TAIL_SEGS):
            wave = math.sin(ph - i * 0.5 + k * 1.3)
            p.rot('tail_%s_%d' % (s, i + 1), (14 if i == 0 else 1.5) + 2.5 * wave, sign * (-3 + 2 * wave), 0)
    p.add('root', 'position', [0, 2.5 * math.sin(ph + math.pi), 0])     # (rising with each downstroke)
    return p


def fly_carry(t):
    return fly(t, carrying=True)


DEATH_LEN = 3.6


def death(t):
    """Brought down: he staggers, his heads sag one after another, and he crashes forward onto his chest."""
    p = Pose()
    fall = smooth(t / 2.6)
    crouch(p, 1.0 + fall)
    blend_wings(p, [(STANCE, 1.0 - fall), (SPREAD, fall)])
    p.sym('wing_shoulder', 6 * fall, 0, -38 * fall)
    p.sym('wing_fore', 0, 0, -12 * fall)
    p.add('root', 'position', [0, -14 * fall, -6 * fall])
    p.rot('root', 34 * fall, 0, 6 * fall)
    for k, h in enumerate(HEADS):
        sag = smooth((t - 0.3 * k) / 1.6)
        neck_curl(p, h, [6 * sag, 7 * sag, 7 * sag, 8 * sag, 8 * sag, 6 * sag, 4 * sag, 2 * sag], yaw=SIDE[h] * 20 * sag)
        head_look(p, h, 20 * sag, SIDE[h] * 15 * sag, SIDE[h] * 20 * sag, jaw=18 * sag)
    tails(p, t, DEATH_LEN, 4.0 * (1 - fall), 1, low=4.0 + 4 * fall)
    return p


# ---------------------------------------------------------------- his entrance, out of the ground
ENTRANCE_LEN = 14.6
DEEP = -128.0          # all of him below the surface (his model's tops are 117 units up)
BURIED = -90.0         # his chest, back spikes and tucked wings still below it, his necks long enough to reach out
FIRST_HEAD = 'l'
HEAD_RISE = {'l': (3.0, 5.6), 'r': (5.4, 7.2), 'c': (6.0, 7.8)}
CLIMB = [(8.0, 8.7, BURIED, -60.0), (8.95, 9.65, -60.0, -29.0), (9.85, 10.6, -29.0, 0.0)]
WINGS_OPEN = (10.8, 12.4)
ROAR_FROM = 12.5
ENTRANCE_ROAR = {'c': 12.6, 'r': 12.75, 'l': 12.9}


def ease(t, a, b):
    return smooth((t - a) / (b - a))


def entrance(t):
    """Fourteen and a half seconds: the ground cracks (only the effects are seen), one head rises through it and
    looks about, the other two follow, uncoiling, each its own way; his body hauls itself out after them, lurch by
    lurch, wings tucked; he pauses, then spreads his wings to their full span; and all three heads throw themselves up
    and roar. It ends in his stance, as his idle and walk begin."""
    p = Pose()
    # where his body is: deep below the surface, then just below it, then hauled up and out in three lurches
    y = DEEP if t < 2.9 else BURIED
    if 2.6 <= t < 3.0:
        y = DEEP + (BURIED - DEEP) * ease(t, 2.6, 3.0)
    for a, b, y0, y1 in CLIMB:
        if t >= a:
            y = y0 + (y1 - y0) * ease(t, a, b)
    climbing = window(t, 7.8, 8.2, 10.4, 10.9)
    heave = sum(math.exp(-((t - b) / 0.14) ** 2) for a, b, _, _ in CLIMB)      # the jolt at the end of each lurch
    p.add('root', 'position', [0, y - 1.5 * heave, 0])
    p.rot('root', 16 * climbing - 4 * heave, 0, 3 * math.sin(t * 2.3) * climbing)
    # his legs push and claw at the earth as he climbs
    for s, off in (('r', 0.0), ('l', math.pi)):
        push = math.sin(t * 4.2 + off) * climbing
        p.rot('leg_thigh_' + s, -30 * climbing - 18 * push)
        p.rot('leg_shin_' + s, 40 * climbing + 15 * max(0.0, push))
        p.rot('leg_foot_' + s, -20 * climbing)
    after = ease(t, 10.4, 11.4)
    crouch(p, after)
    # wings: tucked until he's out, a pause, then spread slowly to their full span, then settled into his stance
    spread = ease(t, *WINGS_OPEN)
    settle = ease(t, 13.4, ENTRANCE_LEN)
    blend_wings(p, [(TUCK, 1.0 - spread), (SPREAD, spread * (1.0 - settle)), (STANCE, settle)])
    p.sym('wing_shoulder', 0, 0, 18 * math.sin(math.pi * spread) * (1.0 - settle))   # (the last of the push)
    p.sym('wing_hand', 0, 0, -10 * math.sin(math.pi * spread))
    # his necks: curled down out of sight, then each uncoiling and rising, base first, in its own time
    for h in HEADS:
        t0, t1 = HEAD_RISE[h]
        segs = []
        for i in range(NECK_SEGS):
            u = ease(t, t0 + (t1 - t0) * 0.55 * i / NECK_SEGS, t0 + (t1 - t0) * (0.45 + 0.55 * i / NECK_SEGS))
            curl = 34.0 * (1.0 - u)                                         # curled forward and down, hidden
            snake = 9.0 * math.sin(TAU * (t * 0.45 + i * 0.13) + ord(h)) * u * (1.0 - u) * 4.0   # serpentine
            segs.append(curl + snake)
        live = ease(t, t0, t1)
        neck_curl(p, h, segs, yaw=SIDE[h] * 14 * live)
        # looking about: the first slowly, deliberately, one way then the other; the outer heads off their own ways,
        # the middle one settling to stare straight ahead
        if h == FIRST_HEAD:
            look = 38 * math.sin(math.pi * ease(t, 5.0, 7.6)) - 22 * math.sin(math.pi * ease(t, 7.4, 9.4))
            head_look(p, h, -12 * live + 8 * math.sin(math.pi * ease(t, 4.6, 6.0)), look, 6 * math.sin(math.pi * ease(t, 5.0, 7.6)))
        elif h == 'r':
            head_look(p, h, -10 * live, -30 * math.sin(math.pi * ease(t, 6.6, 9.8)) - 8 * live, -5 * live)
        else:
            head_look(p, h, -16 * live + 10 * ease(t, 7.6, 9.0), 6 * (1.0 - ease(t, 7.6, 9.0)) * live)
        # the roar
        up = roar_head(p, h, t, ENTRANCE_ROAR[h], hold=0.9) if t >= ROAR_FROM - 0.4 else 0.0
    breathe(p, t, ENTRANCE_LEN, 4, 0.8)
    tails(p, t, ENTRANCE_LEN, 4.0 * after, 3, low=3.0 * after)
    # the idle's first moment at the end, so it carries straight on into it
    if t > 13.6:
        k = ease(t, 13.6, ENTRANCE_LEN)
        q = Pose()
        for h in HEADS:
            wander(q, h, 0.0, IDLE_LEN)
        for bone, ch in q.items():
            for c, v in ch.items():
                p.add(bone, c, [x * k for x in v])
    return p


CLIPS = {
    'idle': (idle, IDLE_LEN, True),
    'walk': (walk, WALK_LEN, True),
    'roar': (roar, ROAR_LEN, False),
    'bite_c': (bite_of('c'), BITE_LEN, False),
    'bite_r': (bite_of('r'), BITE_LEN, False),
    'bite_l': (bite_of('l'), BITE_LEN, False),
    'stomp': (stomp, STOMP_LEN, False),
    'tail_whip': (tail_whip, WHIP_LEN, False),
    'gravity_beam': (gravity_beam, BEAM_LEN, False),
    'gravity_lift': (gravity_lift, BEAM_LEN, False),
    'fly': (fly, FLY_LEN, True),
    'fly_carry': (fly_carry, FLY_LEN, True),
    'death': (death, DEATH_LEN, False),
    'entrance': (entrance, ENTRANCE_LEN, False),
}
FPS = 10


def bake(fn, length, loop, bones):
    n = int(round(length * FPS))
    memory = {}
    frames = []
    for i in range(n + 1):
        t = i / FPS
        pose = fn(t)
        solve_webs(bones, pose, memory)
        frames.append((t, pose))
    clip_bones = {}
    names = sorted({b for _, p in frames for b in p})
    for b in names:
        out = {}
        for ch in ('rotation', 'position', 'scale'):
            rest = [1.0, 1.0, 1.0] if ch == 'scale' else [0.0, 0.0, 0.0]
            vals = [(t, p.get(b, {}).get(ch, rest)) for t, p in frames]
            if all(max(abs(a - r) for a, r in zip(v, rest)) < 1e-3 for _, v in vals):
                continue
            out[ch] = {('%.2f' % t).rstrip('0').rstrip('.') if t else '0.0':
                       {'post': [round(x, 2 if ch != 'scale' else 3) for x in v], 'lerp_mode': 'catmullrom'} for t, v in vals}
        if out:
            clip_bones[b] = out
    clip = {'animation_length': length, 'bones': clip_bones}
    if loop:
        clip['loop'] = True
    return clip


def animations(bones):
    return {'format_version': '1.8.0', 'animations': {
        'animation.king_ghidorah.' + name: bake(fn, length, loop, bones) for name, (fn, length, loop) in CLIPS.items()}}


def main():
    global SHIFT
    m = build()
    pts = world_points(m)
    m.shift(-pts[:, 1].min())
    SHIFT = np.array([0.0, -pts[:, 1].min(), 0.0])
    used = layout(m)
    tex = paint(m)
    os.makedirs(os.path.dirname(GEO), exist_ok=True)
    with open(GEO, 'w') as f:
        json.dump(geo_json(m), f, separators=(',', ':'))
        f.write('\n')
    tex.save(TEXTURE)
    with open(ANIMS, 'w') as f:
        json.dump(animations(geo_json(m)['minecraft:geometry'][0]['bones']), f, separators=(',', ':'))
        f.write('\n')
    pts = world_points(m)
    print(len(m.bones), 'bones', sum(len(b['cubes']) for b in m.bones), 'cubes; texture rows used', used, 'of', TEX)
    print('bounds', pts.min(0).round(1), pts.max(0).round(1))
    return m


def previews(out):
    """Pictures of him: idle from the front, side, behind and three-quarters, then each animation in six frames."""
    os.makedirs(out, exist_ok=True)

    def cam(eye, target, fov=55):
        return {'eye': (-eye[0], eye[1], eye[2]), 'target': (-target[0], target[1], target[2]), 'fov': fov}

    def pose(name, t):
        return rp.animation_pose(ANIMS, 'animation.king_ghidorah.' + name, t)

    tmp = os.path.join(out, '_frame.png')
    views = [((0, 70, -190), (0, 55, 10)), ((-190, 70, 10), (0, 55, 10)), ((0, 90, 200), (0, 50, 20)),
             ((120, 85, -130), (0, 55, 5))]
    frames = []
    for eye, target in views:
        rp.render(GEO, TEXTURE, tmp, size=520, pose=pose('idle', 0.0), camera=cam(eye, target))
        frames.append(Image.open(tmp).copy())
    sheet(frames, os.path.join(out, 'king_ghidorah_all_round.png'))
    for name, (_, length, _) in CLIPS.items():
        frames = []
        for k in range(6):
            rp.render(GEO, TEXTURE, tmp, size=420, pose=pose(name, length * k / 6), camera=cam((130, 85, -125), (0, 58, 5), 60))
            frames.append(Image.open(tmp).copy())
        sheet(frames, os.path.join(out, 'king_ghidorah_%s.png' % name))
    os.remove(tmp)


def sheet(frames, path):
    w = sum(f.width for f in frames)
    out = Image.new('RGB', (w, max(f.height for f in frames)))
    x = 0
    for f in frames:
        out.paste(f, (x, 0))
        x += f.width
    out.save(path)
    print('wrote', path)


if __name__ == '__main__':
    main()
    if '--preview' in sys.argv:
        previews(sys.argv[sys.argv.index('--preview') + 1])
