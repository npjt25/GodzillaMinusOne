"""Renders the mod's own item icons from their models (32x32, transparent background).

    python3 tools/make_icons.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_preview as rp  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
KEY = (255, 0, 255)

# item: (model, texture, yaw, pitch, bones to frame (None = all))
ICONS = {
    "odo_island_monster_spawn_egg": ("odo_island_monster", "odo_island_monster", 125, 8, {"head", "jaw", "neck2"}),
    "searchlight": ("searchlight", "searchlight", 140, 18, None),
    "nuclear_bomb": ("nuclear_bomb", "nuclear_bomb", 100, 10, None),
}


def icon(item, model, texture, yaw, pitch, fit):
    geo = os.path.join(ASSETS, "geo", "entity", model + ".geo.json")
    tex = os.path.join(ASSETS, "textures", "entity", texture + ".png")
    if not os.path.exists(geo):
        return False
    tmp = os.path.join(ROOT, "build", "icon_tmp.png")
    os.makedirs(os.path.dirname(tmp), exist_ok=True)
    rp.render(geo, tex, tmp, yaw, pitch, 256, bg=KEY, fit_bones=fit)
    a = np.asarray(Image.open(tmp).convert("RGB")).astype(int)
    alpha = (np.abs(a - np.array(KEY)).sum(-1) > 30).astype(np.uint8) * 255
    img = Image.fromarray(np.dstack([a.astype(np.uint8), alpha]), "RGBA")
    box = img.getbbox()
    img = img.crop(box)
    side = max(img.width, img.height)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    small = square.resize((30, 30), Image.LANCZOS)
    padded = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    padded.paste(small, (1, 1))
    arr = np.asarray(padded).copy()
    arr[..., 3] = np.where(arr[..., 3] > 100, 255, 0)
    # a dark outline so it reads on any background
    solid = arr[..., 3] > 0
    edge = np.zeros_like(solid)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(solid, dy, 0), dx, 1)
    edge &= ~solid
    arr[edge] = [20, 20, 22, 255]
    Image.fromarray(arr, "RGBA").save(os.path.join(ASSETS, "textures", "item", item + ".png"))
    with open(os.path.join(ASSETS, "models", "item", item + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "godzillaminusone:item/" + item}}, f, indent=2)
        f.write("\n")
    os.remove(tmp)
    return True


def main():
    for item, args in ICONS.items():
        print(item, "ok" if icon(item, *args) else "skipped (no model yet)")


if __name__ == "__main__":
    main()
