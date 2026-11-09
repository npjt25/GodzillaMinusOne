"""Synthesises the mod's own sound effects (the add-on has none for these) as mono Ogg Vorbis.

    python3 tools/make_sounds.py

  * godzilla_inhale.ogg  - Godzilla's in-breath before the atomic breath: exactly as long as the in-breath
                           in the charge animation (tools/edit_animations.py, INHALE_LENGTH = 4 s).
  * nuke_alarm.ogg       - the air-raid siren that wails from the moment a nuclear bomb is called in until it goes
                           off: exactly its 20 second fall (entity/NuclearBombEntity.java, FALL_TICKS).
  * shinden_gun.ogg      - one shot of the Shinden's cannons (one per click): the first of the three shots in the
                           add-on's kplane_fire.ogg burst, with a short echo instead of the next two.
  * shinsei_maru_gun.ogg - one shot of the Shinsei Maru's Type 93 13 mm machine gun: the same shot, lighter and sharper.
  * shinsei_maru_motor.ogg - her old diesel at a steady idle-to-cruise, 2 s that loop seamlessly (every part of it
                           repeats exactly every 2 s: the firing beat, its harmonics and the noise), for the motor
                           (client.ShinseiMaruSounds plays it on a loop, louder and higher as she speeds up).
  * godzilla_minus_zero_tail_swing.ogg - Godzilla Minus Zero's tail strike swinging round (animation.gojira.tail_strike):
                           a deep, heavy whoosh of a tail the size of a train through the air, rising to its loudest as it
                           sweeps past (0.45 s in) and falling away, with the thud of the air it shoves at the peak.
  * king_ghidorah_roar.ogg - King Ghidorah's roar (animation.king_ghidorah.roar): three voices, one a head, each throwing
                           its head up in turn (0.2 s apart, as the animation staggers them): a shrill, rattling trill, a
                           harsh rising and falling cry pulsed some twenty times a second, over a rasp of breath.
  * king_ghidorah_ambient.ogg - one head chittering to itself: the same trill, short and low.
  * king_ghidorah_beam.ogg - his gravity beams: a crackling, buzzing discharge, an electric hum rising under bursts of
                           sparks, that dies away as the beams end.
  * king_ghidorah_rumble.ogg - the ground before his entrance: a deep underground rumble, swelling and grinding, 6 s.
  * king_ghidorah_earth_crack.ogg - the earth breaking open: splitting rock, a crack and the clatter of falling stones.
  * king_ghidorah_wing_spread.ogg - his wings spreading to their full span: a slow, huge rush of air and the snap of
                           the membranes drawn taut.
  * king_ghidorah_wing_flap.ogg - one beat of his wings in flight: a deep whump of air.

    python3 tools/make_sounds.py [name ...]   (only those, e.g. godzilla_minus_zero_tail_swing; all of them without)

Needs numpy and ffmpeg (imageio-ffmpeg's, or one on the PATH) for the Vorbis encoder.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "sounds")
RATE = 44100
INHALE_SECONDS = 4.0
ALARM_SECONDS = 20.0


def ffmpeg():
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        import shutil
        found = shutil.which("ffmpeg")
        if found is None:
            sys.exit("needs ffmpeg: pip install imageio-ffmpeg, or one on the PATH")
        return found


def smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def shaped_noise(seconds, gain_fn, rng, frame=2048):
    """White noise filtered frame by frame (overlap-add STFT): gain_fn(t, freqs) -> gain per frequency bin."""
    n = int(seconds * RATE)
    noise = rng.standard_normal(n + frame)
    hop = frame // 4
    window = np.hanning(frame)
    out = np.zeros(n + frame)
    norm = np.zeros(n + frame)
    freqs = np.fft.rfftfreq(frame, 1.0 / RATE)
    for start in range(0, n, hop):
        seg = noise[start:start + frame] * window
        spec = np.fft.rfft(seg) * gain_fn(start / RATE, freqs)
        out[start:start + frame] += np.fft.irfft(spec, frame) * window
        norm[start:start + frame] += window ** 2
    return out[:n] / np.maximum(norm[:n], 1e-3)


def band(freqs, centre, width):
    return np.exp(-0.5 * ((np.log(np.maximum(freqs, 1.0)) - np.log(centre)) / width) ** 2)


def inhale(rng):
    """A huge creature drawing breath: rushing air that tightens and rises, a rumble in the chest, and the
    crackle of the energy building in his dorsal plates. Swells for 4 s, then stops dead."""
    seconds = INHALE_SECONDS
    t = np.arange(int(seconds * RATE)) / RATE
    p = t / seconds

    def air(time, freqs):
        q = time / seconds
        centre = 320 + 650 * smooth(q)             # the rush of air rises in pitch as the lungs fill
        return band(freqs, centre, 0.75) + 0.35 * band(freqs, 2200 + 900 * q, 0.35)

    rush = shaped_noise(seconds, air, rng)
    rush /= np.max(np.abs(rush))
    swell = smooth(p / 0.12) * (0.45 + 0.55 * smooth(p)) * (1 - smooth((p - 0.965) / 0.035))
    rush *= swell * (1 + 0.18 * np.sin(2 * np.pi * 3.1 * t) * smooth(p / 0.3))

    # chest rumble: low tones with a slow tremble
    rumble = (np.sin(2 * np.pi * 43 * t) + 0.6 * np.sin(2 * np.pi * 61 * t + 1.3) + 0.3 * np.sin(2 * np.pi * 87 * t + 0.4))
    rumble *= (0.5 + 0.5 * np.sin(2 * np.pi * 5.3 * t)) * smooth(p / 0.25) * (1 - smooth((p - 0.96) / 0.04)) * 0.22

    # crackles, more of them as the breath goes on
    crackle = np.zeros_like(t)
    count = int(160 * seconds)
    times = np.sort(rng.random(count) ** 0.6) * seconds * 0.97
    for c in times:
        i = int(c * RATE)
        length = int(RATE * 0.004)
        if i + length < len(crackle):
            crackle[i:i + length] += rng.standard_normal(length) * np.exp(-np.arange(length) / (length / 4)) * (0.2 + 0.8 * c / seconds)
    crackle *= 0.12

    return rush * 0.8 + rumble + crackle


def alarm(rng):
    """An air-raid siren: a rotor siren's two tones wailing up, holding and sliding down again, twice, heard
    across a town (with a little echo off the buildings). Loud enough to hear, not to hurt."""
    seconds = ALARM_SECONDS
    t = np.arange(int(seconds * RATE)) / RATE
    cycle = 10.0
    q = (t % cycle) / cycle
    rise, hold = 0.35, 0.55
    level = np.where(q < rise, smooth(q / rise), np.where(q < hold, 1.0, 1.0 - smooth((q - hold) / (1.0 - hold))))
    freq = 170.0 + 480.0 * level
    freq *= 1 + 0.004 * np.sin(2 * np.pi * 5.5 * t)             # the rotor's wobble
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    phase2 = 2 * np.pi * np.cumsum(freq * 1.26) / RATE            # the second, higher port: a minor third up
    tone = sum(np.sin(k * phase) / k ** 1.3 for k in range(1, 7)) + 0.7 * sum(np.sin(k * phase2) / k ** 1.3 for k in range(1, 6))
    loud = (0.35 + 0.65 * level) * smooth(t / 0.4) * (1 - smooth((t - (seconds - 0.6)) / 0.6))
    wail = tone * loud
    out = wail.copy()
    for delay, gain in ((0.19, 0.35), (0.43, 0.2), (0.77, 0.1)):   # echoes
        d = int(delay * RATE)
        out[d:] += wail[:-d] * gain

    def air(time, freqs):
        return band(freqs, 600, 1.2)

    hiss = shaped_noise(seconds, air, rng)
    hiss /= np.max(np.abs(hiss))
    return out * 0.8 + hiss * loud * 0.06


def read_ogg(name):
    raw = subprocess.run([ffmpeg(), "-v", "error", "-i", os.path.join(OUT, name + ".ogg"), "-f", "s16le", "-ac", "1",
                          "-ar", str(RATE), "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.int16).astype(float) / 32768.0


def single_shot(rng, pitch=1.0, tail=0.25):
    """The first shot of the add-on's three-shot cannon burst (it starts 75 ms in; the second comes at 205 ms), cut
    off before the second and given a short echo so it still sounds full. pitch > 1 makes it lighter."""
    burst = read_ogg("kplane_fire")
    shot = burst[int(0.075 * RATE):int(0.195 * RATE)].copy()
    fade = int(0.03 * RATE)
    shot[-fade:] *= np.linspace(1.0, 0.0, fade) ** 2
    shot[:int(0.003 * RATE)] *= np.linspace(0.0, 1.0, int(0.003 * RATE))
    if pitch != 1.0:
        idx = np.arange(0, len(shot) - 1, pitch)
        shot = np.interp(idx, np.arange(len(shot)), shot)
    # echo: the shot convolved with decaying noise, low-passed
    n = int(tail * RATE)
    impulse = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.06 * RATE))
    impulse = np.convolve(impulse, np.ones(40) / 40, mode="same")
    echo = np.convolve(shot, impulse)
    echo *= 0.35 * np.max(np.abs(shot)) / max(np.max(np.abs(echo)), 1e-9)
    echo[:len(shot)] += shot
    return echo


def motor(rng, seconds=2.0):
    """A small marine diesel: a firing beat of 12 Hz (4-stroke, a few cylinders at a low cruise), its exhaust thump
    and harmonics, a lumpy chug as the cylinders fire unevenly, valve clatter and a rumble of filtered noise. Built from
    whole numbers of cycles of everything in the loop's length, so it loops without a seam."""
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    beat = 12.0                     # firings per second: 24 in the loop (six rounds of four cylinders)
    out = np.zeros(n)
    for k, amp in ((1, 1.0), (2, 0.55), (3, 0.35), (4, 0.22), (6, 0.12), (8, 0.06)):
        out += amp * np.sin(2 * np.pi * beat * k * t + rng.uniform(0, 2 * np.pi))
    # each firing a thump, the cylinders not quite even (one in four a little weaker: a pattern of 4 beats)
    phase = (t * beat) % 1.0
    which = np.floor(t * beat).astype(int) % 4
    strength = np.array([1.0, 0.85, 0.95, 0.75])[which]
    pulse = (1.0 - np.exp(-phase * 60.0)) * np.exp(-phase * 5.0)   # a quick swell and a slower fall: no click
    out *= 0.55 + 0.6 * strength * pulse
    # rumble and clatter: noise shaped in the frequency domain over exactly the loop's length (so it's periodic too)
    spec = rng.standard_normal(n // 2 + 1) + 1j * rng.standard_normal(n // 2 + 1)
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    rumble = np.fft.irfft(spec * np.exp(-((f - 60.0) / 70.0) ** 2), n)
    clatter = np.fft.irfft(spec * np.exp(-((f - 1400.0) / 500.0) ** 2), n) * ((1.0 - np.exp(-phase * 80.0)) * np.exp(-phase * 14.0) + 0.15)
    out = out / np.max(np.abs(out))
    out += 0.5 * rumble / np.max(np.abs(rumble)) + 0.12 * clatter / np.max(np.abs(clatter))
    return out


def tail_swing(rng, seconds=1.15, peak=0.45):
    """A giant tail swung round through the air: a broad band of rushing air that rises in pitch and loudness as it
    comes past (peak seconds in) and drops away after it (as a passing train's does), a low rumble under it, and a
    soft thud of shoved air at the peak."""
    t = np.arange(int(seconds * RATE)) / RATE

    def air(time, freqs):
        x = time - peak
        centre = 150 + 520 * np.exp(-(x / 0.22) ** 2) + (60 if x < 0 else -40) * min(1.0, abs(x) / 0.4)
        return band(freqs, centre, 0.9) + 0.25 * band(freqs, 1400 + 600 * np.exp(-(x / 0.15) ** 2), 0.45)

    rush = shaped_noise(seconds, air, rng)
    rush /= np.max(np.abs(rush))
    swell = np.where(t < peak, smooth(t / peak) ** 1.6, np.exp(-(np.abs(t - peak) / 0.3) ** 1.3))
    rush *= swell * (1 - smooth((t - (seconds - 0.08)) / 0.08))

    rumble = np.sin(2 * np.pi * 38 * t) + 0.5 * np.sin(2 * np.pi * 57 * t + 0.7)
    rumble *= np.exp(-((t - peak) / 0.25) ** 2) * 0.35

    thud = np.sin(2 * np.pi * 52 * (t - peak)) * np.exp(-np.maximum(t - peak, 0) / 0.09) * (t >= peak) * 0.45
    return rush + rumble + thud


def trill_voice(t, start, length, pitch, rng):
    """One of King Ghidorah's heads crying out: a harsh harmonic cry, rising and falling, pulsed by a rattling trill."""
    x = np.clip((t - start) / length, 0.0, 1.0)
    on = (t >= start) & (t <= start + length)
    f0 = pitch * (360 + 300 * np.sin(np.pi * x) ** 0.7 - 80 * x)
    f0 *= 1 + 0.02 * np.sin(2 * np.pi * 6.5 * t)
    phase = 2 * np.pi * np.cumsum(f0) / RATE
    tone = sum(np.sin(k * phase + 0.3 * k) / k ** 0.9 for k in range(1, 9))
    trill_rate = 19 + 6 * np.sin(np.pi * x)
    pulse = (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(trill_rate) / RATE)) ** 3
    env = smooth(x / 0.08) * (1 - smooth((x - 0.75) / 0.25))
    rasp = rng.standard_normal(len(t)) * 0.25 * (0.3 + pulse)
    return (tone * (0.35 + 0.65 * pulse) + rasp) * env * on


