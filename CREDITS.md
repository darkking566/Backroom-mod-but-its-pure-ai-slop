# Audio & Asset Credits

All custom audio files in `src/main/resources/assets/noclip_backrooms/sounds/level0/` are derived from real recordings licensed under **CC0 1.0 Universal (Public Domain)** on Freesound.org, converted to mono Ogg Vorbis for Minecraft 3D positional audio:

- `fluorescent_hum.ogg`
  - Source: *Fluorescent lightbulb hum buzz (Extended)* by **Developer_Jake**
  - URL: https://freesound.org/people/Developer_Jake/sounds/638895/
  - License: CC0 1.0 (Public Domain)

- `distant_whisper.ogg`
  - Source: *whisper trail 3.ogg* by **sleepCircle**
  - URL: https://freesound.org/people/sleepCircle/sounds/22330/
  - License: CC0 1.0 (Public Domain)

- `wall_scratch_1.ogg`, `wall_scratch_2.ogg`
  - Source: *Scratching and tapping Hollow Ice* by **Wigglesworth**
  - URL: https://freesound.org/people/Wigglesworth/sounds/838100/
  - License: CC0 1.0 (Public Domain)

- `manila_piano.ogg`
  - Source: *Calm Ambient Piano Loop* by **Jadis0x**
  - URL: https://freesound.org/people/Jadis0x/sounds/832628/
  - License: CC0 1.0 (Public Domain)

## Realistic textures (built-in resource pack)

The photo-based textures in `src/main/resources/resourcepacks/realistic/` are made by `tools/make_level0_realistic.py` from these ambientCG materials, all licensed **CC0 1.0 Universal (Public Domain)** (https://ambientcg.com): Carpet016 and Fabric028 (carpet), Wallpaper001A (paper grain), Wallpaper002A, Wallpaper002B and Wallpaper002C (water stains and mould). The wallpaper print, ceiling tiles, light panels, baseboard and painted metal are drawn by that tool. The camcorder filter (`shaders/post/camcorder.fsh`) is original.

## Film furniture store props (Level 0)

The furniture-store objects in `level0/film/` (meshes in `assets/noclip_backrooms/models/film/`, textures in `textures/film/`) are original models made in Blender by `tools/film/`. They are modelled after the kinds of furniture seen in A24's film *Backrooms* (2026) — overstuffed 1990s sofas and recliners, ladder-back chairs, piles of furniture, sale signs — without copying its store name, logo or any image. Their surfaces start from these ambientCG materials, all licensed **CC0 1.0 Universal (Public Domain)** (https://ambientcg.com): Fabric028, Fabric030, Fabric061, Leather026, Plastic006, Plastic010, Cardboard004, Wood049, Wood051, Wood066, Paper001, PaintedMetal004, Metal032, Metal049A, Rust004, Carpet016, Wallpaper002B and Wallpaper002C. The fabric prints, pictures and sign faces are drawn by `tools/film/prints.py`; the sign lettering is rendered with fonts installed with Windows (Cooper Black, Arial Black, Ink Free).

## Manila Room design

The Manila Room follows the Backrooms wiki concept (https://backrooms-wiki-cn.wikidot.com/manila-room, CC BY-SA 3.0) for general structure and atmosphere: a rare 8x8 wooden-floored sanctuary with thick manila-paper walls, hidden Void Stone core, four wooden doors, an octagonal central table, chairs, supply cabinet, Almond Water, and a Manila Mary Foundation guide note. All textures are drawn by `tools/make_manila.py`.

## Level 1 design

Level 1 follows the "Level 1" page of the Backrooms Wikidot wiki (https://backrooms-wiki.wikidot.com/level-1, CC BY-SA 3.0, authors listed on that page) for general ideas only: a concrete parking structure, tight corridors, blackouts, supply crates, and a flickering wall out of Level 0. No text was copied. Level 1 sounds reuse vanilla Minecraft sound events and the fluorescent hum above; all Level 1 textures are drawn by `tools/make_level1.py`.
