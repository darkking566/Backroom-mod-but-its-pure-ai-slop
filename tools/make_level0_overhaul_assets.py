"""Generates 128px base and 512px labPBR realistic textures, 3D block models, blockstates, and item definitions
for the 2026-11-26 Level 0 Overhaul (micro-event blocks + multi-form non-door anomalous exits).

Run:
    python tools/make_level0_overhaul_assets.py
"""

import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
REALISTIC = ROOT / "src/main/resources/resourcepacks/realistic/assets/noclip_backrooms"
S = 512


def labpbr(height: np.ndarray, smooth: np.ndarray, f0: np.ndarray, porosity: np.ndarray, glow: np.ndarray):
    gx = np.roll(height, -1, axis=1) - np.roll(height, 1, axis=1)
    gy = np.roll(height, -1, axis=0) - np.roll(height, 1, axis=0)
    nx, ny = -gx * 0.4, -gy * 0.4
    ao = np.clip(1.0 - np.abs(gx) * 0.08 - np.abs(gy) * 0.08, 0.4, 1.0)
    n_img = np.stack([
        np.clip((nx * 0.5 + 0.5) * 255.0, 0, 255),
        np.clip((ny * 0.5 + 0.5) * 255.0, 0, 255),
        np.clip(ao * 255.0, 0, 255),
        np.clip(255.0 - np.clip(128.0 - height * 8.0, 0, 255), 0, 255),
    ], axis=-1).astype(np.uint8)
    s_img = np.stack([
        np.clip(smooth * 255.0, 0, 255),
        np.clip(f0, 0, 255),
        np.clip(porosity * 255.0, 0, 255),
        np.clip(glow * 255.0, 0, 255),
    ], axis=-1).astype(np.uint8)
    return Image.fromarray(n_img, "RGBA"), Image.fromarray(s_img, "RGBA")


def save_tex(name: str, color: Image.Image, height: np.ndarray, smooth: np.ndarray,
             f0: np.ndarray, porosity: np.ndarray, glow: np.ndarray):
    base_out = ASSETS / "textures/block/level0"
    real_out = REALISTIC / "textures/block/level0"
    base_out.mkdir(parents=True, exist_ok=True)
    real_out.mkdir(parents=True, exist_ok=True)

    c512 = color.convert("RGB")
    c128 = c512.resize((128, 128), Image.Resampling.LANCZOS)
    c128.save(base_out / f"{name}.png")
    c512.save(real_out / f"{name}.png")

    n512, s512 = labpbr(height, smooth, f0, porosity, glow)
    n512.save(real_out / f"{name}_n.png")
    s512.save(real_out / f"{name}_s.png")
    n512.resize((128, 128), Image.Resampling.BILINEAR).save(base_out / f"{name}_n.png")
    s512.resize((128, 128), Image.Resampling.BILINEAR).save(base_out / f"{name}_s.png")


def noise_grid(seed: int, scale: int = 32) -> np.ndarray:
    rng = np.random.default_rng(seed)
    small = rng.normal(0.0, 1.0, (scale, scale))
    img = Image.fromarray(np.clip((small * 0.25 + 0.5) * 255, 0, 255).astype(np.uint8))
    return (np.asarray(img.resize((S, S), Image.Resampling.BICUBIC), dtype=float) / 255.0 - 0.5) * 2.0


