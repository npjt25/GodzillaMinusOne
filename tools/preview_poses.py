"""Preview of the posed Godzilla statues (entity.GodzillaStatueEntity) as you'd see them in the game: each one has
turned to face you and you stand in front of it, looking up from low down. Their glow is drawn as the statue
renderer draws it (eyes; the tail's plates, or every plate and the inside of the mouth), on a ground of one-block
squares with a player beside his feet for scale.

    python3 tools/preview_poses.py [out.png]        (default docs/poses_preview.png)

Run after tools/edit_animations.py (the poses) and tools/godzilla_mouth_glow.py.
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_preview as rp  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "godzillaminusone")
GEO = os.path.join(ASSETS, "geo", "entity", "godzilla.geo.json")
TEX = os.path.join(ASSETS, "textures", "entity", "godzilla.png")
ANIM = os.path.join(ASSETS, "animations", "entity", "godzilla.animation.json")
GLOW = os.path.join(ASSETS, "textures", "entity")
SCALE = 7.83                                    # client.render.GodzillaRenderer.SCALE
TAIL_PLATES = {"T%d" % i for i in range(15, 23)}
ALL_PLATES = {"T"} | {"T%d" % i for i in range(2, 23)}
# (pose, statue, glow: as GodzillaStatueEntity.Stance)
POSES = [
    ("pose_charging_roar", "godzilla_pose_charging_roar", "tail"),
    ("pose_tail_whip", "godzilla_pose_tail_whip", None),
    ("pose_sky_roar", "godzilla_pose_sky_roar", None),
    ("pose_poster_snarl", "godzilla_pose_poster_snarl", "charged"),
    ("pose_tail_hook", "godzilla_pose_tail_hook", None),
    ("pose_hunched_snarl", "godzilla_pose_hunched_snarl", None),
]
# in model pixels (his model faces -z, towards you): low down in front of him, looking up
CAMERA = {"eye": (0.0, 14.0, -165.0), "target": (0.0, 50.0, 0.0), "fov": 52.0}
W, H = 420, 520


def overlays(glow):
    out = [(os.path.join(GLOW, "godzilla_glow.png"), {"Head"}, 1.0)]
    if glow == "tail":
        out.append((os.path.join(GLOW, "godzilla_charged_glow.png"), TAIL_PLATES, 1.0))
    elif glow == "charged":
        out.append((os.path.join(GLOW, "godzilla_charged_glow.png"), ALL_PLATES, 1.0))
        out.append((os.path.join(GLOW, "godzilla_mouth_glow.png"), {"Head", "Jew"}, 1.0))
    return out


def main(out):
    sheet = Image.new("RGB", (W * len(POSES), H))
    tmp = out + ".part.png"
    ground = rp.scale_props(SCALE, (-40, 40, -80, 40), player_at=(-6.0, -12.0))
    for i, (pose, statue, glow) in enumerate(POSES):
        rp.render(GEO, TEX, tmp, size=H, width=W, pose=rp.animation_pose(ANIM, "animation.gojira." + pose, 0.0),
                  camera=CAMERA, overlays=overlays(glow), bloom=0.5 if glow else 0.0, props=ground, hide=("B",),
                  bg=((128, 140, 150), (78, 86, 94)))
        panel = Image.open(tmp)
        ImageDraw.Draw(panel).text((8, 8), "/summon godzillaminusone:" + statue, fill=(255, 255, 255))
        sheet.paste(panel, (W * i, 0))
    os.remove(tmp)
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs", "poses_preview.png"))
