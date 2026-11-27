"""The story's assets (docs/企劃書/1-遊戲玩法/README.md, "M.E.G. 任務主線" and "第一趟：跟著纜線走"):
the first trip's cable and glowing door (textures: 64 px for the cable, a prop; 128 px for the door, with labPBR
_n/_s; models, blockstates, item models), and the language lines (the writing on the walls, the researchers' notes,
the main quest's assignments, the extra contracts, the messages).

Run:  py tools/make_story.py
Safe to run again: it rewrites only its own files, and adds language lines only if they are missing.
"""

import json
import math
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/noclip_backrooms"
TEX = ASSETS / "textures/block/story"
DOCS = ROOT / "docs/企劃書/1-遊戲玩法/README.md"
CATEGORY = {"指路": "guide", "有東西": "thing", "迷失": "lost", "研究員": "team", "伏筆": "hint"}


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf8")


def flat_maps(name: str, size: int, height: np.ndarray, smooth: float, glow: np.ndarray | None = None) -> None:
    """labPBR normal (from a height map) and specular maps."""
    gx = np.roll(height, -1, axis=1) - np.roll(height, 1, axis=1)
    gy = np.roll(height, -1, axis=0) - np.roll(height, 1, axis=0)
    nx, ny = -gx * 0.5, -gy * 0.5
    n = np.stack([(nx * 0.5 + 0.5) * 255, (ny * 0.5 + 0.5) * 255, np.full_like(nx, 255), np.full_like(nx, 255)], -1)
    Image.fromarray(np.clip(n, 0, 255).astype(np.uint8), "RGBA").save(TEX / f"{name}_n.png")
    g = np.zeros((size, size)) if glow is None else glow
    s = np.stack([np.full((size, size), smooth * 255), np.full((size, size), 10), np.zeros((size, size)), np.clip(g * 254, 0, 254)], -1)
    Image.fromarray(s.astype(np.uint8), "RGBA").save(TEX / f"{name}_s.png")


def cable(corner: bool) -> None:
    """A thick black cable with yellow bands, lying across the block: straight north-south, or from north round to east."""
    size, width = 64, 9
    rng = np.random.default_rng(7 if corner else 3)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    centre = size / 2
    if corner:
        # A quarter circle round the north-east corner, from the middle of the north edge to the middle of the east edge.
        d.arc((centre, -centre, size + centre, centre), 90, 180, fill=255, width=width)
    else:
        d.rectangle((centre - width / 2, 0, centre + width / 2, size), fill=255)
    m = np.array(mask) > 0
    ys, xs = np.mgrid[0:size, 0:size]
    # How far along the cable each pixel is, for the yellow bands.
    if corner:
        along = ((np.degrees(np.arctan2(ys + 0.0, size - xs)) % 90) / 90) * (math.pi * centre / 2)
    else:
        along = ys.astype(float)
    band = (np.floor(along / 6) % 2 == 0)
    # Rounded: lighter along the middle of the cable, darker at its sides.
    if corner:
        r = np.hypot(xs - size, ys)
        across = np.abs(r - centre) / (width / 2)
    else:
        across = np.abs(xs - centre + 0.5) / (width / 2)
    shade = np.clip(1.0 - across ** 2 * 0.55, 0.35, 1.0)
    noise = rng.normal(0, 0.06, (size, size))
    black = np.array([30, 28, 26]) * (shade + noise)[..., None]
    yellow = np.array([206, 160, 34]) * (shade + noise)[..., None]
    rgb = np.where(band[..., None], yellow, black)
    rgba = np.zeros((size, size, 4))
    rgba[..., :3] = np.clip(rgb, 0, 255)
    rgba[..., 3] = m * 255
    name = "guide_cable_corner" if corner else "guide_cable_straight"
    Image.fromarray(rgba.astype(np.uint8), "RGBA").save(TEX / f"{name}.png")
    flat_maps(name, size, shade * m * 3.0, 0.35)


