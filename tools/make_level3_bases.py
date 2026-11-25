"""Base 128 px textures, models, blockstates, items and loot tables for Level 3 Batch 3's 5 faction blocks:
security_camera, foj_white_wall, jerry_mural, blue_station_light, uec_apparatus.

Run: python tools/make_level3_bases.py
"""
import copy
import json
import sys
from pathlib import Path

import numpy as np

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_level1 as l1  # noqa: E402
import make_level3 as l3  # noqa: E402
import make_textures as mt  # noqa: E402
from make_textures import X, Y, box, flat, metal, paint, step, tint  # noqa: E402

ASSETS = l3.ASSETS
TEXTURES = l3.TEXTURES
MODELS = l3.MODELS
NS = l3.NS
el, sides = l3.el, l3.sides


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def textures():
    mt.OUT = TEXTURES

    # Security camera body + glowing red lens
    cam = metal((156, 160, 158), 0.45)
    paint(cam, box(32, 36, 96, 92, 1), (36, 38, 42), height=1.2, smooth=0.8)
    lens = 1 - step(14, 18, np.hypot(X - 64, Y - 64))
    paint(cam, lens, (18, 22, 28), height=-0.5, smooth=0.95, f0=220)
    led = 1 - step(4, 6, np.hypot(X - 86, Y - 46))
    paint(cam, led, (220, 42, 42), smooth=0.9)
    cam.glow = np.maximum(cam.glow, led * 0.9)
    mt.save("security_camera", cam)

    # F.O.J. Blue Salvation white drywall
    fw = l1.concrete((222, 224, 226), 1310, smooth=0.18, porosity=35)
    seam = np.maximum(1 - step(0.6, 1.5, np.minimum(X, mt.SIZE - X)), 1 - step(0.6, 1.5, np.minimum(Y, mt.SIZE - Y)))
    paint(fw, seam, (192, 196, 200), height=-0.8)
    mt.save("foj_white_wall", fw)

    # Jerry mural: blue macaw silhouette on white/blue drywall
    jm = copy.deepcopy(fw)
    paint(jm, box(10, 10, 118, 118, 1), (206, 214, 224), height=0.2)
    body = 1 - step(18, 22, np.hypot((X - 64) * 1.3, (Y - 68) * 0.75))
    head = 1 - step(11, 14, np.hypot(X - 62, Y - 40))
    wing = 1 - step(12, 16, np.hypot((X - 72) * 1.4, (Y - 72) * 0.8))
    paint(jm, np.maximum(body, head), (44, 118, 212), smooth=0.35)
    paint(jm, wing, (28, 76, 168), smooth=0.35)
    beak = 1 - step(5, 7, np.hypot((X - 50) * 1.1, Y - 42))
    paint(jm, beak, (218, 178, 62), smooth=0.4)
    eye = 1 - step(2.2, 3.2, np.hypot(X - 59, Y - 38))
    paint(jm, eye, (240, 240, 240))
    mt.save("jerry_mural", jm)

    # Blue-tinted station light lens
    bl = l3.lens((96, 166, 245), 0.85)
    mt.save("light_lens_blue", bl)

    # U.E.C. Scylla Colony apparatus
    ua_side = metal((68, 72, 76), 0.4)
    paint(ua_side, box(16, 16, 112, 112, 1), (52, 56, 60), height=-0.8)
    mt.save("uec_apparatus_side", ua_side)

    ua_front = copy.deepcopy(ua_side)
    screen = box(24, 24, 104, 66, 1)
    paint(ua_front, screen, (20, 42, 46), height=-0.6, smooth=0.9)
    wave = (1 - step(1.5, 2.8, np.abs(Y - (45 + np.sin(X * 0.2) * 10)))) * screen
    paint(ua_front, wave, (80, 232, 168))
    ua_front.glow = np.maximum(ua_front.glow, wave * 0.95)
    knob1 = 1 - step(6, 8, np.hypot(X - 42, Y - 92))
    knob2 = 1 - step(6, 8, np.hypot(X - 86, Y - 92))
    paint(ua_front, np.maximum(knob1, knob2), (168, 42, 36), height=1.2)
    mt.save("uec_apparatus_front", ua_front)


def models_and_states():
    m = {}
    m["security_camera"] = l3.custom({"c": "security_camera", "m": "lock"}, [
        el((5, 8, 0), (11, 14, 7), {**sides("#m", ("north", "east", "west", "up", "down")), "south": l3.face("#c")}),
        el((7, 10, 7), (9, 12, 8), sides("#m")),
    ], particle="security_camera")
    m["foj_white_wall"] = l3.cube("foj_white_wall")
    m["jerry_mural"] = l3.custom({"p": "jerry_mural", "r": "lock"}, [
        el((1, 1, 0), (15, 15, 1), {**sides("#r", ("north", "east", "west", "up", "down")), "south": l3.face("#p")}),
    ], particle="jerry_mural")
    m["blue_station_light"] = l3.fixture("light_lens_blue")
    m["blue_station_light"]["elements"][-1]["light_emission"] = 14
    m["uec_apparatus"] = l3.custom({"f": "uec_apparatus_front", "s": "uec_apparatus_side"}, [
        el((2, 0, 2), (14, 14, 14), {**sides("#s", ("north", "east", "west", "up", "down")), "south": l3.face("#f")}),
        el((5, 14, 5), (11, 16, 11), sides("#s")),
    ], particle="uec_apparatus_side")

    for name, model in m.items():
        write_json(MODELS / f"{name}.json", model)

    states = {
        "security_camera": l3.facing_state("security_camera"),
        "foj_white_wall": l3.simple("foj_white_wall"),
        "jerry_mural": l3.facing_state("jerry_mural"),
        "blue_station_light": l3.axis_state("blue_station_light"),
        "uec_apparatus": l3.facing_state("uec_apparatus"),
    }
    for name, state in states.items():
        write_json(ASSETS / f"blockstates/{name}.json", state)
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": NS + name}})
        write_json(mt.ROOT / f"src/main/resources/data/noclip_backrooms/loot_table/blocks/{name}.json", l3.loot_table(name))


def main():
    textures()
    models_and_states()
    print("Level 3 Batch 3 base assets written.")


if __name__ == "__main__":
    main()
