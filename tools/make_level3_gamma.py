"""Base 128 px textures, models, blockstates, items and loot tables for Base Gamma's own blocks (Level 3):
daylight_panel (the atrium's simulated daylight) and checkpoint_door (the metal detector at the gate).

Run: python tools/make_level3_gamma.py
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_level3 as l3  # noqa: E402
import make_textures as mt  # noqa: E402
from make_textures import X, Y, box, flat, metal, paint, step  # noqa: E402

ASSETS = l3.ASSETS
MODELS = l3.MODELS
NS = l3.NS
el, sides = l3.el, l3.sides


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def textures():
    mt.OUT = l3.TEXTURES
    # A frosted diffuser in a thin aluminium grid, brighter in the middle of each pane.
    panel = flat((236, 238, 230), 0.6, 20)
    pane = np.minimum(np.minimum(X % 64, 63 - X % 64), np.minimum(Y % 64, 63 - Y % 64))
    paint(panel, 1 - step(1, 3, pane), (176, 180, 178), height=1.5, f0=200)
    paint(panel, step(6, 30, pane) * 0.5, (255, 253, 240))
    mt.save("daylight_panel", panel)

    frame = metal((112, 118, 122), 0.4)
    paint(frame, box(52, 8, 76, 120, 1), (52, 56, 60), height=1.2)
    for cy in range(16, 128, 16):
        paint(frame, 1 - step(2, 3, np.hypot(X - 64, Y - cy)), (120, 220, 140), glow=0.6)
    mt.save("checkpoint_frame", frame)

    bar = flat((226, 182, 40), 0.3)
    paint(bar, ((X + Y) // 16 % 2).astype(float), (32, 32, 30))
    mt.save("checkpoint_bar", bar)


def models_and_states():
    write_json(MODELS / "daylight_panel.json", l3.cube("daylight_panel"))
    frame = {"f": "checkpoint_frame", "b": "checkpoint_bar"}
    posts = [el((0, 0, 6), (3, 16, 10), sides("#f")), el((13, 0, 6), (16, 16, 10), sides("#f"))]
    write_json(MODELS / "checkpoint_door_0.json", l3.custom(frame, posts + [el((3, 7, 7), (13, 9, 9), sides("#b"))], particle="checkpoint_frame"))
    write_json(MODELS / "checkpoint_door_1.json", l3.custom(frame, posts + [el((3, 12, 6), (13, 16, 10), sides("#f"))], particle="checkpoint_frame"))

    write_json(ASSETS / "blockstates/daylight_panel.json", l3.simple("daylight_panel"))
    write_json(ASSETS / "blockstates/checkpoint_door.json", l3.part_state("checkpoint_door", 2))
    write_json(ASSETS / "items/daylight_panel.json", {"model": {"type": "minecraft:model", "model": NS + "daylight_panel"}})
    write_json(ASSETS / "items/checkpoint_door.json", {"model": {"type": "minecraft:model", "model": NS + "checkpoint_door_0"}})
    write_json(mt.ROOT / "src/main/resources/data/noclip_backrooms/loot_table/blocks/daylight_panel.json", l3.loot_table("daylight_panel"))


def main():
    textures()
    models_and_states()
    print("Base Gamma blocks written.")


if __name__ == "__main__":
    main()