def door() -> None:
    """A plain steel door, white light coming round its edges and through its wired-glass window."""
    w, h = 128, 256
    rng = np.random.default_rng(11)
    base = np.full((h, w, 3), (196, 200, 196), float)
    base += rng.normal(0, 4, (h, w, 1))
    ys, xs = np.mgrid[0:h, 0:w]
    glow = np.zeros((h, w))
    # The frame: light leaks round the door's edges.
    edge = (xs < 6) | (xs >= w - 6) | (ys < 6)
    base[edge] = (250, 252, 240)
    glow[edge] = 1.0
    # Panel lines and the kick plate.
    for x0 in (14, w - 15):
        base[20:h - 10, x0] *= 0.82
    base[h - 46:h - 10, 14:w - 14] *= 0.9
    # The window, high up: glowing, crossed by wire.
    win = (xs >= 36) & (xs < 92) & (ys >= 40) & (ys < 110)
    base[win] = (255, 250, 222)
    glow[win] = 1.0
    wire = win & (((xs - 36) % 8 == 0) | ((ys - 40) % 8 == 0))
    base[wire] = (150, 150, 140)
    glow[wire] = 0.4
    # The bar handle.
    base[150:158, 20:108] = (150, 154, 150)
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB")
    draw = ImageDraw.Draw(img)
    # "M.E.G." stencilled under the window.
    draw.text((44, 118), "M.E.G.", fill=(70, 80, 90))
    img = img.filter(ImageFilter.GaussianBlur(0.4))
    height = np.zeros((h, w))
    height[20:h - 10, 14:w - 14] = 1.0
    height[150:158, 20:108] = 2.0
    for half, (top, bottom) in (("upper", (0, 128)), ("lower", (128, 256))):
        name = f"guide_door_{half}"
        img.crop((0, top, w, bottom)).save(TEX / f"{name}.png")
        flat_maps(name, 128, height[top:bottom] * 2, 0.55, glow[top:bottom])