def wallpaper_base(seed: int, rgb=(194, 178, 114)) -> tuple[np.ndarray, np.ndarray]:
    base = np.ones((S, S, 3), dtype=float) * np.array(rgb, dtype=float)
    # Subtle vertical Backrooms wallpaper stripes
    xs = np.arange(S)
    stripe = ((xs // 32) % 2 == 0).astype(float)[None, :, None]
    base -= stripe * 10.0
    base += noise_grid(seed, 24)[..., None] * 14.0 + noise_grid(seed + 1, 96)[..., None] * 6.0
    height = noise_grid(seed + 2, 64) * 0.35
    return base, height


def write_json(path: Path, data: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def write_item_def(name: str, model_ref: str):
    write_json(ASSETS / f"items/{name}.json", {
        "model": {"type": "minecraft:model", "model": model_ref}
    })


def write_cube_all(name: str, tex: str):
    write_json(ASSETS / f"models/block/level0/{name}.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"noclip_backrooms:block/level0/{tex}"}
    })


def build_loose_ceiling_tile():
    for searched in (False, True):
        base = np.ones((S, S, 3), dtype=float) * np.array((198, 192, 168), dtype=float)
        base += noise_grid(410 if not searched else 411, 32)[..., None] * 12.0
        height = noise_grid(412, 64) * 0.4
        smooth = np.full((S, S), 0.14)
        f0 = np.full((S, S), 10.0)
        porosity = np.full((S, S), 0.35)
        glow = np.zeros((S, S))
        img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
        draw = ImageDraw.Draw(img)
        draw.rectangle([8, 8, S - 8, S - 8], outline=(112, 108, 92), width=12)
        if not searched:
            # Dark water-stain ring and sagging crack
            draw.ellipse([110, 120, 390, 380], outline=(130, 118, 86), width=18)
            draw.line([(40, 260), (220, 235), (470, 290)], fill=(45, 42, 34), width=10)
            height[230:295, 40:470] -= 1.8
        else:
            # Dark cavity opened inside tile
            draw.rectangle([72, 72, S - 72, S - 72], fill=(16, 16, 18), outline=(72, 68, 58), width=12)
            height[72:S - 72, 72:S - 72] -= 3.5
        tex_name = "loose_ceiling_tile_open" if searched else "loose_ceiling_tile"
        save_tex(tex_name, img, height, smooth, f0, porosity, glow)

    write_json(ASSETS / "models/block/level0/loose_ceiling_tile.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "noclip_backrooms:block/level0/loose_ceiling_tile"}
    })
    write_json(ASSETS / "models/block/level0/loose_ceiling_tile_open.json", {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": "noclip_backrooms:block/level0/loose_ceiling_tile_open"}
    })
    write_json(ASSETS / "blockstates/loose_ceiling_tile.json", {
        "variants": {
            "searched=false": {"model": "noclip_backrooms:block/level0/loose_ceiling_tile"},
            "searched=true": {"model": "noclip_backrooms:block/level0/loose_ceiling_tile_open"}
        }
    })
    write_item_def("loose_ceiling_tile", "noclip_backrooms:block/level0/loose_ceiling_tile")


def build_hollow_wall():
    for opened in (False, True):
        base, height = wallpaper_base(420 if not opened else 421)
        smooth = np.full((S, S), 0.16)
        f0 = np.full((S, S), 10.0)
        porosity = np.full((S, S), 0.25)
        glow = np.zeros((S, S))
        img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
        draw = ImageDraw.Draw(img)
        if not opened:
            # Bulging torn wallpaper seam showing grey plaster underneath
            draw.polygon([(190, 90), (310, 140), (335, 360), (220, 420), (175, 250)], fill=(122, 118, 108), outline=(74, 68, 52), width=6)
            draw.line([(245, 110), (255, 395)], fill=(28, 26, 22), width=10)
            height[110:400, 180:335] -= 1.4
        else:
            draw.polygon([(140, 80), (360, 110), (380, 410), (160, 430)], fill=(22, 20, 18), outline=(92, 86, 72), width=12)
            height[80:430, 140:380] -= 3.0
        tex_name = "hollow_wall_open" if opened else "hollow_wall"
        save_tex(tex_name, img, height, smooth, f0, porosity, glow)
        write_cube_all(tex_name, tex_name)

    write_json(ASSETS / "blockstates/hollow_wall.json", {
        "variants": {
            "opened=false": {"model": "noclip_backrooms:block/level0/hollow_wall"},
            "opened=true": {"model": "noclip_backrooms:block/level0/hollow_wall_open"}
        }
    })
    write_item_def("hollow_wall", "noclip_backrooms:block/level0/hollow_wall")


def build_graffiti_wall():
    variants = {}
    for v in range(4):
        rgb = (108, 110, 112) if v == 3 else (194, 178, 114)
        base, height = wallpaper_base(430 + v, rgb=rgb)
        smooth = np.full((S, S), 0.16)
        f0 = np.full((S, S), 10.0)
        porosity = np.full((S, S), 0.25)
        glow = np.zeros((S, S))
        img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
        draw = ImageDraw.Draw(img)
        ink = (24, 24, 22)
        if v == 0:
            # Stenciled pillar code A-14
            draw.rectangle([76, 150, 436, 355], outline=(48, 44, 34), width=8)
            # A
            draw.line([(120, 315), (155, 190), (190, 315)], fill=ink, width=16)
            draw.line([(132, 272), (178, 272)], fill=ink, width=14)
            # -
            draw.rectangle([215, 248, 265, 266], fill=ink)
            # 1
            draw.rectangle([295, 190, 315, 315], fill=ink)
            # 4
            draw.line([(395, 315), (395, 190), (345, 270), (412, 270)], fill=ink, width=16)
        elif v == 1:
            # Bold painted black arrow ->
            draw.line([(92, 256), (392, 256)], fill=ink, width=28)
            draw.line([(312, 168), (404, 256), (312, 344)], fill=ink, width=28)
        elif v == 2:
            # Frantic warning scrawl ("NO" + slash + eye)
            draw.ellipse([160, 128, 352, 248], outline=ink, width=14)
            draw.ellipse([232, 160, 280, 216], fill=ink)
            draw.line([(110, 330), (402, 310)], fill=(120, 26, 24), width=18)
            draw.line([(135, 385), (370, 392)], fill=ink, width=12)
        else:
            # Pitfalls undercroft black UP escape arrow
            draw.line([(256, 412), (256, 108)], fill=ink, width=32)
            draw.line([(160, 200), (256, 98), (352, 200)], fill=ink, width=32)
            draw.rectangle([140, 424, 372, 444], fill=(175, 155, 58))
        name = f"graffiti_wall_{v}"
        save_tex(name, img, height, smooth, f0, porosity, glow)
        write_cube_all(name, name)
        variants[f"variant={v}"] = {"model": f"noclip_backrooms:block/level0/{name}"}

    write_json(ASSETS / "blockstates/graffiti_wall.json", {"variants": variants})
    write_item_def("graffiti_wall", "noclip_backrooms:block/level0/graffiti_wall_0")


def build_anomaly_poster():
    # Faded 1980 Appalachia misty mountain lodge poster
    base = np.zeros((S, S, 3), dtype=float)
    # Foggy pale ochre sky gradient
    for y in range(S):
        t = y / float(S)
        base[y, :, 0] = 178 - 70 * t
        base[y, :, 1] = 172 - 62 * t
        base[y, :, 2] = 148 - 46 * t
    base += noise_grid(450, 28)[..., None] * 14.0
    height = noise_grid(451, 48) * 0.4
    smooth = np.full((S, S), 0.18)
    f0 = np.full((S, S), 10.0)
    porosity = np.full((S, S), 0.3)
    glow = np.zeros((S, S))

    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)
    # Paper frame & water stains
    draw.rectangle([16, 16, S - 16, S - 16], outline=(218, 206, 174), width=18)
    # Distant misty mountains
    draw.polygon([(26, 320), (145, 150), (265, 290), (370, 130), (486, 320)], fill=(64, 78, 72))
    draw.polygon([(26, 370), (190, 230), (330, 310), (486, 240), (486, 486), (26, 486)], fill=(38, 52, 44))
    # Wooden mountain lodge silhouette with dim orange cabin window
    draw.polygon([(190, 345), (256, 285), (322, 345)], fill=(54, 34, 24))
    draw.rectangle([206, 345, 306, 412], fill=(68, 44, 28))
    draw.rectangle([240, 362, 272, 392], fill=(214, 148, 62))
    glow[362:392, 240:272] = 0.25
    # Faded red "1980" banner stripe at top
    draw.rectangle([58, 54, 454, 108], fill=(142, 48, 42))
    draw.rectangle([90, 74, 422, 88], fill=(224, 210, 180))
    # Torn corner curl
    draw.polygon([(430, 496), (496, 424), (496, 496)], fill=(196, 184, 142))

    save_tex("anomaly_poster", img, height, smooth, f0, porosity, glow)
    write_json(ASSETS / "models/block/level0/anomaly_poster.json", {
        "textures": {
            "particle": "noclip_backrooms:block/level0/anomaly_poster",
            "front": "noclip_backrooms:block/level0/anomaly_poster"
        },
        "elements": [{
            "from": [1, 1, 14.8],
            "to": [15, 15, 16],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#front"},
                "east": {"uv": [15, 0, 16, 16], "texture": "#front"},
                "west": {"uv": [0, 0, 1, 16], "texture": "#front"},
                "up": {"uv": [0, 0, 16, 1], "texture": "#front"},
                "down": {"uv": [0, 15, 16, 16], "texture": "#front"}
            }
        }]
    })
    write_facing_blockstate("anomaly_poster", "noclip_backrooms:block/level0/anomaly_poster")
    write_item_def("anomaly_poster", "noclip_backrooms:block/level0/anomaly_poster")


