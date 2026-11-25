"""The realistic items pack: Gemini's items, weapons, gear and props redrawn in the levels' style.
Magic items stay as Gemini made them (the user's choice, 2026-11-25: they come from later levels and are meant to look
like fantasy, not like the worn things found in the Backrooms).

Box models with 64-pixel materials made from CC0 photos plus wear and grime (tools/item_look/mats.py), each with labPBR
_n / _s. Everything is written into the built-in resource pack "realistic_items" (on by default; core.look.ItemLook),
which replaces the listed items' models while it is on; Gemini's own models stay as they are underneath.

  tools/item_look/lib.py      helpers: textures, materials, boxes, poses
  tools/item_look/mats.py     the material library
  tools/item_look/melee.py    the 41 melee weapons
  tools/item_look/guns.py     firearms, ammunition and attachments
  tools/item_look/ported.py   the eight trial pieces (approved 2026-11-25), until their categories are redone
  tools/item_look/bake.py     baked shading for box models (Blender)
  tools/item_look/mesh.py     items modelled in Blender with real shapes (blender_mesh.py)

Run from the repository root:  py -I tools/make_realistic_items.py
The pack's assets are written afresh each time.
"""
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "item_look"))
sys.dont_write_bytecode = True

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import bake  # noqa: E402
import lib  # noqa: E402
import mats  # noqa: E402, F401  (registers the materials)
import guns  # noqa: E402
import mesh  # noqa: E402
import melee  # noqa: E402
import ported  # noqa: E402

ROOT = lib.ROOT


def items():
    """id -> (elements, display) for every redrawn item."""
    out = {}
    for name, build in melee.WEAPONS.items():
        out[name] = build()
    for name, build in guns.ITEMS.items():
        out[name] = build()
    for name, build in ported.ITEMS.items():
        out.setdefault(name, build())
    return out


def props():
    return {name: build() for name, build in ported.PROPS.items()}


# Baked in Blender (bake.py): shading, worn edges and dust painted onto a per-item atlas of the box model.
BAKED = set()
# Modelled in Blender with real shapes (mesh.py, blender_mesh.py); being tried on these first.
MESH = {"west_arming_sword", "gun_pistol_9mm", "prop_office_chair"}


def main():
    if lib.OUT.exists():
        shutil.rmtree(lib.OUT)
    done = []
    all_items, all_props = items(), props()
    baked = bake.bake({**{n: (e, "item") for n, (e, _) in all_items.items() if n in BAKED},
                       **{n: (e, "block") for n, e in all_props.items() if n in BAKED}})
    for name, (elements, display) in all_items.items():
        if name in baked:
            elements, atlas = baked[name]
            lib.write(lib.OUT / "models/item/look" / f"{name}.json", lib.model(elements, "item", display, atlas=atlas))
        else:
            lib.write(lib.OUT / "models/item/look" / f"{name}.json", lib.model(elements, "item", display))
        lib.write(lib.OUT / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:item/look/{name}"}})
        done.append(name)
    for name, elements in all_props.items():
        atlas = None
        if name in baked:
            elements, atlas = baked[name]
        lib.write(lib.OUT / "models/block/look" / f"{name}.json", lib.model(elements, "block", parent="minecraft:block/block", atlas=atlas))
        lib.write(lib.OUT / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:block/look/{name}"}})
        states = json.loads((lib.ASSETS / "blockstates" / f"{name}.json").read_text(encoding="utf8"))
        for variant in states.get("variants", {}).values():
            for v in variant if isinstance(variant, list) else [variant]:
                v["model"] = f"noclip_backrooms:block/look/{name}"
        lib.write(lib.OUT / "blockstates" / f"{name}.json", states)
        done.append(name)
    meshes = mesh.run({n: (lib.SHIFTS.get(n, (0.0, 0.0, 0.0)), "block" if n in all_props else "item") for n in MESH})
    for name, (mesh_id, texture) in meshes.items():
        base = f"noclip_backrooms:{'block' if name in all_props else 'item'}/look/{name}"
        lib.write(lib.OUT / "items" / f"{name}.json", {"model": {"type": "minecraft:special", "base": base,
                                                               "model": {"type": "noclip_backrooms:mesh", "mesh": mesh_id, "texture": texture}}})
    lib.write(lib.OUT / "item_look.json", {"replaces": sorted(done)})
    lib.write(lib.PACK / "pack.mcmeta", {"pack": {"description": {"translate": "pack.noclip_backrooms.realistic_items.description",
                                                                   "fallback": "Items and props in the levels' worn style"},
                                                  "min_format": 88, "max_format": 88}})
    # A sheet of the materials, for a look without the game.
    names = sorted(lib._MADE)
    cell = 72
    sheet = Image.new("RGB", (cell * 10, cell * ((len(names) + 9) // 10)), (30, 30, 30))
    for i, name in enumerate(names):
        image = lib._MADE[name][0]
        tile = Image.fromarray((image * 255).round().astype(np.uint8), "RGBA").resize((cell - 6, cell - 6), Image.NEAREST)
        bg = Image.new("RGBA", tile.size, (60, 60, 60, 255))
        bg.alpha_composite(tile)
        sheet.paste(bg.convert("RGB"), ((i % 10) * cell + 3, (i // 10) * cell + 3))
    sheet.save(ROOT / "docs/look/items_materials.png")
    print(f"{len(done)} items and props, {len(names)} materials -> {lib.PACK.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