def models() -> None:
    block = ASSETS / "models/block/story"
    for name in ("guide_cable_straight", "guide_cable_corner"):
        write_json(block / f"{name}.json", {
            "ambientocclusion": False,
            "textures": {"cable": f"noclip_backrooms:block/story/{name}", "particle": f"noclip_backrooms:block/story/{name}"},
            "elements": [{"from": [0, 0, 0], "to": [16, 0.75, 16], "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#cable"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#cable", "cullface": "down"}}}]})
    for half in ("lower", "upper"):
        tex = f"noclip_backrooms:block/story/guide_door_{half}"
        write_json(block / f"guide_door_{half}.json", {
            "textures": {"door": tex, "particle": tex},
            "elements": [{"from": [0, 0, 13], "to": [16, 16, 16], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#door"},
                "south": {"uv": [16, 0, 0, 16], "texture": "#door", "cullface": "south"},
                "east": {"uv": [0, 0, 3, 16], "texture": "#door"},
                "west": {"uv": [13, 0, 16, 16], "texture": "#door"},
                "up": {"uv": [0, 0, 16, 3], "texture": "#door"},
                "down": {"uv": [0, 13, 16, 16], "texture": "#door"}}}]})
    runs = {"north_south": ("straight", 0), "east_west": ("straight", 90), "north_east": ("corner", 0),
            "east_south": ("corner", 90), "south_west": ("corner", 180), "west_north": ("corner", 270)}
    write_json(ASSETS / "blockstates/guide_cable.json", {"variants": {
        f"run={run}": {"model": f"noclip_backrooms:block/story/guide_cable_{shape}", **({"y": y} if y else {})}
        for run, (shape, y) in runs.items()}})
    facing = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_json(ASSETS / "blockstates/guide_door.json", {"variants": {
        f"facing={f},half={half}": {"model": f"noclip_backrooms:block/story/guide_door_{half}", **({"y": y} if y else {})}
        for f, y in facing.items() for half in ("lower", "upper")}})
    write_json(ASSETS / "items/guide_cable.json", {"model": {"type": "minecraft:model", "model": "noclip_backrooms:block/story/guide_cable_straight"}})
    write_json(ASSETS / "items/guide_door.json", {"model": {"type": "minecraft:model", "model": "noclip_backrooms:block/story/guide_door_lower"}})


def graffiti() -> dict[str, tuple[str, str]]:
    """The writing on the walls, from the table in the design doc: key -> (中文, English)."""
    text = DOCS.read_text(encoding="utf8")
    table = text[text.index("塗鴉的字（先寫一批"):text.index("順序（建議）")]
    counts: dict[str, int] = {}
    out = {}
    for row in table.splitlines():
        cells = [c.strip() for c in row.strip().strip("|").split("|")]
        if len(cells) != 3 or cells[0] not in CATEGORY:
            continue
        kind = CATEGORY[cells[0]]
        n = counts.get(kind, 0)
        counts[kind] = n + 1
        zh, en = cells[1], cells[2]
        if zh.startswith("正字記號"):
            zh, en = "正 正 正 丅", "IIII IIII IIII II"
        out[f"graffiti.noclip_backrooms.{kind}.{n}"] = (zh, en)
    assert counts == {"guide": 10, "thing": 10, "lost": 13, "team": 5, "hint": 4}, counts
    return out


LINES = {
    "block.noclip_backrooms.guide_cable": ("研究員的纜線", "Researchers' Cable"),
    "block.noclip_backrooms.guide_door": ("亮著的門", "The Lit Door"),
    "item.noclip_backrooms.researcher_helmet": ("研究員的安全帽", "Researcher's Hard Hat"),
    "message.noclip_backrooms.first_trip.refuse": ("門的另一邊好像什麼都沒有。地上的纜線一路延伸……",
                                                   "There seems to be nothing on the other side. The cable on the floor runs on..."),
    "message.noclip_backrooms.first_trip.door_shut": ("門打不開。門縫裡的光是冷的。", "The door won't open. The light in the gap is cold."),
    "book.noclip_backrooms.cable_note.0.title": ("纜線日誌（一）", "Cable Log (1)"),
    "book.noclip_backrooms.cable_note.0.page1": (
        "第一天。我們四個人從入口拉著纜線進來，每走五十步就把它壓在地毯上。燈一直在響。凱斯勒說牆好像在動，我說他累了。",
        "Day one. The four of us came in from the entrance paying out the cable, pinning it to the carpet every fifty steps. "
        "The lights won't stop humming. Kessler says the walls are moving. I said he's tired."),
    "book.noclip_backrooms.cable_note.0.page2": ("纜線還剩三捲。只要跟著纜線，就回得去。",
                                                 "Three reels of cable left. As long as we follow the cable, we can get back."),
    "book.noclip_backrooms.cable_note.1.title": ("纜線日誌（二）", "Cable Log (2)"),
    "book.noclip_backrooms.cable_note.1.page1": (
        "不知道第幾天了。凱斯勒說他去看看前面的聲音，就沒有回來。我們沿著纜線找他，纜線在一面牆底下斷了，像被牆吃掉一樣。",
        "I don't know what day it is. Kessler went to look at a sound up ahead and never came back. We followed the cable "
        "to find him. It ends under a wall, as if the wall had eaten it."),
    "book.noclip_backrooms.cable_note.1.page2": ("我們只剩三個人了。", "There are only three of us now."),
    "book.noclip_backrooms.cable_note.2.title": ("纜線日誌（三）", "Cable Log (3)"),
    "book.noclip_backrooms.cable_note.2.page1": (
        "找到門了，門是亮的。無線電說門後面很安全，會有人來接我們。另外兩個人先進去了，我留在這裡等凱斯勒。",
        "We found the door. It's lit. The radio says it's safe on the other side and someone will come for us. "
        "The other two went through first. I'm staying here to wait for Kessler."),
    "book.noclip_backrooms.cable_note.2.page2": ("如果你看到這個，不要等我，跟著纜線走到底。進去以後，不要告訴他們你看到了什麼。",
                                                 "If you find this, don't wait for me. Follow the cable to the end. "
                                                 "Once you're inside, don't tell them what you saw."),
}


# The main quest (core/story/MainQuest): title, M.E.G.'s reason, what to do, and what is said once it is reported.
QUEST = {
    "lobby": (("大廳測繪", "Mapping the Lobby"),
              ("指揮官：「你們能從 Level 0 走出來，就能走回去。我們需要一張大廳的地圖，好派搜救隊進去找失蹤的小隊。」",
               "Commander: \"You walked out of Level 0, so you can walk back in. We need a map of the Lobby to send a rescue team after the missing squad.\""),
              ("到 Level 0，離落點 300 格以上放下測繪信標，等它掃完，帶著磁帶活著回來。",
               "Go to Level 0, set the mapping beacon down at least 300 blocks from where you land, let it finish, and come back alive."),
              ("地圖收下了。指揮官看了一眼：「很好，小隊的路線對上了。」可是研究所的名冊上，那四個人從來沒有出發過。",
               "The map is filed. The commander glances at it: \"Good. It matches the squad's route.\" But by the institute's roster, those four never set out.")),
    "zenith": (("天頂站的回音", "The Zenith's Echo"),
               ("指揮官：「Level 0.1 的天頂站還在發訊號。去看看是誰在回答。只是看看，不用碰任何東西。」",
                "Commander: \"The Zenith Station on Level 0.1 is still transmitting. Go and see who is answering. Just look; don't touch anything.\""),
               ("到 Level 0.1（天頂站），再活著回來。", "Reach Level 0.1 (the Zenith Station) and come back alive."),
               ("你回報站裡沒有人。指揮官點點頭，好像早就知道。訊號用的是研究所自己的呼號。",
                "You report that nobody is there. The commander nods, unsurprised. The signal was using the institute's own call sign.")),
    "parking": (("停車場的笑臉", "Smiles in the Car Park"),
                ("指揮官：「Level 1 停電的時候，黑暗裡會有發光的東西。我們要研究燈光對它的干擾。拍下來就好，別靠太近。」",
                 "Commander: \"When the power goes out on Level 1, something glows in the dark. We are studying how the lights disturb it. A photo will do; don't get close.\""),
                ("到 Level 1，拍下一隻微笑者，再活著回來。", "Go to Level 1, photograph a smiler, and come back alive."),
                ("照片收下了。歸檔的標籤寫的不是「燈光干擾」，是「收容候選」。B1 的觀察窗後面，好像多了一張笑臉。",
                 "The photo is filed under \"containment candidate\", not \"light interference\". Behind the observation glass in B1 there seems to be one more smile.")),
    "pipes": (("管線裡的狗", "Dogs in the Pipes"),
              ("指揮官：「Level 2 的獵犬咬斷了我們的管線讀數。清掉兩隻，讓維修隊進得去。」",
               "Commander: \"Hounds on Level 2 have chewed through our pipe readings. Put down two so the repair crew can get in.\""),
              ("在 Level 2 打倒 2 隻獵犬，再活著回來。", "Put down 2 hounds on Level 2 and come back alive."),
              ("維修隊從來沒有出發。倒是獵犬的屍體被收走了。收容格裡多了一隻獵犬，牠在玻璃後面看著你。",
               "No repair crew ever went in. The hounds' bodies were taken away, though. There is a hound in a containment cell now, watching you through the glass.")),
    "power": (("發電站的讀數", "Readings from the Power Station"),
              ("指揮官：「Level 3 的發電機在輸出某種讀數。到那裡，活著回來，你身上的儀器會記錄下來。」",
               "Commander: \"The generators on Level 3 are putting out some kind of reading. Get there and back alive; the instruments on you will record it.\""),
              ("到 Level 3，再活著回來。", "Reach Level 3 and come back alive."),
              ("儀器上的讀數不是電壓，是一組座標。研究所在找的不是電，是出口。不是給你們用的那種。",
               "The reading on the instruments isn't voltage. It's a set of coordinates. The institute isn't looking for power; it's looking for a way out. Not one meant for you.")),
    "office": (("辦公室的窗", "The Office Windows"),
               ("指揮官：「Level 4 的窗戶後面有東西。我們需要一張清楚的照片。這是最後一件事了，做完你們就可以休息。」",
                "Commander: \"Something is behind the windows on Level 4. We need a clear photo. This is the last thing; after it you can rest.\""),
               ("到 Level 4，拍下一扇「窗戶」，再活著回來。", "Go to Level 4, photograph a \"window\", and come back alive."),
               ("「做得很好，可以休息了。」指揮官這麼說的時候，沒有看你。收容格滿了，B2 的檔案室門縫裡透著光。",
                "\"Well done. You can rest now,\" the commander says, without looking at you. The containment cells are full, and light shows under the door of B2's archive.")),
}


def quest_lines() -> dict[str, tuple[str, str]]:
    out = {}
    for quest, (title, reason, goal, after) in QUEST.items():
        out[f"quest.noclip_backrooms.{quest}.title"] = title
        out[f"quest.noclip_backrooms.{quest}.reason"] = reason
        out[f"quest.noclip_backrooms.{quest}.goal"] = goal
        out[f"quest.noclip_backrooms.{quest}.after"] = after
    return out


MESSAGES = {
    "message.noclip_backrooms.first_trip.welcome.1": ("前哨指揮官：「歡迎來到 M.E.G. 前室研究所。你們進過後室了，這就夠了。」",
                                                      "Outpost commander: \"Welcome to the M.E.G. Frontrooms institute. You have been in the Backrooms. That is enough.\""),
    "message.noclip_backrooms.first_trip.welcome.2": ("「從現在起，你們替 M.E.G. 工作。不用擔心，我們會照顧你們。」",
                                                      "\"From now on you work for M.E.G. Don't worry. We will take care of you.\""),
    "message.noclip_backrooms.quest.header": ("【M.E.G. 指派 第 %s 件】%s", "[M.E.G. assignment %s] %s"),
    "message.noclip_backrooms.quest.progress": ("主線「%s」：%s / %s", "Assignment \"%s\": %s / %s"),
    "message.noclip_backrooms.quest.status": ("進度：%s / %s", "Progress: %s / %s"),
    "message.noclip_backrooms.quest.done": ("主線「%s」做完了。活著回到研究所回報。", "Assignment \"%s\" done. Get back to the institute alive to report."),
    "message.noclip_backrooms.quest.come_back": ("這件已經做完了，活著回研究所回報。", "This one is done; get back to the institute alive to report."),
    "message.noclip_backrooms.quest.reported": ("回報了「%s」：%s 點交進配額。", "Reported \"%s\": %s points toward the quota."),
    "message.noclip_backrooms.quest.beacon": ("指揮官遞給你一個測繪信標。", "The commander hands you a mapping beacon."),
    "message.noclip_backrooms.quest.finished": ("M.E.G. 沒有新的指派了。B2 的機密檔案室裡，也許有你該知道的事。",
                                                "M.E.G. has no more assignments. B2's classified archive may hold something you ought to know."),
    "contract.noclip_backrooms.photo_blackout_level0": ("一直閃的燈：在 Level 0 停電的那一刻拍照，再活著回來",
                                                        "The Flickering Lights: photograph a blackout in Level 0 as it happens, and return alive"),
    "contract.noclip_backrooms.survey_level0p1": ("勘查天頂站：到 Level 0.1，再活著回來", "Survey the Zenith: reach Level 0.1 and return alive"),
    "contract.noclip_backrooms.photo_duller": ("無聲的灰影：在後室拍下一隻鈍人，再活著回來", "The Silent Grey: photograph a duller in the Backrooms and return alive"),
    "contract.noclip_backrooms.survey_level4": ("勘查辦公室：到 Level 4，再活著回來", "Survey the Offices: reach Level 4 and return alive"),
    # The town and the nights of 398 (frontrooms/TownLife, frontrooms/Nights398).
    "entity.noclip_backrooms.town_gunsmith": ("睡不好的槍店老闆", "The Gunsmith Who Can't Sleep"),
    "message.noclip_backrooms.398.faceless_passed": ("沒有臉的人讓開了。（%s / %s）", "The people without faces step aside. (%s / %s)"),
    "message.noclip_backrooms.398.faceless_struck": ("你碰了沒有臉的人。今晚它們不會再讓路了。",
                                                     "You laid a hand on one of the people without faces. Tonight they will not step aside again."),
    "message.noclip_backrooms.398.letters_missing": ("沒有臉的人都讓開了，路的盡頭還是黑的。好像還有家裡的信沒看完。",
                                                     "The people without faces have all stepped aside, but the end of the street is still dark. Some letter from home is still unread."),
    "message.noclip_backrooms.398.door_lit": ("路的盡頭，有一扇門亮了。", "At the end of the street, a door has lit up."),
    "message.noclip_backrooms.398.door_shut": ("門打不開。", "The door won't open."),
    "ending.noclip_backrooms.home.title": ("回家了", "Home At Last"),
    "ending.noclip_backrooms.home.subtitle": ("門的另一邊是你家的玄關。燈是暖的，沒有嗡嗡聲。",
                                              "On the other side of the door is your own front hall. The light is warm, and nothing hums."),
}


def add_lang(lines: dict[str, tuple[str, str]]) -> None:
    for file, which in (("zh_tw.json", 0), ("en_us.json", 1)):
        path = ASSETS / "lang" / file
        with open(path, encoding="utf8", newline="") as f:
            text = f.read()
        have = json.loads(text)
        new = {k: v[which] for k, v in lines.items() if k not in have}
        if not new:
            continue
        nl = "\r\n" if "\r\n" in text else "\n"
        body = text.rstrip()
        assert body.endswith("}")
        body = body[:-1].rstrip()
        added = "".join(f",{nl}  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}" for k, v in new.items())
        path.write_text(body + added + nl + "}" + nl, encoding="utf8", newline="")
        json.loads(path.read_text(encoding="utf8"))


def main() -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    cable(False)
    cable(True)
    door()
    models()
    add_lang({**graffiti(), **LINES, **quest_lines(), **MESSAGES})
    print("first trip assets written")


if __name__ == "__main__":
    main()
