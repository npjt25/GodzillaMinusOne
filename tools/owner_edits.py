"""The mod owner's own edits to the models and textures, made in Blockbench, kept here as data
(tools/owner_edits/) and laid over what the generators make, so that re-running a generator never loses them:

- <model>.geo.edits.json: bones the owner changed, each as the owner left it ({"pivot", "rotation" (absent: none),
  "cubes"}), replacing the generated bone of that name (its cubes, pivot and rotation; its place, parent and the rest
  of it as generated).
- <texture>.paint.json: texels the owner repainted, [x, y, [r, g, b, a]] each, set over the generated texture.

convert_addon.py (Godzilla) and make_minus_zero.py (Minus Zero) call these as they write. King Ghidorah's (his knee
spurs taken off) is in make_ghidorah.py itself.
"""
import json
import os

HERE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "owner_edits")


def apply_geo(data, model):
    """Lay the owner's bones for `model` (e.g. "godzilla_minus_zero") over `data`, a geo.json's contents. Returns how
    many bones were replaced."""
    path = os.path.join(HERE, model + ".geo.edits.json")
    if not os.path.exists(path):
        return 0
    edits = json.load(open(path))
    done = 0
    for bone in data["minecraft:geometry"][0]["bones"]:
        e = edits.get(bone["name"])
        if e is None:
            continue
        bone["pivot"] = e["pivot"]
        if e.get("rotation"):
            bone["rotation"] = e["rotation"]
        else:
            bone.pop("rotation", None)
        if e["cubes"]:
            bone["cubes"] = e["cubes"]
        else:
            bone.pop("cubes", None)
        done += 1
    missing = set(edits) - {b["name"] for b in data["minecraft:geometry"][0]["bones"]}
    if missing:
        raise SystemExit("owner's edits for bones %s of %s: no such bones" % (sorted(missing), model))
    return done


def apply_paint(image, texture):
    """Set the texels the owner repainted on `texture` (e.g. "godzilla_minus_zero") in `image` (a PIL RGBA image of
    it). Returns how many."""
    path = os.path.join(HERE, texture + ".paint.json")
    if not os.path.exists(path):
        return 0
    texels = json.load(open(path))
    px = image.load()
    for x, y, rgba in texels:
        px[x, y] = tuple(rgba)
    return len(texels)
