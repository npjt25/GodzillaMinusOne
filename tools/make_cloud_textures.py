"""Builds the mushroom cloud's smoke sprite (client/render/NuclearCloud.java) from the add-on's.

    python3 tools/make_cloud_textures.py

GojiDraw's cloud draws its smoke sprites (textures/particles/smoke.png, 16 x 15, soft-edged) with Bedrock's
alpha-tested particle material, which tests the texture's own alpha against 0.5 before tinting: only the sprite's
denser texels show, solid, in the tint's colour, for the sprite's whole life (the tint's alpha changes nothing).
This writes that cut-out sprite (textures/effect/mushroom_smoke.png), so the renderer can draw it as it is.
"""
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone", "textures")


def main():
    sprite = np.array(Image.open(os.path.join(TEX, "particle", "smoke.png")).convert("RGBA"))
    shown = sprite[..., 3] >= 128
    out = np.zeros_like(sprite)
    out[shown] = sprite[shown]
    out[..., 3] = np.where(shown, 255, 0)
    os.makedirs(os.path.join(TEX, "effect"), exist_ok=True)
    Image.fromarray(out, "RGBA").save(os.path.join(TEX, "effect", "mushroom_smoke.png"))
    print("wrote the mushroom cloud's smoke sprite: %d of its texels pass the alpha test" % shown.sum())


if __name__ == "__main__":
    main()