def build_anomaly_window():
    # Level 188 Courtyard of Windows square window embedded in yellow wall
    base = np.ones((S, S, 3), dtype=float) * np.array((184, 188, 194), dtype=float)
    base += noise_grid(460, 24)[..., None] * 10.0
    height = np.zeros((S, S))
    smooth = np.full((S, S), 0.85)
    f0 = np.full((S, S), 42.0)
    porosity = np.zeros((S, S))
    glow = np.full((S, S), 0.35)
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)
    # Courtyard white grid of dozens of dark square windows visible beyond the glass!
    for gx in range(72, 440, 58):
        for gy in range(72, 440, 68):
            col = (24, 26, 30) if (gx + gy) % 3 != 0 else (235, 224, 170)
            draw.rectangle([gx, gy, gx + 36, gy + 44], fill=col, outline=(120, 124, 128), width=3)
    # Outer white-painted wood frame + center crossbars
    draw.rectangle([0, 0, S - 1, S - 1], outline=(218, 212, 196), width=38)
    draw.rectangle([244, 20, 268, S - 20], fill=(210, 204, 188))
    draw.rectangle([20, 244, S - 20, 268], fill=(210, 204, 188))
    height[:38, :] += 1.5
    height[-38:, :] += 1.5
    height[:, :38] += 1.5
    height[:, -38:] += 1.5
    glow[:38, :] = 0.0
    glow[-38:, :] = 0.0
    glow[:, :38] = 0.0
    glow[:, -38:] = 0.0
    save_tex("anomaly_window", img, height, smooth, f0, porosity, glow)
    write_cube_all("anomaly_window", "anomaly_window")
    write_facing_blockstate("anomaly_window", "noclip_backrooms:block/level0/anomaly_window")
    write_item_def("anomaly_window", "noclip_backrooms:block/level0/anomaly_window")


