#!/usr/bin/env python3
"""
Builds Godzilla Minus Zero's animation file from Minus One's.

    python3 tools/make_minus_zero_animations.py

Writes animations/entity/godzilla_minus_zero.animation.json: every one of Minus One's animations as it is (he moves as
Minus One does), plus two that are his alone:

animation.gojira.charge_zero -- his atomic breath with a second blast in it. Minus One's charge up to the end of his
    beam, where it holds for 6 s more (the beam goes on flickering as it does). His first charge is his middle row of
    plates' (T, T2-T22: they punch up and light as Minus One's do, and slam down as he fires); the second is the row
    either side of it (T12_side, T15_side ... T22_side, tools/make_minus_zero.py): in those 6 s, as their glow comes up
    (the renderer: the middle row's dimmed out 2 s after the first explosion; these lit over the 2 s after that), they
    punch up one after another from the end of his tail forward (SIDE_ORDER, SIDE_STEP apart) and slam down as he fires
    his second laser (GodzillaEntity.SURGE_TICK), with the jolt of his first, while the middle row stays down. His
    sounds: charged again as they light up, then his second beam firing. All that came after the beam in Minus One's
    (the beam dying out, the roar) comes 6 s later.

animation.gojira.tail_strike -- the tail strike: the tip of his tail's plate (T22) punches up and glows (the renderer's
    tail-tip glow, GodzillaEntity's DATA_TAIL_GLOW) for 1 s, then spin_whip as Minus One does it, 1 s later; the plate
    goes back down once the whip is over. His sounds: the plate punching up, then his tail swinging round.

The game reads the times in GodzillaEntity (BEAM_START_TICK, CHARGE_BLAST_TICK, SURGE_TICK, SECOND_BEAM_END_TICK,
Attack.TAIL_STRIKE) and GodzillaRenderer (FIRE, FIRST_BLAST, DIMMED, SURGE): they have to agree with the ones here.
"""
import copy
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                    'godzillaminusone', 'animations', 'entity')
SOURCE = os.path.join(ROOT, 'godzilla.animation.json')
TARGET = os.path.join(ROOT, 'godzilla_minus_zero.animation.json')

PLATES = ['T'] + ['T%d' % i for i in range(2, 23)]

# --- the second blast (GodzillaEntity: BEAM_START_TICK 304 = renderer FIRE 14.92 s)
HOLD_AT = 18.9583           # Minus One's beam at its last full flicker (it dies out from 19.125 s)
EXTRA = 6.0                 # SECOND_BEAM_END_TICK - BEAM_END_TICK = 120 ticks
FIRE = 14.9167              # his first laser (and the plates' first slam: 14.9167 -> 15.0 -> 15.125)
SURGE = FIRE + EXTRA        # his second (SURGE_TICK 424: 120 ticks after the first)
DIMMED = 18.92              # renderer DIMMED: his plates' glow at its lowest, coming back from here
PLATE_UP = [0.0, 3.0, 4.0]  # the plates up, as in Minus One's charge
# his second charge's plates, the row either side of the middle one, in the order they punch up (GodzillaRenderer's
# SIDE_ORDER and SIDE_STEP, for their glow, have to agree)
SIDE_ORDER = ['T22', 'T21', 'T20', 'T19', 'T18', 'T17', 'T16', 'T15', 'T12']
SIDE_STEP = 0.1             # seconds between one and the next
SIDE_RISE = 0.25            # seconds each takes to punch up
FLICKER = 0.041667          # the beam's flicker (its bone B alternates every 1/24 s)

# his own sounds (registry.ModSounds; the mod author's): charged again as his plates light up the second time, ending
# as he fires (the sound is 0.54 s long); his second beam firing
CHARGED_AGAIN = SURGE - 0.6
CHARGED_AGAIN_SOUND = 'godzilla_minus_zero.charged_2nd_time'
SECOND_FIRE_SOUND = 'godzilla_minus_zero.2nd_fire_beam'

# --- the tail strike
WINDUP = 1.0                # T22 up and glowing before the spin (Attack.TAIL_STRIKE: 20 ticks more than SPIN)
TIP_UP_SOUND = 'godzilla_minus_zero.dorsal_fin_punch_up'   # as T22 punches up and lights
SWING = 1.4                 # the tail swinging round: its whoosh loudest 0.45 s in, as it sweeps past (the hit, 1.85 s)
SWING_SOUND = 'godzilla_minus_zero.tail_swing'
TIP_UP = [0.0, 5.25, 7.0]   # T22's punch: higher than the charge's
TIP_OVERSHOOT = [0.0, 6.3, 8.4]


def t(x):
    return '%.4f' % x


def vec(value):
    """A keyframe's value as a vector (the one it leaves at: 'post', else 'pre')."""
    if isinstance(value, dict):
        return list(value.get('post', value.get('pre')))
    return list(value)


def vec_in(value):
    """A keyframe's value as a vector (the one it arrives at: 'pre', else 'post')."""
    if isinstance(value, dict):
        return list(value.get('pre', value.get('post')))
    return list(value)


def keys(channel):
    return sorted(channel.keys(), key=float)


def sample(channel, time):
    """A channel's value at a time (linear between its keyframes, which is what a hold needs)."""
    if not isinstance(channel, dict):
        return list(channel)
    ks = keys(channel)
    if time <= float(ks[0]):
        return vec_in(channel[ks[0]])
    for a, b in zip(ks, ks[1:]):
        if float(a) <= time <= float(b):
            va, vb = vec(channel[a]), vec_in(channel[b])
            f = (time - float(a)) / (float(b) - float(a)) if float(b) > float(a) else 0.0
            return [round(x + (y - x) * f, 4) for x, y in zip(va, vb)]
    return vec(channel[ks[-1]])