def ghidorah_roar(rng, seconds=3.2):
    t = np.arange(int(seconds * RATE)) / RATE
    out = (trill_voice(t, 0.05, 2.4, 1.0, rng) + 0.85 * trill_voice(t, 0.25, 2.3, 1.13, rng)
           + 0.9 * trill_voice(t, 0.45, 2.3, 0.88, rng))
    # a little room: the cry thrown back off the hills
    echo = np.zeros_like(out)
    d = int(0.13 * RATE)
    echo[d:] = out[:-d] * 0.25
    return out + echo


def ghidorah_ambient(rng, seconds=1.3):
    t = np.arange(int(seconds * RATE)) / RATE
    return trill_voice(t, 0.02, 1.2, 0.8, rng)


def ghidorah_beam(rng, seconds=2.4):
    """A crackling electric discharge: a buzzing hum (a rough 110 Hz sawtooth, wavering) rising in, bursts of sparks
    (clicks of filtered noise) all through it, dying away at the end."""
    t = np.arange(int(seconds * RATE)) / RATE
    env = smooth(t / 0.15) * (1 - smooth((t - (seconds - 0.5)) / 0.5))
    f = 110 * (1 + 0.04 * np.sin(2 * np.pi * 3.0 * t)) * (1 + 0.15 * smooth(t / 1.0))
    saw = 2 * ((np.cumsum(f) / RATE) % 1.0) - 1
    hum = (saw + 0.5 * np.sign(np.sin(2 * np.pi * np.cumsum(2 * f) / RATE))) * 0.35

    def sparks(time, freqs):
        return band(freqs, 3500, 0.8) + 0.5 * band(freqs, 900, 0.6)

    crackle = shaped_noise(seconds, sparks, rng)
    crackle /= np.max(np.abs(crackle))
    gate = np.zeros_like(t)
    for start in rng.uniform(0, seconds, 140):
        gate += np.exp(-np.maximum(t - start, 0) / rng.uniform(0.004, 0.03)) * (t >= start) * rng.uniform(0.3, 1.0)
    return (hum + crackle * np.minimum(gate, 1.2)) * env


