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

# item: (model, texture, yaw, pitch, bones to frame (None = all), turn the picture by (degrees))
ICONS = {
    "odo_island_monster_spawn_egg": ("odo_island_monster", "odo_island_monster", 125, 8, {"bone12", "bone5", "bone11"}, 0),
    "searchlight": ("searchlight", "searchlight", 25, 12, None, 0),
    "nuclear_bomb": ("nuclear_bomb", "nuclear_bomb", 30, 10, None, -135),
    "shinsei_maru": ("shinsei_maru", "shinsei_maru", 60, 18, None, 0),
}


def icon(item, model, texture, yaw, pitch, fit, turn):
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
    if turn:
        img = img.rotate(turn, resample=Image.NEAREST, expand=True)
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


def save_item(item, arr):
    Image.fromarray(arr, "RGBA").save(os.path.join(ASSETS, "textures", "item", item + ".png"))
    with open(os.path.join(ASSETS, "models", "item", item + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "godzillaminusone:item/" + item}}, f, indent=2)
        f.write("\n")


def remote_detonator():
    """Drawn by hand: an olive-drab radio detonator with a red firing button and a telescopic antenna."""
    a = np.zeros((32, 32, 4), np.uint8)

    def rect(x0, y0, x1, y1, rgb):
        a[y0:y1 + 1, x0:x1 + 1] = list(rgb) + [255]

    # antenna, leaning back to the right, with a red tip
    for i in range(12):
        x, y = 20 + i // 2, 12 - i
        a[y, x] = [196, 200, 204, 255]
        a[y, x + 1] = [120, 124, 128, 255]
    rect(25, 0, 27, 2, (170, 30, 24))
    a[0, 25] = [230, 80, 60, 255]
    # the box
    rect(6, 12, 23, 29, (78, 86, 52))
    rect(6, 12, 23, 13, (104, 112, 70))              # lit top edge
    rect(6, 12, 7, 29, (96, 104, 64))                # lit left edge
    rect(22, 14, 23, 29, (54, 60, 36))               # shaded right edge
    rect(6, 28, 23, 29, (46, 50, 30))                # shaded bottom
    # the firing button in its guard
    rect(10, 9, 17, 11, (40, 40, 38))
    rect(11, 8, 16, 10, (196, 36, 28))
    rect(12, 8, 14, 8, (240, 110, 90))
    # a dial, a lamp and a grille
    rect(9, 16, 14, 21, (30, 32, 26))
    rect(10, 17, 13, 20, (214, 206, 170))
    a[18, 11] = a[19, 12] = [30, 30, 30, 255]
    rect(17, 16, 19, 18, (250, 196, 40))
    for y in (23, 25):
        rect(9, y, 19, y, (40, 44, 28))
    # outline (no wrapping round the edges)
    solid = np.pad(a[..., 3] > 0, 1)
    edge = solid[:-2, 1:-1] | solid[2:, 1:-1] | solid[1:-1, :-2] | solid[1:-1, 2:]
    edge &= ~solid[1:-1, 1:-1]
    a[edge] = [20, 20, 22, 255]
    save_item("remote_detonator", a)


def main():
    for item, args in ICONS.items():
        print(item, "ok" if icon(item, *args) else "skipped (no model yet)")
    remote_detonator()
    print("remote_detonator ok")


if __name__ == "__main__":
    main()
