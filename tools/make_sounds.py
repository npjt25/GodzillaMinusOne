"""Synthesises the mod's own sound effects (the add-on has none for these) as mono Ogg Vorbis.

    python3 tools/make_sounds.py

  * godzilla_inhale.ogg  - Godzilla's in-breath before the atomic breath: exactly as long as the in-breath
                           in the charge animation (tools/edit_animations.py, INHALE_LENGTH = 4 s).
  * nuke_alarm.ogg       - the air-raid siren that wails from the moment a nuclear bomb is called in until it goes
                           off: exactly its 20 second fall (entity/NuclearBombEntity.java, FALL_TICKS).
  * shinden_gun.ogg      - one shot of the Shinden's cannons (one per click): the first of the three shots in the
                           add-on's kplane_fire.ogg burst, with a short echo instead of the next two.
  * shinsei_maru_gun.ogg - one shot of the Shinsei Maru's Type 93 13 mm machine gun: the same shot, lighter and sharper.

Needs numpy and imageio-ffmpeg (for the Vorbis encoder).
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
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


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
    if len(sys.argv) != 1:
        sys.exit(__doc__)
    rng = np.random.default_rng(1954)
    write_ogg(inhale(rng), "godzilla_inhale")
    write_ogg(alarm(rng), "nuke_alarm")
    write_ogg(single_shot(rng), "shinden_gun")
    write_ogg(single_shot(rng, pitch=1.35, tail=0.18), "shinsei_maru_gun")


if __name__ == "__main__":
    main()