def ghidorah_rumble(rng, seconds=6.0):
    """Deep earth moving: low noise, grinding and swelling, with slow surges."""
    t = np.arange(int(seconds * RATE)) / RATE

    def low(time, freqs):
        return band(freqs, 45, 0.7) + 0.6 * band(freqs, 110, 0.6) + 0.15 * band(freqs, 400, 0.5)

    rum = shaped_noise(seconds, low, rng)
    rum /= np.max(np.abs(rum))
    surge = 0.55 + 0.45 * np.sin(2 * np.pi * 0.45 * t + 1.0) ** 2
    env = smooth(t / 1.2) * (1 - smooth((t - (seconds - 1.5)) / 1.5))
    sub = np.sin(2 * np.pi * 31 * t + 2 * np.sin(2 * np.pi * 0.7 * t)) * 0.4
    return (rum + sub) * surge * env


def ghidorah_crack(rng, seconds=1.6):
    """Rock splitting: a sharp crack, a grinding tear, then stones clattering down."""
    t = np.arange(int(seconds * RATE)) / RATE

    def rock(time, freqs):
        return band(freqs, 220 if time < 0.25 else 600, 0.9) + 0.4 * band(freqs, 2400, 0.6)

    tear = shaped_noise(seconds, rock, rng)
    tear /= np.max(np.abs(tear))
    tear *= np.exp(-t / 0.35) * 0.9 + 0.1 * np.exp(-t / 1.0)
    crack = rng.standard_normal(len(t)) * np.exp(-t / 0.015) * 1.2
    clatter = np.zeros_like(t)
    for start in rng.uniform(0.15, seconds - 0.2, 45):
        clatter += np.exp(-np.maximum(t - start, 0) / 0.012) * (t >= start) * rng.standard_normal(len(t)) * rng.uniform(0.1, 0.45)
    thump = np.sin(2 * np.pi * 48 * t) * np.exp(-t / 0.18) * 0.8
    return crack + tear + clatter + thump