def build_steam_grate():
    # Floor rusted iron grate with dark pipe depth & warm steam glow (Level 2 Pipe Dreams)
    base = np.ones((S, S, 3), dtype=float) * np.array((24, 22, 22), dtype=float)
    height = np.full((S, S), -2.5)
    smooth = np.full((S, S), 0.42)
    f0 = np.full((S, S), 145.0)
    porosity = np.zeros((S, S))
    glow = np.zeros((S, S))
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)
    # Warm furnace-red glint deep beneath bars
    draw.ellipse([140, 140, 372, 372], fill=(64, 30, 22))
    # Heavy rusted cast-iron frame & parallel grate bars
    draw.rectangle([0, 0, S - 1, S - 1], outline=(86, 74, 68), width=42)
    for x in range(64, S - 56, 56):
        draw.rectangle([x, 36, x + 22, S - 36], fill=(96, 82, 74), outline=(54, 46, 42), width=3)
        height[36:S - 36, x:x + 22] = 0.8
    for y in (164, 326):
        draw.rectangle([36, y, S - 36, y + 22], fill=(88, 76, 68))
        height[y:y + 22, 36:S - 36] = 0.9
    height[:42, :] = 1.2
    height[-42:, :] = 1.2
    height[:, :42] = 1.2
    height[:, -42:] = 1.2
    save_tex("steam_grate", img, height, smooth, f0, porosity, glow)
    write_cube_all("steam_grate", "steam_grate")
    write_facing_blockstate("steam_grate", "noclip_backrooms:block/level0/steam_grate")
    write_item_def("steam_grate", "noclip_backrooms:block/level0/steam_grate")


def build_oil_vent_hatch():
    # Oil-stained industrial metal vent hatch leading to Level 1 Parking Garage
    base = np.ones((S, S, 3), dtype=float) * np.array((98, 102, 106), dtype=float)
    base += noise_grid(470, 32)[..., None] * 14.0
    height = noise_grid(471, 64) * 0.3
    smooth = np.full((S, S), 0.48)
    f0 = np.full((S, S), 155.0)
    porosity = np.zeros((S, S))
    glow = np.zeros((S, S))
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)
    draw.rectangle([16, 16, S - 16, S - 16], outline=(52, 56, 60), width=20)
    # Dark louvered vent slats
    for y in range(74, S - 74, 42):
        draw.rectangle([66, y, S - 66, y + 20], fill=(22, 24, 28), outline=(72, 76, 80), width=3)
        height[y:y + 20, 66:S - 66] = -1.6
    # Oil drip stains running down from lower slats
    for ox in (140, 280, 360):
        draw.ellipse([ox - 16, 360, ox + 16, 488], fill=(28, 26, 22))
        smooth[360:488, ox - 16:ox + 16] = 0.85
    save_tex("oil_vent_hatch", img, height, smooth, f0, porosity, glow)
    write_cube_all("oil_vent_hatch", "oil_vent_hatch")
    write_facing_blockstate("oil_vent_hatch", "noclip_backrooms:block/level0/oil_vent_hatch")
    write_item_def("oil_vent_hatch", "noclip_backrooms:block/level0/oil_vent_hatch")


