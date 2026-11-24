"""Redraws only the corridor-room props (bookcase, cubicle panels) without touching the rest of Level 1's assets.

tools/make_level1.py draws everything from scratch and has fallen behind the hand-made models and textures added since,
so a full run would overwrite them; run this instead after changing book_shelf() or cubicle_panel().
"""
import make_level1 as ml
import make_textures as mt

NAMES = ["book_shelf", "cubicle_panel", "cubicle_panel_1", "cubicle_panel_2", "cubicle_panel_3", "cubicle_trim"]

mt.OUT = ml.TEXTURES
textures = ml.all_textures()
for name in NAMES:
    mt.save(name, textures[name])
models = ml.all_models()
# The cubicle panel models and blockstate come from tools/make_cubicle_models.py.
for name in ("book_shelf", "cubicle_trim"):
    if name in models:
        ml.write_json(ml.MODELS / f"{name}.json", models[name])
states = ml.all_blockstates()
for name in ("book_shelf",):
    ml.write_json(ml.ASSETS / f"blockstates/{name}.json", states[name])
print("room props redrawn")