def ghidorah_wing_spread(rng, seconds=2.2):
    """A huge, slow rush of air rising to the moment the membranes snap taut (1.5 s in)."""
    t = np.arange(int(seconds * RATE)) / RATE
    peak = 1.5

    def air(time, freqs):
        return band(freqs, 120 + 260 * smooth(time / peak), 0.8) + 0.3 * band(freqs, 1100, 0.5)

    rush = shaped_noise(seconds, air, rng)
    rush /= np.max(np.abs(rush))
    rush *= np.where(t < peak, smooth(t / peak) ** 1.5, np.exp(-(t - peak) / 0.35))
    snap = (rng.standard_normal(len(t)) * 0.6 + np.sin(2 * np.pi * 70 * (t - peak)) * 0.8) * np.exp(-np.maximum(t - peak, 0) / 0.07) * (t >= peak)
    return rush + snap


def ghidorah_wing_flap(rng, seconds=0.7):
    t = np.arange(int(seconds * RATE)) / RATE

    def air(time, freqs):
        return band(freqs, 90, 0.7) + 0.3 * band(freqs, 500, 0.5)

    whump = shaped_noise(seconds, air, rng)
    whump /= np.max(np.abs(whump))
    env = smooth(t / 0.12) * np.exp(-np.maximum(t - 0.12, 0) / 0.18)
    return whump * env + np.sin(2 * np.pi * 42 * t) * np.exp(-np.maximum(t - 0.1, 0) / 0.12) * smooth(t / 0.1) * 0.6