def build_peeling_carpet_exit():
    # Yellow carpet peeled back revealing rotten wooden floorboards beneath (Level 27)
    base = np.ones((S, S, 3), dtype=float) * np.array((172, 152, 92), dtype=float)
    base += noise_grid(480, 64)[..., None] * 14.0
    height = noise_grid(481, 64) * 0.4
    smooth = np.full((S, S), 0.14)
    f0 = np.full((S, S), 10.0)
    porosity = np.full((S, S), 0.45)
    glow = np.zeros((S, S))
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)
    # Exposed dark weathered wooden planks in lower-right triangle
    draw.polygon([(140, 512), (512, 120), (512, 512)], fill=(86, 58, 36))
    for py in range(160, 512, 54):
        draw.line([(140, py), (512, py)], fill=(42, 26, 16), width=6)
    # Curled carpet fold edge casting shadow
    draw.line([(140, 512), (512, 120)], fill=(38, 32, 20), width=16)
    draw.polygon([(120, 490), (490, 100), (455, 85), (105, 455)], fill=(198, 180, 118))
    save_tex("peeling_carpet_exit", img, height, smooth, f0, porosity, glow)
    write_cube_all("peeling_carpet_exit", "peeling_carpet_exit")
    write_facing_blockstate("peeling_carpet_exit", "noclip_backrooms:block/level0/peeling_carpet_exit")
    write_item_def("peeling_carpet_exit", "noclip_backrooms:block/level0/peeling_carpet_exit")


def build_hollow_plaster_exit():
    # Torn yellow wallpaper exposing fractured grey plaster & deep fissure (Level 194)
    base, height = wallpaper_base(490)
    smooth = np.full((S, S), 0.16)
    f0 = np.full((S, S), 10.0)
    porosity = np.full((S, S), 0.35)
    glow = np.zeros((S, S))
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)
    draw.polygon([(80, 50), (430, 90), (460, 440), (65, 460)], fill=(134, 132, 126), outline=(82, 74, 54), width=10)
    # Deep dark jagged crack in the plaster
    draw.line([(245, 70), (272, 195), (218, 310), (264, 445)], fill=(18, 18, 20), width=16)
    draw.line([(272, 195), (365, 240)], fill=(26, 26, 28), width=10)
    height[70:445, 210:285] -= 2.8
    save_tex("hollow_plaster_exit", img, height, smooth, f0, porosity, glow)
    write_cube_all("hollow_plaster_exit", "hollow_plaster_exit")
    write_facing_blockstate("hollow_plaster_exit", "noclip_backrooms:block/level0/hollow_plaster_exit")
    write_item_def("hollow_plaster_exit", "noclip_backrooms:block/level0/hollow_plaster_exit")


