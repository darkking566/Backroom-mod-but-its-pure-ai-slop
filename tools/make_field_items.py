"""The investigator's field kit (docs/企劃書/1-遊戲玩法/README.md, "後室進階玩法"): 32x32 item icons for the camera,
photos, topology tape, chalk, glow sticks, the guide-line spool, barricade planks, the tripwire alarm, the emergency lamp
and the mapping beacon; and the textures and models of the blocks they place (chalk marks, glow sticks, the red line,
barricades, the alarm, the lamp, the beacon). Simple icons for now; Gemini may replace them with 3D models.

Run from the repository root:  py -I tools/make_field_items.py
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
ITEM_TEX = ASSETS / "textures/item/field"
BLOCK_TEX = ASSETS / "textures/block/field"
MODELS = ASSETS / "models/block/field"
NS = "noclip_backrooms:block/field/"
S = 32
FACING = {"north": 0, "east": 90, "south": 180, "west": 270}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf8")


def canvas(size=S):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def noisy(img, seed, amount=8):
    rnd = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a:
                v = rnd.randint(-amount, amount)
                px[x, y] = (max(0, min(255, r + v)), max(0, min(255, g + v)), max(0, min(255, b + v)), a)
    return img


def item(name, img, block=None):
    ITEM_TEX.mkdir(parents=True, exist_ok=True)
    img.save(ITEM_TEX / f"{name}.png")
    write_json(ASSETS / "models/item/field" / f"{name}.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"noclip_backrooms:item/field/{name}"}})
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"noclip_backrooms:item/field/{name}"}})


# Item icons

def camera():
    img, d = canvas()
    d.rectangle([4, 10, 27, 25], fill=(54, 58, 62, 255), outline=(30, 32, 34, 255))
    d.rectangle([6, 7, 13, 10], fill=(70, 74, 78, 255))
    d.ellipse([11, 12, 22, 23], fill=(24, 26, 30, 255), outline=(150, 156, 160, 255))
    d.ellipse([14, 15, 19, 20], fill=(70, 110, 140, 255))
    d.rectangle([22, 11, 26, 14], fill=(240, 240, 220, 255))
    d.rectangle([4, 25, 27, 27], fill=(230, 226, 214, 255))
    return noisy(img, 1, 5)


def photo():
    img, d = canvas()
    d.rectangle([6, 4, 25, 28], fill=(236, 232, 220, 255), outline=(180, 176, 166, 255))
    d.rectangle([8, 6, 23, 20], fill=(40, 38, 30, 255))
    d.ellipse([12, 9, 19, 16], fill=(210, 200, 120, 255))
    d.line([13, 14, 18, 14], fill=(40, 38, 30, 255))
    return noisy(img, 2, 6)


def tape():
    img, d = canvas()
    d.rectangle([4, 9, 27, 24], fill=(50, 52, 56, 255), outline=(26, 28, 30, 255))
    d.rectangle([8, 11, 23, 16], fill=(80, 160, 170, 255))
    d.ellipse([8, 17, 13, 22], fill=(200, 200, 200, 255))
    d.ellipse([18, 17, 23, 22], fill=(200, 200, 200, 255))
    d.text((9, 10), "MAP", fill=(20, 30, 30, 255))
    return noisy(img, 3, 5)


def chalk():
    img, d = canvas()
    d.polygon([(8, 24), (22, 8), (26, 12), (12, 28)], fill=(238, 236, 226, 255), outline=(190, 188, 180, 255))
    d.polygon([(8, 24), (12, 28), (9, 28), (6, 25)], fill=(210, 206, 196, 255))
    return noisy(img, 4, 7)


def glow_stick():
    img, d = canvas()
    d.line([9, 24, 23, 8], fill=(80, 255, 120, 255), width=4)
    d.line([9, 24, 11, 22], fill=(220, 220, 220, 255), width=4)
    d.line([21, 10, 23, 8], fill=(220, 220, 220, 255), width=4)
    return img


def spool():
    img, d = canvas()
    d.rectangle([7, 6, 24, 9], fill=(120, 90, 60, 255))
    d.rectangle([7, 23, 24, 26], fill=(120, 90, 60, 255))
    d.rectangle([9, 9, 22, 23], fill=(190, 30, 30, 255))
    for y in range(10, 23, 2):
        d.line([9, y, 22, y], fill=(150, 20, 20, 255))
    d.line([22, 16, 29, 20], fill=(200, 30, 30, 255), width=1)
    return noisy(img, 5, 4)


def planks():
    img, d = canvas()
    for i, y in enumerate((6, 14, 22)):
        d.rectangle([3, y, 28, y + 5], fill=(150 - i * 10, 112 - i * 8, 70 - i * 6, 255), outline=(90, 66, 40, 255))
    for x in (7, 24):
        for y in (8, 16, 24):
            d.point((x, y), fill=(180, 180, 186, 255))
    return noisy(img, 6, 8)


def alarm():
    img, d = canvas()
    d.line([2, 26, 30, 26], fill=(170, 170, 170, 255), width=1)
    for x0 in (6, 18):
        d.rectangle([x0, 12, x0 + 7, 24], fill=(176, 182, 186, 255), outline=(110, 116, 120, 255))
        d.line([x0 + 1, 14, x0 + 6, 14], fill=(200, 60, 50, 255))
    d.line([10, 12, 10, 4], fill=(150, 150, 150, 255))
    return noisy(img, 7, 4)


def lamp():
    img, d = canvas()
    d.rectangle([10, 6, 21, 9], fill=(60, 60, 60, 255))
    d.rectangle([9, 10, 22, 22], fill=(255, 236, 160, 255), outline=(70, 70, 70, 255))
    d.line([9, 16, 22, 16], fill=(70, 70, 70, 255))
    d.rectangle([8, 22, 23, 27], fill=(200, 160, 30, 255), outline=(90, 70, 20, 255))
    return noisy(img, 8, 3)


def beacon_icon():
    img, d = canvas()
    d.rectangle([9, 16, 22, 28], fill=(70, 76, 82, 255), outline=(40, 44, 48, 255))
    d.line([16, 16, 16, 4], fill=(150, 150, 150, 255), width=1)
    d.ellipse([13, 2, 19, 7], fill=(80, 230, 240, 255))
    d.rectangle([11, 19, 20, 23], fill=(40, 160, 170, 255))
    return noisy(img, 9, 4)


def bio_scanner():
    img, d = canvas()
    d.rectangle([8, 6, 23, 27], fill=(56, 62, 68, 255), outline=(28, 32, 36, 255))
    d.line([21, 6, 21, 2], fill=(160, 168, 174, 255), width=2)
    d.ellipse([19, 1, 23, 4], fill=(230, 60, 60, 255))
    d.rectangle([10, 9, 21, 18], fill=(20, 48, 42, 255), outline=(40, 90, 80, 255))
    d.ellipse([12, 10, 19, 17], outline=(70, 235, 195, 255))
    d.point((16, 12), fill=(255, 70, 70, 255))
    d.point((17, 12), fill=(255, 70, 70, 255))
    d.rectangle([10, 20, 14, 23], fill=(80, 220, 180, 255))
    d.rectangle([16, 20, 21, 23], fill=(210, 80, 70, 255))
    d.rectangle([11, 25, 20, 26], fill=(110, 118, 124, 255))
    return noisy(img, 14, 4)


# Block textures (16 px faces drawn at 64 px)

def block_tex(name, img):
    BLOCK_TEX.mkdir(parents=True, exist_ok=True)
    img.save(BLOCK_TEX / f"{name}.png")


def chalk_symbol(symbol):
    img, d = canvas(64)
    col = (236, 234, 224, 235)
    if symbol == "arrow":
        d.line([32, 54, 32, 14], fill=col, width=5)
        d.line([32, 12, 18, 28], fill=col, width=5)
        d.line([32, 12, 46, 28], fill=col, width=5)
    elif symbol == "cross":
        d.line([14, 14, 50, 50], fill=col, width=6)
        d.line([50, 14, 14, 50], fill=col, width=6)
    else:
        d.rectangle([16, 12, 48, 52], outline=col, width=4)
        d.text((22, 24), "EXIT", fill=col)
    px = img.load()
    rnd = random.Random(len(symbol))
    for y in range(64):
        for x in range(64):
            if px[x, y][3] and rnd.random() < 0.18:
                px[x, y] = (0, 0, 0, 0)
    return img


def glow_stick_tex():
    img, d = canvas(64)
    d.line([12, 52, 52, 12], fill=(90, 255, 130, 255), width=8)
    d.line([12, 52, 18, 46], fill=(220, 220, 220, 255), width=8)
    d.line([46, 18, 52, 12], fill=(220, 220, 220, 255), width=8)
    return img


def red_line_tex():
    img, d = canvas(64)
    d.line([0, 32, 64, 32], fill=(200, 26, 26, 255), width=3)
    return img


def barricade_tex():
    img, d = canvas(64)
    for i, y in enumerate((4, 24, 44)):
        d.rectangle([0, y, 63, y + 15], fill=(140 - i * 8, 104 - i * 6, 64 - i * 4, 255), outline=(84, 60, 36, 255))
        for x in (6, 57):
            d.ellipse([x - 2, y + 5, x + 2, y + 9], fill=(170, 170, 176, 255))
    return noisy(img, 10, 10)


def alarm_tex():
    img, d = canvas(64)
    d.line([0, 50, 64, 50], fill=(180, 180, 180, 255), width=2)
    for x0 in (14, 38):
        d.rectangle([x0, 30, x0 + 12, 50], fill=(176, 182, 186, 255), outline=(110, 116, 120, 255))
    return img


def lamp_tex(lit):
    img, d = canvas(64)
    d.rectangle([0, 0, 63, 63], fill=(70, 70, 72, 255))
    d.rectangle([6, 6, 57, 57], fill=(255, 238, 170, 255) if lit else (90, 86, 70, 255))
    d.line([6, 32, 57, 32], fill=(70, 70, 72, 255), width=3)
    return img


def beacon_tex(top):
    img, d = canvas(64)
    d.rectangle([0, 0, 63, 63], fill=(70, 76, 82, 255), outline=(40, 44, 48, 255))
    if top:
        d.ellipse([20, 20, 44, 44], fill=(80, 230, 240, 255), outline=(30, 120, 130, 255))
    else:
        d.rectangle([10, 20, 54, 40], fill=(40, 160, 170, 255))
        for x in range(14, 52, 8):
            d.line([x, 46, x, 56], fill=(30, 34, 38, 255), width=3)
    return noisy(img, 11 if top else 12, 4)


def tex(name):
    return NS + name


def face(t, uv=(0, 0, 16, 16)):
    return {"texture": "#" + t, "uv": list(uv)}


def element(frm, to, faces, shade=True):
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if not shade:
        e["shade"] = False
    return e


def custom(textures, elements, particle):
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": tex(particle), **{k: tex(v) for k, v in textures.items()}}, "elements": elements}


def models():
    # Chalk marks: on the floor (a decal just above it) or on a wall (a decal just off its face), each symbol.
    variants = {}
    for symbol in ("arrow", "cross", "exit"):
        floor = custom({"t": f"chalk_{symbol}"}, [element((0, 0.05, 0), (16, 0.1, 16), {"up": face("t"), "down": face("t")})], f"chalk_{symbol}")
        wall = custom({"t": f"chalk_{symbol}"}, [element((0, 0, 15.9), (16, 16, 15.95), {"north": face("t"), "south": face("t")})], f"chalk_{symbol}")
        write_json(MODELS / f"chalk_{symbol}_floor.json", floor)
        write_json(MODELS / f"chalk_{symbol}_wall.json", wall)
        for f, y in FACING.items():
            variants[f"face=floor,facing={f},symbol={symbol}"] = {"model": NS + f"chalk_{symbol}_floor", **({"y": y} if y else {})}
            variants[f"face=wall,facing={f},symbol={symbol}"] = {"model": NS + f"chalk_{symbol}_wall", **({"y": y} if y else {})}
    write_json(ASSETS / "blockstates/chalk_mark.json", {"variants": variants})

    write_json(MODELS / "glow_stick.json", custom({"t": "glow_stick"}, [element((2, 0.1, 2), (14, 0.2, 14), {"up": face("t"), "down": face("t")}, shade=False)], "glow_stick"))
    write_json(ASSETS / "blockstates/glow_stick.json", {"variants": {"": {"model": NS + "glow_stick"}}})

    write_json(MODELS / "red_line.json", custom({"t": "red_line"}, [element((0, 0.1, 0), (16, 0.15, 16), {"up": face("t"), "down": face("t")})], "red_line"))
    write_json(ASSETS / "blockstates/red_line.json", {"variants": {"axis=x": {"model": NS + "red_line"}, "axis=z": {"model": NS + "red_line", "y": 90}}})

    plank = custom({"p": "barricade"}, [element((0, 0, 6), (16, 16, 10), {d: face("p") for d in ("north", "south", "east", "west", "up", "down")})], "barricade")
    write_json(MODELS / "barricade.json", plank)
    write_json(ASSETS / "blockstates/barricade.json", {"variants": {"axis=x": {"model": NS + "barricade"}, "axis=z": {"model": NS + "barricade", "y": 90}}})

    write_json(MODELS / "tripwire_alarm.json", custom({"a": "tripwire_alarm"}, [element((0, 0, 7.9), (16, 16, 8.1), {"north": face("a"), "south": face("a")})], "tripwire_alarm"))
    write_json(ASSETS / "blockstates/tripwire_alarm.json", {"variants": {"axis=x": {"model": NS + "tripwire_alarm"}, "axis=z": {"model": NS + "tripwire_alarm", "y": 90}}})

    for lit in (True, False):
        name = "emergency_lamp" if lit else "emergency_lamp_out"
        write_json(MODELS / f"{name}.json", custom({"l": name}, [element((4, 0, 4), (12, 9, 12), {d: face("l") for d in ("north", "south", "east", "west", "up", "down")})], name))
    write_json(ASSETS / "blockstates/emergency_lamp.json", {"variants": {"lit=true": {"model": NS + "emergency_lamp"}, "lit=false": {"model": NS + "emergency_lamp_out"}}})

    write_json(MODELS / "mapping_beacon.json", custom({"s": "mapping_beacon_side", "t": "mapping_beacon_top"}, [
        element((3, 0, 3), (13, 10, 13), {"north": face("s"), "south": face("s"), "east": face("s"), "west": face("s"), "up": face("t"), "down": face("s")}),
        element((7.5, 10, 7.5), (8.5, 16, 8.5), {d: face("s", (7, 0, 8, 6)) for d in ("north", "south", "east", "west", "up")})], "mapping_beacon_side"))
    write_json(ASSETS / "blockstates/mapping_beacon.json", {"variants": {"": {"model": NS + "mapping_beacon"}}})
    write_json(ASSETS / "items/mapping_beacon.json", {"model": {"type": "minecraft:model", "model": "noclip_backrooms:item/field/mapping_beacon"}})


def main():
    item("meg_camera", camera())
    item("evidence_photo", photo())
    item("topology_tape", tape())
    item("chalk", chalk())
    item("glow_stick", glow_stick())
    item("guide_line", spool())
    item("barricade_planks", planks())
    item("tripwire_alarm", alarm())
    item("emergency_lamp", lamp())
    item("mapping_beacon", beacon_icon())
    item("bio_scanner", bio_scanner())
    for symbol in ("arrow", "cross", "exit"):
        block_tex(f"chalk_{symbol}", chalk_symbol(symbol))
    block_tex("glow_stick", glow_stick_tex())
    block_tex("red_line", red_line_tex())
    block_tex("barricade", barricade_tex())
    block_tex("tripwire_alarm", alarm_tex())
    block_tex("emergency_lamp", lamp_tex(True))
    block_tex("emergency_lamp_out", lamp_tex(False))
    block_tex("mapping_beacon_top", beacon_tex(True))
    block_tex("mapping_beacon_side", beacon_tex(False))
    models()
    all_icons = ("meg_camera", "evidence_photo", "topology_tape", "chalk", "glow_stick", "guide_line",
                 "barricade_planks", "tripwire_alarm", "emergency_lamp", "mapping_beacon", "bio_scanner")
    sheet = Image.new("RGBA", (len(all_icons) * (S * 3 + 4) + 4, S * 3 + 8), (40, 40, 40, 255))
    for i, name in enumerate(all_icons):
        icon = Image.open(ITEM_TEX / f"{name}.png").resize((S * 3, S * 3), Image.NEAREST)
        sheet.alpha_composite(icon, (4 + i * (S * 3 + 4), 4))
    out = ROOT / "docs/企劃書/1-遊戲玩法/圖片/field_items.png"
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
