"""The atomic blast's cloud as client/render/NuclearCloud.java builds it, for tools/preview_blast.py: the same puffs,
placed, coloured and faded by the same formulas (each function here is the Java method of the same name), from its
own random numbers (so no two blasts, and not the preview and the game, are puff-for-puff the same).

Everything is measured from ground zero, in blocks, and in seconds since the impact.
"""
import math
from dataclasses import dataclass

import numpy as np

# Sprite cells (tools/make_cloud_sprites.py).
SMOKE = (0, 1, 2, 3)
GLOW_OF = {0: 4, 1: 5, 2: 6, 3: 7, 10: 11}
DUST, WISP, COLUMN, FIREBALL, FLASH, OUTER_FIRE = 8, 9, 10, 12, 13, 14

# The cloud's shape, as fractions of its height (mushroomCloudHeight).
FIREBALL_RADIUS = 0.15
CAP_RADIUS = 0.5
CAP_HALF_HEIGHT = 0.12
CAP_CENTRE = 0.8
STEM_RADIUS = 0.075
SKIRT_RADIUS = 0.36
WAVE_REACH = 0.95
RING_ALTITUDE = 0.3

# Colours.
CHARCOAL = np.array([0.20, 0.19, 0.18])
GREY = np.array([0.42, 0.41, 0.39])
BROWN = np.array([0.39, 0.31, 0.23])
PALE = np.array([0.68, 0.66, 0.62])
DUST_COLOUR = np.array([0.56, 0.48, 0.39])
MIST = np.array([0.86, 0.86, 0.85])
DEEP_RED = np.array([0.55, 0.12, 0.03])
ORANGE = np.array([1.0, 0.45, 0.08])
YELLOW = np.array([1.0, 0.82, 0.40])
WHITE = np.array([1.0, 0.98, 0.93])
FLASH_YELLOW = np.array([1.0, 0.93, 0.72])


@dataclass
class Settings:
    height: float = 240.0
    flash: float = 1.5
    fireball: float = 4.0
    shockwave: float = 8.0
    rise: float = 30.0
    cooling: float = 30.0
    mature: float = 60.0
    dissipate: float = 90.0
    flash_strength: float = 1.0

    @property
    def lifetime(self):
        return self.rise + self.mature + self.dissipate


def clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def smoothstep(a, b, x):
    t = clamp((x - a) / (b - a)) if b != a else (1.0 if x >= b else 0.0)
    return t * t * (3.0 - 2.0 * t)


def mix(a, b, t):
    return a + (b - a) * t


def hot_colour(heat, white):
    c = mix(DEEP_RED, ORANGE, clamp(heat * 2.0))
    c = mix(c, YELLOW, clamp(heat * 2.0 - 1.0))
    return mix(c, WHITE, white)


class Puff:
    __slots__ = ("group", "a", "b", "c", "d", "e", "size", "sprite", "shade", "brown", "roll", "spin", "linger", "fade_at", "aspect")

    def __init__(self, **kw):
        for k, v in kw.items():
            setattr(self, k, v)


