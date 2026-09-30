"""The burns the atomic breath leaves round Godzilla's mouth (drawn over him after he fires, fading as they heal:
client.render.GodzillaRenderer): raw, torn, blistered and charred flesh, painted onto the texels of the add-on's
texture layout that only his head, jaw and upper neck use.

    python3 tools/godzilla_burns.py

  * godzilla_burns.png: the wounds; their alpha is the order they heal in (the edges close up first),
  * godzilla_burns_glow.png: the flesh still glowing hot just after the beam.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import noise  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
GEO = os.path.join(ASSETS, "geo", "entity", "godzilla.geo.json")
TEX = os.path.join(ASSETS, "textures", "entity")
TEXELS = 2                        # texels per UV unit, as in the add-on's textures (512 px for its 256 UV space)

BURN_BONES = ("Head", "Jew", "neck2")
MOUTH = np.array([-0.04, 71.9, -31.5])    # Bedrock pixels (the beam leaves the mouth here: edit_animations.py)


def build_burns():
    """Raw, blistered, charred flesh round the mouth, on the texels only the head, jaw and upper neck use."""
    import render_preview as rp
    bones, tw, th = rp.load_geo(GEO)
    size = 256 * TEXELS
    shared = np.zeros((size, size), bool)
    weight = np.zeros((size, size))
    for b in bones:
        mouth_bone = b["name"] in BURN_BONES
        for c in b.get("cubes", []):
            for name, corners, uvs in rp.face_list(c, tw, th):
                px = uvs * size
                u0, v0 = np.floor(px.min(0)).astype(int)
                u1, v1 = np.ceil(px.max(0)).astype(int)
                u0, v0, u1, v1 = max(u0, 0), max(v0, 0), min(u1, size), min(v1, size)
                if u1 <= u0 or v1 <= v0:
                    continue
                if not mouth_bone:
                    shared[v0:v1, u0:u1] = True
                    continue
                # where each texel of the face is on him (corners: top-left, top-right, bottom-right, bottom-left)
                uu, vv = np.meshgrid(np.linspace(0, 1, u1 - u0), np.linspace(0, 1, v1 - v0))
                p = (corners[0] * ((1 - uu) * (1 - vv))[..., None] + corners[1] * (uu * (1 - vv))[..., None]
                     + corners[2] * (uu * vv)[..., None] + corners[3] * ((1 - uu) * vv)[..., None])
                mouth = np.array([-MOUTH[0], MOUTH[1], MOUTH[2]])
                d = np.linalg.norm((p - mouth) * np.array([1.0, 1.3, 0.8]), axis=-1)
                w = np.exp(-(d / 5.5) ** 2) * np.clip((75.6 - p[..., 1]) / 1.2, 0.0, 1.0)   # below the eyes
                weight[v0:v1, u0:u1] = np.maximum(weight[v0:v1, u0:u1], w)
    weight[shared] = 0.0

    r = np.random.default_rng(91)
    from_hide = noise.hide_detail(size, r, cell=5)
    blotch = noise.smooth_noise(size, 24, r) * 0.6 + noise.smooth_noise(size, 55, r) * 0.4
    wound = weight * 1.25 + (blotch - 0.5) * 0.9
    burnt = wound > 0.32
    core = np.clip((wound - 0.32) / 0.6, 0.0, 1.0)

    img = np.zeros((size, size, 4))
    char = np.array([34.0, 26.0, 24.0])
    raw_dark = np.array([96.0, 18.0, 16.0])
    raw = np.array([188.0, 58.0, 46.0])
    pink = np.array([222.0, 128.0, 112.0])
    col = (char[None, None] * (1 - np.clip(core * 3, 0, 1))[..., None]
           + raw_dark[None, None] * np.clip(core * 3, 0, 1)[..., None] * (1 - np.clip(core * 2 - 0.6, 0, 1))[..., None]
           + raw[None, None] * np.clip(core * 2 - 0.6, 0, 1)[..., None])
    # torn, stringy flesh: the hide's own cracks and nodules show through as darker and lighter tissue
    col *= np.clip(from_hide, 0.6, 1.35)[..., None]
    # blisters: pale, glistening bubbles in the raw flesh
    f1, _ = noise.worley(size, 4, r)
    blister = (f1 < 0.9) & (r.random((size, size)) < 0.5) & (core > 0.15) & (core < 0.85)
    col[blister] = pink * (0.9 + 0.2 * r.random((int(blister.sum()), 1)))
    col[blister & (f1 < 0.4)] = [246, 220, 196]
    img[..., :3] = np.clip(col, 0, 255)
    # alpha: the order it heals in (the rim first, the deepest wounds last), never below the cut-out threshold
    img[..., 3] = np.where(burnt, 0.22 + 0.78 * core ** 0.8, 0.0) * 255
    Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(TEX, "godzilla_burns.png"))

    glow = np.zeros((size, size, 4))
    g1, g2 = noise.worley(size, 6, r)
    crack = (np.abs(g2 - g1) < 0.8) & (core > 0.35)
    glow[crack] = [255, 118, 34, 255]
    glow[crack & (core > 0.7)] = [255, 196, 90, 255]
    Image.fromarray(glow.astype(np.uint8), "RGBA").save(os.path.join(TEX, "godzilla_burns_glow.png"))
    print("burns: %d texels" % burnt.sum())


if __name__ == "__main__":
    build_burns()
