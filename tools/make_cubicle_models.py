"""Writes the thin cubicle panel models, blockstate and item model.

A panel is a 2 px post with a 7 px arm toward each neighbouring panel; the four pinned-up looks (style 0-3) are the
textures cubicle_panel, cubicle_panel_1..3, split across the two arms of a straight run so the picture reads whole.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
NS = "noclip_backrooms:block/level1/"
TEXTURES = ["cubicle_panel", "cubicle_panel_1", "cubicle_panel_2", "cubicle_panel_3"]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def face(texture, uv=None):
    f = {"texture": texture}
    if uv:
        f["uv"] = uv
    return f


POST = {"from": [7, 0, 7], "to": [9, 16, 9],
        "faces": {s: face("#trim") for s in ("north", "south", "east", "west", "up", "down")}}
# North arm, then the south arm mirrored by hand for the item model.
NORTH_ARM = {"from": [7, 0, 0], "to": [9, 16, 7],
             "faces": {"north": face("#trim"), "up": face("#trim"), "down": face("#trim"),
                       "east": face("#side", [9, 0, 16, 16]), "west": face("#side", [0, 0, 7, 16])}}
SOUTH_ARM = {"from": [7, 0, 9], "to": [9, 16, 16],
             "faces": {"south": face("#trim"), "up": face("#trim"), "down": face("#trim"),
                       "east": face("#side", [0, 0, 7, 16]), "west": face("#side", [9, 0, 16, 16])}}


def model(side, elements):
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NS + "cubicle_trim", "trim": NS + "cubicle_trim", "side": NS + side},
            "elements": elements}


write(ASSETS / "models/block/level1/cubicle_panel_post.json", model(TEXTURES[0], [POST]))
parts = [{"apply": {"model": NS + "cubicle_panel_post"}}]
for k, tex in enumerate(TEXTURES):
    write(ASSETS / f"models/block/level1/cubicle_panel_arm_{k}.json", model(tex, [NORTH_ARM]))
    for direction, turn in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        apply = {"model": NS + f"cubicle_panel_arm_{k}"}
        if turn:
            apply["y"] = turn
        parts.append({"when": {"AND": [{direction: "true"}, {"style": str(k)}]}, "apply": apply})
write(ASSETS / "blockstates/cubicle_panel.json", {"multipart": parts})
write(ASSETS / "models/block/level1/cubicle_panel.json", model(TEXTURES[0], [POST, NORTH_ARM, SOUTH_ARM]))
for k in (1, 2, 3):
    path = ASSETS / f"models/block/level1/cubicle_panel_{k}.json"
    if path.exists():
        path.unlink()
print("cubicle models written")