class Cloud:
    HEAD, DOME, UNDER, STEM, SKIRT, WAVE, HAZE, RING = range(8)

    def __init__(self, settings, seed=1):
        self.s = settings
        rng = np.random.default_rng(seed)
        self.rng = rng
        self.puffs = []
        H = settings.height
        wind = rng.uniform(0, 2 * math.pi)
        self.wind = np.array([math.cos(wind), 0.0, math.sin(wind)])

        def add(group, n, **fixed):
            for _ in range(n):
                p = dict(group=group, a=rng.random(), b=rng.random(), c=rng.random(), d=rng.random(), e=rng.random(),
                         size=rng.uniform(0.8, 1.3), sprite=int(rng.choice(SMOKE)), shade=rng.random(), brown=rng.uniform(0.0, 0.6),
                         roll=rng.uniform(-0.35, 0.35), spin=rng.uniform(-0.002, 0.002), linger=rng.random() < 0.08, fade_at=0.0,
                         aspect=1.0)
                p.update(fixed)
                self.puffs.append(Puff(**p))

        add(self.HEAD, 210)
        add(self.DOME, 60)
        add(self.UNDER, 50)
        add(self.STEM, 120)
        for p in self.puffs[-120:]:
            p.sprite = COLUMN if p.e < 0.6 else p.sprite
        add(self.SKIRT, 70)
        for p in self.puffs[-70:]:
            p.sprite = DUST if p.e < 0.7 else p.sprite
        add(self.WAVE, 96, sprite=DUST)
        add(self.HAZE, 110)
        for p in self.puffs[-110:]:
            p.sprite = p.sprite if p.e < 0.85 else WISP
        add(self.RING, 48)
        for p in self.puffs[-48:]:
            p.sprite = WISP if p.e < 0.5 else DUST
        # sprites with a flat bottom or a long axis stay (nearly) upright; the ground's dust is squashed flat
        for p in self.puffs:
            if p.sprite in (DUST, WISP, COLUMN):
                p.roll *= 0.35
            p.aspect = {self.HAZE: 0.6, self.WAVE: 0.8, self.SKIRT: 0.85}.get(p.group, 1.0)
        # when each fades in the end: the cap's outer billows first, then its underside, the stem and the cap's
        # heart, then the dust at its foot, the ground haze last (a few scattered puffs linger after their neighbours)
        start = {self.HEAD: 0.0, self.DOME: 0.12, self.UNDER: 0.2, self.STEM: 0.28, self.SKIRT: 0.5, self.WAVE: 0.0,
                 self.HAZE: 0.66, self.RING: 0.0}
        spread = {self.HEAD: 0.18, self.DOME: 0.2, self.UNDER: 0.2, self.STEM: 0.35, self.SKIRT: 0.2, self.WAVE: 0.0,
                  self.HAZE: 0.12, self.RING: 0.0}
        for p in self.puffs:
            depth = p.c if p.group == self.HEAD else 0.0
            p.fade_at = start[p.group] + spread[p.group] * p.d + 0.3 * depth + (0.3 if p.linger else 0.0)

    # ------------------------------------------------------------------ the timeline

    def flash(self, t):
        """How bright the flash is: up in a moment, then dying away."""
        f = self.s.flash
        rise = 0.06
        if t < 0 or t > f:
            return 0.0
        v = t / rise if t < rise else math.exp(-(t - rise) / (0.22 * f))
        return v * (1.0 - smoothstep(0.6 * f, f, t))

    def heat(self, t):
        """How hot the cloud still is inside: white-hot at first, cooling to nothing by coolingSeconds."""
        return 1.0 - smoothstep(0.3, self.s.cooling, t)

    def white(self, t):
        return math.exp(-t / 0.6)

    def matured(self, t):
        return clamp((t - self.s.rise) / self.s.mature)

    def dissipated(self, t):
        return clamp((t - self.s.rise - self.s.mature) / self.s.dissipate)

    def fireball_radius(self, t):
        return FIREBALL_RADIUS * self.s.height * (1.0 - math.exp(-max(t, 0.0) / 0.35))

    def risen(self, t):
        """How far the fireball has risen towards the cap's height: quickly at first, slower and slower."""
        x = clamp((t - 0.4) / (self.s.rise - 0.4))
        return 1.0 - (1.0 - x) ** 2.6

    def head(self, t):
        """Centre height, horizontal radius and vertical half-height of the fireball, then of the cap it becomes."""
        H = self.s.height
        r = self.fireball_radius(t)
        e = self.risen(t)
        spread = 1.0 - (1.0 - clamp((t - 0.4) / (self.s.rise * 0.8))) ** 2.0
        m, d = self.matured(t), self.dissipated(t)
        centre = r * 0.55 + (CAP_CENTRE * H - r * 0.55) * e + 0.04 * H * m + 0.06 * H * d
        a = mix(r, CAP_RADIUS * H, spread) + 0.06 * H * m + 0.30 * H * d
        b = mix(r, CAP_HALF_HEIGHT * H, spread) * (1.0 + 0.3 * d)
        return centre, a, b, spread

    def roll(self, t):
        """How far the cap has rolled over on itself (rising in the middle, over the top, down its outside)."""
        return 2.0 * math.pi * t / 120.0 + 1.2 * (1.0 - math.exp(-t / 8.0))

    def wave_radius(self, t):
        S = self.s.shockwave
        tau = S / 3.0
        return WAVE_REACH * self.s.height * (1.0 - math.exp(-max(t, 0.0) / tau)) / (1.0 - math.exp(-S / tau))

    def wave_passes(self, u):
        """When the dust wave reaches u of its reach."""
        S = self.s.shockwave
        tau = S / 3.0
        return -tau * math.log(max(1e-6, 1.0 - u * (1.0 - math.exp(-S / tau))))

    # ------------------------------------------------------------------ puffs

    def build(self, t, ground=lambda x, z: 0.0):
        """Every puff at time t: (position, half-width, height/width, roll, sprite, smoke rgb, hot rgb, how hot it
        looks, alpha, glow sprite, glow rgb)."""
        out = []
        if t < 0 or t >= self.s.lifetime:
            return out
        H = self.s.height
        centre, a, b, spread = self.head(t)
        heat = self.heat(t)
        white = self.white(t)
        d = self.dissipated(t)
        roll = self.roll(t)
        drift = self.wind * (0.12 * H * d * d)
        fb = self.s.fireball
        blaze = 1.0 - smoothstep(0.5 * fb, 1.5 * fb, t)   # the whole fireball glowing, before it cools from the outside in

        for p in self.puffs:
            g = p.group
            fade = 1.0 - smoothstep(p.fade_at, p.fade_at + 0.3, d)
            if fade <= 0.0:
                continue
            alpha = fade * (1.0 - 0.35 * d)
            size_grow = 1.0 + 0.6 * d
            spin = p.roll + p.spin * t
            local_heat = 0.0
            normal_y = 0.0
            outer = 0.0
            if g in (self.HEAD, self.DOME, self.UNDER):
                if g == self.HEAD:
                    theta = p.a * 2 * math.pi
                    phi = p.b * 2 * math.pi - roll * (1.0 - 0.5 * p.c)
                    depth = p.c
                    core = 0.55 * a * spread
                    tube_h = a - core
                    shrink = 1.0 - 0.5 * depth
                    rad = core + tube_h * shrink * math.cos(phi)
                    y = b * shrink * math.sin(phi)
                    normal_y = math.sin(phi)
                    outer = (1.0 - depth) * clamp(0.5 + 0.5 * math.cos(phi))
                    lowness = clamp(0.5 - 0.5 * math.sin(phi))
                    local_heat = clamp(lowness ** 1.5 * (0.6 + 0.5 * depth) + 0.1 * depth)
                elif g == self.DOME:
                    theta = p.a * 2 * math.pi + 0.01 * t
                    rr = math.sqrt(p.b) * 0.55 * a
                    rad = rr
                    y = 0.2 * b + 0.6 * b * math.sqrt(max(0.0, 1.0 - (rr / (0.62 * a)) ** 2)) * (0.6 + 0.4 * p.c)
                    normal_y = 0.9
                    outer = 0.4
                    local_heat = 0.05 * p.c
                else:
                    theta = p.a * 2 * math.pi - 0.008 * t
                    rad = (0.15 + 0.75 * math.sqrt(p.b)) * a
                    y = -b * (0.62 + 0.18 * p.c)
                    normal_y = -1.0
                    outer = 0.2
                    local_heat = clamp(0.7 + 0.3 * p.c)
                pos = np.array([rad * math.cos(theta), centre + y, rad * math.sin(theta)])
                size = p.size * (0.22 * a + 0.08 * b) * size_grow
                if g == self.UNDER:
                    size *= 0.9
                h = heat * max(local_heat, blaze)
                if p.linger:
                    pos = pos + np.array([math.cos(theta), 0.2, math.sin(theta)]) * (0.25 * H * d)
                sprite = p.sprite
                base_alpha = 1.0
                smoke = self.smoke_colour(p, normal_y, outer)
            elif g == self.STEM:
                if t < 0.6:
                    continue
                top = centre - 0.55 * b
                f = (p.b + t / 45.0) % 1.0
                env = smoothstep(0.0, 0.06, f) * (1.0 - smoothstep(0.9, 1.0, f)) * smoothstep(0.6, 2.5, t)
                y = f * top
                theta = p.a * 2 * math.pi + 0.03 * t + 2.5 * f
                r0 = STEM_RADIUS * H
                r = r0 * (1.0 + 1.6 * (1.0 - f) ** 6 + 1.4 * f ** 5) * (0.55 + 0.45 * p.c)
                r *= 1.0 + 0.18 * math.sin(3.0 * p.a * 2 * math.pi + 5.0 * f + 0.1 * t)
                r += 0.1 * H * d * p.e
                pos = np.array([r * math.cos(theta), y, r * math.sin(theta)])
                size = p.size * r0 * (1.25 + 0.8 * (1.0 - f) ** 4 + 0.7 * f ** 3) * size_grow
                h = heat * clamp(f * f * 0.9)
                normal_y = 0.2
                outer = 0.3
                sprite = p.sprite
                base_alpha = env
                smoke = mix(self.smoke_colour(p, normal_y, outer), DUST_COLOUR * 0.8, clamp(1.0 - f * 2.2))
                if p.linger:
                    pos = pos + np.array([math.cos(theta), 0.0, math.sin(theta)]) * (0.2 * H * d)
            elif g == self.SKIRT:
                grow = (1.0 - math.exp(-t / 5.0)) + 0.15 * self.matured(t) + 0.35 * d
                u = p.b
                rad = SKIRT_RADIUS * H * math.sqrt(u) * grow
                theta = p.a * 2 * math.pi + 0.004 * t
                size = (0.06 + 0.05 * (1.0 - u)) * H * p.size * size_grow * (0.5 + 0.5 * min(1.0, grow))
                x, z = rad * math.cos(theta), rad * math.sin(theta)
                y = ground(x, z) + 0.45 * size + 0.05 * H * (1.0 - u) * p.c
                pos = np.array([x, y, z])
                h = 0.0
                normal_y = 0.4
                sprite = p.sprite
                base_alpha = 0.92 * smoothstep(0.2, 0.9, t)
                smoke = mix(DUST_COLOUR, BROWN, p.brown) * (0.8 + 0.3 * p.shade)
            elif g == self.WAVE:
                S = self.s.shockwave
                R = self.wave_radius(t) * (1.0 + 0.03 * (p.b - 0.5))
                if R < 1.0:
                    continue
                theta = (p.a + 0.3 * p.c) * 2 * math.pi
                x, z = R * math.cos(theta), R * math.sin(theta)
                size = (0.035 + 0.05 * R / (WAVE_REACH * H)) * H * p.size
                y = ground(x, z) + 0.4 * size
                pos = np.array([x, y, z])
                h = 0.0
                sprite = p.sprite
                base_alpha = smoothstep(0.0, 0.25, t) * (1.0 - smoothstep(0.55 * S, 1.25 * S, t))
                smoke = DUST_COLOUR * (0.85 + 0.3 * p.shade)
            elif g == self.HAZE:
                u = 0.15 + 0.85 * math.sqrt(p.b)
                passed = self.wave_passes(u)
                if t < passed:
                    continue
                since = t - passed
                theta = p.a * 2 * math.pi
                R = u * WAVE_REACH * H
                x, z = R * math.cos(theta), R * math.sin(theta)
                size = (0.1 + 0.04 * u) * H * p.size * (1.0 + min(0.3, 0.3 * since / 60.0)) * size_grow
                y = ground(x, z) + 0.25 * size + 0.02 * H * (1.0 - math.exp(-since / 20.0))
                pos = np.array([x, y, z])
                h = 0.0
                sprite = p.sprite
                base_alpha = 0.3 * smoothstep(0.0, 2.5, since)
                smoke = mix(DUST_COLOUR, GREY, 0.4 * p.shade)
            else:  # RING
                r = 0.12 * H + 0.75 * H * (1.0 - math.exp(-t / 1.0))
                theta = (p.a + 0.2 * p.c) * 2 * math.pi
                pos = np.array([r * math.cos(theta), RING_ALTITUDE * H + (p.b - 0.5) * 0.03 * H, r * math.sin(theta)])
                size = 0.045 * H * p.size * (1.0 + r / H)
                h = 0.0
                sprite = p.sprite
                base_alpha = 0.5 * smoothstep(0.15, 0.45, t) * (1.0 - smoothstep(1.2, 3.0, t))
                smoke = MIST
            alpha *= base_alpha
            if alpha <= 0.004:
                continue
            pos = pos + drift
            hv = smoothstep(0.05, 0.6, h)
            hot = hot_colour(h, white)
            glow_sprite = GLOW_OF.get(sprite)
            out.append((pos, size, p.aspect, spin, sprite, smoke, hot, hv, alpha, glow_sprite, hot * h if glow_sprite is not None else None))
        return out

    def smoke_colour(self, p, normal_y, outer):
        c = mix(CHARCOAL, GREY, p.shade)
        c = mix(c, BROWN, p.brown)
        lit = clamp(0.5 + 0.5 * normal_y)
        c = c * (0.55 + 0.6 * lit)
        c = mix(c, PALE, 0.65 * lit * outer)
        return c * (1.0 - 0.5 * clamp(-normal_y))

    def extras(self, t):
        """The flash, and the fireball's own glare: (position, half-size, sprite, rgb, additive?, alpha)."""
        H = self.s.height
        out = []
        f = self.flash(t)
        if f > 0.0:
            size = H * (0.05 + 0.35 * (1.0 - math.exp(-t / 0.12)))
            out.append((np.array([0.0, 0.05 * H, 0.0]), size, FLASH, WHITE * f, True, 1.0))
            out.append((np.array([0.0, 0.05 * H, 0.0]), size * 2.8, FLASH, FLASH_YELLOW * (0.55 * f * self.s.flash_strength), True, 1.0))
        fb = self.s.fireball
        if t < 1.3 * fb:
            centre, a, b, _ = self.head(t)
            glare = 1.0 - smoothstep(0.3 * fb, 1.3 * fb, t)
            colour = hot_colour(1.0, self.white(t))
            out.append((np.array([0.0, centre, 0.0]), 1.25 * max(a, b), FIREBALL, colour * glare, True, 1.0))
            if t < fb:
                out.append((np.array([0.0, centre, 0.0]), 1.15 * self.fireball_radius(t), FIREBALL,
                            colour, False, 1.0 - smoothstep(0.4 * fb, fb, t)))
        return out