def sort_channel(channel):
    return {k: channel[k] for k in keys(channel)}


def shift(events, after, by):
    """Keyframed events (sounds, particles) after a time, later."""
    out = {}
    for k in keys(events):
        out[t(float(k) + by) if float(k) > after else k] = events[k]
    return out


def charge_zero(charge):
    anim = copy.deepcopy(charge)
    anim['animation_length'] = round(charge['animation_length'] + EXTRA, 4)
    for name, bone in anim['bones'].items():
        for kind, channel in list(bone.items()):
            if not isinstance(channel, dict):
                continue  # a constant: the same all the way through
            held = sample(channel, HOLD_AT)
            out = {}
            for k in keys(channel):
                time = float(k)
                out[t(time + EXTRA) if time > HOLD_AT + 1e-4 else k] = channel[k]
            if name == 'B':
                # the beam goes on as it was, flickering: its keyframes alternate, every 1/24 s
                flick = [k for k in keys(channel) if float(k) <= HOLD_AT + 1e-4][-2:]
                steps = int(round(EXTRA / FLICKER))
                for i in range(1, steps + 1):
                    out[t(HOLD_AT + i * FLICKER)] = copy.deepcopy(channel[flick[(i + 1) % 2]])
            else:
                if t(HOLD_AT) not in out and not any(abs(float(k) - HOLD_AT) < 1e-3 for k in out):
                    out[t(HOLD_AT)] = held
                out[t(HOLD_AT + EXTRA)] = held
            if name in PLATES and kind == 'position':
                # (the middle row stays down through his second charge: it's the rows either side of it that rise)
                assert held == [0.0, 0.0, 0.0] or held == [0, 0, 0], (name, held)
            if name == 'Body' and kind == 'rotation':
                # the jolt of his second laser
                body = vec(out[t(HOLD_AT + EXTRA)])
                out[t(SURGE)] = body
                out[t(SURGE + 0.0833)] = [body[0] + 5.0, body[1], body[2]]
                out[t(SURGE + 0.375)] = body
            bone[kind] = sort_channel(out)
    # the rows either side punching up as their glow comes back, one after another from the end of his tail, then down
    # with his second laser (as the middle row with his first)
    for i, plate in enumerate(SIDE_ORDER):
        up = DIMMED + 0.05 + i * SIDE_STEP
        anim['bones'][plate + '_side'] = {'position': sort_channel({
            t(0.0): [0.0, 0.0, 0.0],
            t(up): [0.0, 0.0, 0.0],
            t(up + SIDE_RISE * 0.6): [0.0, round(PLATE_UP[1] * 1.15, 3), round(PLATE_UP[2] * 1.15, 3)],   # (overshooting)
            t(up + SIDE_RISE): list(PLATE_UP),
            t(SURGE): list(PLATE_UP),
            t(SURGE + 0.0833): [0.0, -0.45, -0.6],
            t(SURGE + 0.2083): [0.0, 0.0, 0.0],
        })}
    for kind in ('sound_effects', 'particle_effects'):
        if kind in anim:
            anim[kind] = shift(anim[kind], HOLD_AT, EXTRA)
    # his sounds: charged again, and his second beam firing
    sounds = anim['sound_effects']
    sounds[t(CHARGED_AGAIN)] = {'effect': CHARGED_AGAIN_SOUND}
    sounds[t(SURGE)] = {'effect': SECOND_FIRE_SOUND}
    anim['sound_effects'] = sort_channel(sounds)
    # the smoke from his mouth as his second laser fires, as with his first
    smoke = anim['particle_effects']
    for k in [k for k in list(smoke) if FIRE - 0.01 < float(k) < FIRE + 0.01]:
        smoke[t(float(k) + EXTRA)] = copy.deepcopy(smoke[k])
    anim['particle_effects'] = sort_channel(smoke)
    return anim


def tail_strike(spin):
    anim = copy.deepcopy(spin)
    anim['animation_length'] = round(spin['animation_length'] + WINDUP, 4)
    for name, bone in anim['bones'].items():
        for kind, channel in list(bone.items()):
            if not isinstance(channel, dict):
                continue
            start = sample(channel, 0.0)
            out = {t(0.0): start}
            for k in keys(channel):
                out[t(float(k) + WINDUP)] = channel[k]
            bone[kind] = sort_channel(out)
    for kind in ('sound_effects', 'particle_effects'):
        if kind in anim:
            anim[kind] = shift(anim[kind], -1.0, WINDUP)
    # the plate at the tip of his tail punching up (and lighting), then the whoosh of his tail swung round
    sounds = anim.setdefault('sound_effects', {})
    sounds[t(0.0)] = {'effect': TIP_UP_SOUND}
    sounds[t(SWING)] = {'effect': SWING_SOUND}
    anim['sound_effects'] = sort_channel(sounds)
    end = anim['animation_length']
    anim['bones']['T22'] = {'position': {
        t(0.0): [0.0, 0.0, 0.0],
        t(0.1667): list(TIP_OVERSHOOT),
        t(0.2917): list(TIP_UP),
        t(end - 0.35): list(TIP_UP),
        t(end - 0.1): [0.0, 0.0, 0.0],
    }}
    return anim


def main():
    with open(SOURCE) as f:
        data = json.load(f)
    animations = data['animations']
    animations['animation.gojira.charge_zero'] = charge_zero(animations['animation.gojira.charge'])
    animations['animation.gojira.tail_strike'] = tail_strike(animations['animation.gojira.spin_whip'])
    with open(TARGET, 'w') as f:
        json.dump(data, f, separators=(',', ':'))
        f.write('\n')
    print('wrote', TARGET, len(animations), 'animations')


if __name__ == '__main__':
    main()