def build_giant_mirror_exit():
    for half in ("lower", "upper"):
        base = np.ones((S, S, 3), dtype=float) * np.array((148, 160, 166), dtype=float)
        # Warped diagonal reflection bands
        yy, xx = np.mgrid[0:S, 0:S]
        sheen = np.sin((xx * 0.018 + yy * 0.012)) * 24.0
        base[..., 0] += sheen * 0.7
        base[..., 1] += sheen * 0.85
        base[..., 2] += sheen
        height = noise_grid(500 if half == "lower" else 501, 48) * 0.25
        smooth = np.full((S, S), 0.94)
        f0 = np.full((S, S), 210.0)
        porosity = np.zeros((S, S))
        glow = np.full((S, S), 0.18)
        img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
        draw = ImageDraw.Draw(img)
        # Dark wooden tall mirror bezel
        draw.rectangle([0, 0, 34, S - 1], fill=(56, 44, 34))
        draw.rectangle([S - 34, 0, S - 1, S - 1], fill=(56, 44, 34))
        if half == "upper":
            draw.rectangle([0, 0, S - 1, 34], fill=(56, 44, 34))
            # Faint uncanny shadowed reflection silhouette inside glass
            draw.ellipse([212, 168, 300, 260], fill=(78, 86, 92))
        else:
            draw.rectangle([0, S - 34, S - 1, S - 1], fill=(56, 44, 34))
        # Subtle fracture hairline across glass
        draw.line([(96, 120), (240, 260), (412, 340)], fill=(215, 226, 232), width=3)
        tex = f"giant_mirror_exit_{half}"
        save_tex(tex, img, height, smooth, f0, porosity, glow)
        write_json(ASSETS / f"models/block/level0/{tex}.json", {
            "textures": {
                "particle": f"noclip_backrooms:block/level0/{tex}",
                "face": f"noclip_backrooms:block/level0/{tex}"
            },
            "elements": [{
                "from": [0, 0, 13],
                "to": [16, 16, 16],
                "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#face"} for d in ("north", "south", "east", "west", "up", "down")}
            }]
        })

    variants = {}
    for half in ("lower", "upper"):
        for facing, yrot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            variants[f"facing={facing},half={half}"] = {
                "model": f"noclip_backrooms:block/level0/giant_mirror_exit_{half}",
                "y": yrot
            }
    write_json(ASSETS / "blockstates/giant_mirror_exit.json", {"variants": variants})
    write_item_def("giant_mirror_exit", "noclip_backrooms:block/level0/giant_mirror_exit_lower")


def build_locked_utility_door():
    for half in ("lower", "upper"):
        base = np.ones((S, S, 3), dtype=float) * np.array((76, 82, 88), dtype=float)
        base += noise_grid(510 if half == "lower" else 511, 32)[..., None] * 10.0
        height = np.zeros((S, S))
        smooth = np.full((S, S), 0.45)
        f0 = np.full((S, S), 140.0)
        porosity = np.zeros((S, S))
        glow = np.zeros((S, S))
        img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
        draw = ImageDraw.Draw(img)
        draw.rectangle([16, 0, S - 16, S - 1], outline=(46, 50, 54), width=16)
        for y in range(44, S - 24, 52):
            draw.line([(28, y), (S - 28, y)], fill=(54, 58, 64), width=8)
            height[y:y + 8, 28:S - 28] -= 1.2
        if half == "upper":
            # Red locked indicator LED
            draw.rectangle([388, 340, 444, 412], fill=(32, 34, 38))
            draw.ellipse([402, 354, 430, 382], fill=(224, 42, 42))
            glow[354:382, 402:430] = 0.85
        tex = f"locked_utility_door_{half}"
        save_tex(tex, img, height, smooth, f0, porosity, glow)
        write_json(ASSETS / f"models/block/level0/{tex}.json", {
            "textures": {
                "particle": f"noclip_backrooms:block/level0/{tex}",
                "face": f"noclip_backrooms:block/level0/{tex}"
            },
            "elements": [{
                "from": [0, 0, 6],
                "to": [16, 16, 10],
                "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#face"} for d in ("north", "south", "east", "west", "up", "down")}
            }]
        })

    # Open state uses a minimal thin top header frame so the player walks right through
    write_json(ASSETS / "models/block/level0/locked_utility_door_open.json", {
        "textures": {"particle": "noclip_backrooms:block/level0/locked_utility_door_upper",
                     "face": "noclip_backrooms:block/level0/locked_utility_door_upper"},
        "elements": [{
            "from": [0, 14, 6],
            "to": [16, 16, 10],
            "faces": {d: {"uv": [0, 0, 16, 2], "texture": "#face"} for d in ("north", "south", "east", "west", "up", "down")}
        }]
    })
    variants = {}
    for half in ("lower", "upper"):
        for opened in ("false", "true"):
            for facing, yrot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                model = "noclip_backrooms:block/level0/locked_utility_door_open" if opened == "true" \
                    else f"noclip_backrooms:block/level0/locked_utility_door_{half}"
                variants[f"facing={facing},half={half},open={opened}"] = {"model": model, "y": yrot}
    write_json(ASSETS / "blockstates/locked_utility_door.json", {"variants": variants})
    write_item_def("locked_utility_door", "noclip_backrooms:block/level0/locked_utility_door_lower")


