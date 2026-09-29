"""Synthesises the mod's own sound effects (the add-on has none for these) as mono Ogg Vorbis.

    python3 tools/make_sounds.py

  * godzilla_inhale.ogg  - Godzilla's in-breath before the atomic breath: exactly as long as the in-breath
                           in the charge animation (tools/edit_animations.py, INHALE_LENGTH = 4 s).
  * nuke_whistle.ogg     - the falling nuclear bomb's whistle over its last seconds.

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
WHISTLE_SECONDS = 7.0


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


def whistle(rng):
    """A falling bomb: a whistle sliding down in pitch and getting louder, with wind."""
    seconds = WHISTLE_SECONDS
    t = np.arange(int(seconds * RATE)) / RATE
    p = t / seconds
    freq = 1900 * (0.28 / 1.0) ** p                  # 1900 Hz down to ~530 Hz
    freq *= 1 + 0.006 * np.sin(2 * np.pi * 6 * t)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    tone = np.sin(phase) + 0.25 * np.sin(2 * phase) + 0.1 * np.sin(3 * phase)
    loud = (0.15 + 0.85 * p ** 1.6) * smooth(p / 0.05) * (1 - smooth((p - 0.985) / 0.015))

    def wind(time, freqs):
        return band(freqs, 900 + 500 * (time / seconds), 0.9)

    air = shaped_noise(seconds, wind, rng)
    air /= np.max(np.abs(air))
    return tone * loud * 0.7 + air * loud * 0.25


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
    write_ogg(whistle(rng), "nuke_whistle")


if __name__ == "__main__":
    main()