def write_ogg(samples, name):
    samples = samples / np.max(np.abs(samples)) * 0.89
    pcm = (samples * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        dst = os.path.join(OUT, name + ".ogg")
        subprocess.run([ffmpeg(), "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", dst], check=True)
    print("wrote", dst, "%.2f s" % (len(pcm) / RATE))


def main():
    wanted = set(sys.argv[1:])
    makers = ["godzilla_inhale", "nuke_alarm", "shinden_gun", "shinsei_maru_gun", "shinsei_maru_motor", "godzilla_minus_zero_tail_swing",
              "king_ghidorah_roar", "king_ghidorah_ambient", "king_ghidorah_beam", "king_ghidorah_rumble",
              "king_ghidorah_earth_crack", "king_ghidorah_wing_spread", "king_ghidorah_wing_flap"]
    if wanted - set(makers):
        sys.exit(__doc__)
    rng = np.random.default_rng(1954)   # (one stream, in this order, for the first five: as they were made)
    sounds = {
        "godzilla_inhale": lambda: inhale(rng),
        "nuke_alarm": lambda: alarm(rng),
        "shinden_gun": lambda: single_shot(rng),
        "shinsei_maru_gun": lambda: single_shot(rng, pitch=1.35, tail=0.18),
        "shinsei_maru_motor": lambda: motor(rng),
        "godzilla_minus_zero_tail_swing": lambda: tail_swing(np.random.default_rng(1203)),
        "king_ghidorah_roar": lambda: ghidorah_roar(np.random.default_rng(1964)),
        "king_ghidorah_ambient": lambda: ghidorah_ambient(np.random.default_rng(1965)),
        "king_ghidorah_beam": lambda: ghidorah_beam(np.random.default_rng(1991)),
        "king_ghidorah_rumble": lambda: ghidorah_rumble(np.random.default_rng(2019)),
        "king_ghidorah_earth_crack": lambda: ghidorah_crack(np.random.default_rng(2020)),
        "king_ghidorah_wing_spread": lambda: ghidorah_wing_spread(np.random.default_rng(2021)),
        "king_ghidorah_wing_flap": lambda: ghidorah_wing_flap(np.random.default_rng(2022)),
    }
    for name in makers:
        samples = sounds[name]()   # (made in order even when not written, so each keeps its random numbers)
        if not wanted or name in wanted:
            write_ogg(samples, name)


if __name__ == "__main__":
    main()