def build_anomaly_portal_doors():
    themes = {
        "bowling": ((44, 72, 142), (176, 62, 224), 0.65),
        "remodel": ((214, 212, 206), (218, 184, 42), 0.0),
        "floating": ((122, 82, 52), (192, 162, 92), 0.0),
        "glass": ((168, 198, 210), (228, 240, 246), 0.25),
        "diner": ((136, 68, 46), (208, 178, 118), 0.1),
        "fake_exit": ((62, 66, 68), (228, 32, 32), 0.92),
    }
    variants = {}
    seed_i = 520
    for kind, (bg, accent, glow_val) in themes.items():
        for half in ("lower", "upper"):
            seed_i += 1
            base = np.ones((S, S, 3), dtype=float) * np.array(bg, dtype=float)
            base += noise_grid(seed_i, 28)[..., None] * 12.0
            height = noise_grid(seed_i + 100, 48) * 0.3
            smooth = np.full((S, S), 0.75 if kind == "glass" else 0.34)
            f0 = np.full((S, S), 60.0 if kind == "glass" else 40.0)
            porosity = np.zeros((S, S))
            glow = np.zeros((S, S))
            img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
            draw = ImageDraw.Draw(img)
            draw.rectangle([20, 12, S - 20, S - 12], outline=(38, 38, 42), width=14)
            if kind == "remodel":
                # Yellow-black caution stripes across remodel door
                for sy in range(40, S - 40, 120):
                    draw.rectangle([28, sy, S - 28, sy + 42], fill=accent)
                    for sx in range(28, S - 48, 64):
                        draw.polygon([(sx, sy), (sx + 32, sy), (sx + 16, sy + 42), (sx - 16, sy + 42)], fill=(28, 28, 28))
            elif kind == "bowling" and half == "upper":
                # Neon trim + bowling pin icon
                draw.rectangle([48, 48, S - 48, S - 48], outline=accent, width=14)
                draw.ellipse([226, 120, 286, 180], fill=(235, 235, 240))
                draw.ellipse([212, 188, 300, 390], fill=(235, 235, 240))
                draw.rectangle([216, 224, 296, 244], fill=(214, 48, 48))
                glow[48:62, 48:S - 48] = glow_val
                glow[S - 62:S - 48, 48:S - 48] = glow_val
            elif kind == "fake_exit" and half == "upper":
                # Glowing crimson EXIT trap sign
                draw.rectangle([86, 86, S - 86, 214], fill=(38, 14, 14), outline=accent, width=10)
                draw.rectangle([120, 122, S - 120, 178], fill=accent)
                glow[86:214, 86:S - 86] = glow_val
            elif kind == "glass":
                # Spiderweb crack lines across frosted glass door
                draw.line([(80, 90), (256, 256), (430, 130)], fill=accent, width=6)
                draw.line([(120, 420), (256, 256), (390, 430)], fill=accent, width=6)
            elif kind == "diner" and half == "upper":
                draw.ellipse([156, 96, 356, 296], fill=(24, 24, 26), outline=accent, width=14)
            elif half == "lower":
                draw.ellipse([392, 48, 444, 100], fill=accent, outline=(42, 34, 24), width=6)

            tex = f"portal_door_{kind}_{half}"
            save_tex(tex, img, height, smooth, f0, porosity, glow)
            write_cube_all(tex, tex)
            for facing, yrot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                variants[f"facing={facing},half={half},kind={kind}"] = {
                    "model": f"noclip_backrooms:block/level0/{tex}",
                    "y": yrot
                }

    write_json(ASSETS / "blockstates/anomaly_portal_door.json", {"variants": variants})
    write_item_def("anomaly_portal_door", "noclip_backrooms:block/level0/portal_door_bowling_lower")


def write_facing_blockstate(name: str, model_ref: str):
    variants = {}
    for half in ("lower", "upper"):
        for facing, yrot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            variants[f"facing={facing},half={half}"] = {"model": model_ref, "y": yrot}
    write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})


def main():
    build_loose_ceiling_tile()
    build_hollow_wall()
    build_graffiti_wall()
    build_anomaly_poster()
    build_anomaly_window()
    build_steam_grate()
    build_oil_vent_hatch()
    build_peeling_carpet_exit()
    build_hollow_plaster_exit()
    build_giant_mirror_exit()
    build_locked_utility_door()
    build_anomaly_portal_doors()
    print("Generated all Level 0 Overhaul textures (128px + 512px labPBR), models, blockstates, and item definitions.")


if __name__ == "__main__":
    main()
