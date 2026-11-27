"""Pictures for the institute's objects (laid on panels by make_institute_props.py): a chest X-ray on a lit viewer, green
text on a CRT, the stencilled M.E.G. decal and a biohazard label. Written next to the film's prints.

Run: py tools/film/institute_prints.py tools/film/prints
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "tools/film/prints")
OUT.mkdir(parents=True, exist_ok=True)


def font(size, bold=False):
    for name in (("consolab.ttf" if bold else "consola.ttf"), "cour.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def xray():
    """A chest film on the light box: ribs, spine, collarbones, the heart's shadow; a patient label in a corner."""
    w, h = 512, 640
    img = Image.new("L", (w, h), 14)
    d = ImageDraw.Draw(img)
    cx = w // 2
    # Lungs: soft dark lobes inside a lighter body outline.
    d.ellipse((60, 70, 460, 640), fill=58)
    d.ellipse((90, 130, 240, 560), fill=26)
    d.ellipse((272, 130, 422, 560), fill=26)
    # Spine.
    for i in range(16):
        y = 70 + i * 36
        d.rounded_rectangle((cx - 18, y, cx + 18, y + 28), 6, fill=150)
    # Ribs: arcs on both sides.
    for i in range(10):
        y = 120 + i * 40
        for side in (-1, 1):
            box = (cx - 200 if side < 0 else cx - 20, y - 30, cx + 20 if side < 0 else cx + 200, y + 70 + i * 4)
            d.arc(box, 200 if side < 0 else 280, 340 if side < 0 else 340 + 0 if side < 0 else 340, fill=170, width=9)
            start, end = (190, 330) if side < 0 else (210, 350)
            d.arc(box, start, end, fill=175, width=9)
    # Collarbones and the heart.
    d.line((cx - 30, 110, cx - 190, 80), fill=185, width=14)
    d.line((cx + 30, 110, cx + 190, 80), fill=185, width=14)
    d.ellipse((cx - 40, 330, cx + 130, 520), fill=96)
    img = img.filter(ImageFilter.GaussianBlur(3))
    rgb = Image.merge("RGB", [img.point(lambda v: v * 0.85), img.point(lambda v: v * 0.95), img.point(lambda v: min(255, v * 1.1 + 8))])
    d = ImageDraw.Draw(rgb)
    d.text((20, 18), "M.E.G. 04-11  R", fill=(220, 230, 240), font=font(26, True))
    d.text((20, 600), "SUBJ. ##### / LVL 0 RETURN", fill=(200, 210, 220), font=font(18))
    rgb.save(OUT / "inst_xray.png")


def crt_text():
    """Green phosphor text on a dark screen, scanlines, a bright cursor."""
    w, h = 512, 384
    img = Image.new("RGB", (w, h), (6, 18, 10))
    d = ImageDraw.Draw(img)
    f = font(20)
    lines = ["M.E.G. FIELD TELEMETRY  v2.3", "--------------------------------", "SUBJECT    LEVEL   STATUS",
             "R-0417     0       RETURNED", "R-0418     1       NO SIGNAL", "R-0419     0       QUARANTINE",
             "R-0420     2       NO SIGNAL", "", "CONTAINMENT B1-03 ..... SEALED", "CONTAINMENT B1-04 ..... OCCUPIED",
             "SAMPLE INTAKE ......... PENDING", "", "> _"]
    for i, line in enumerate(lines):
        d.text((18, 14 + i * 27), line, fill=(90, 255, 140), font=f)
    glow = img.filter(ImageFilter.GaussianBlur(3))
    img = Image.blend(img, glow, 0.35)
    px = img.load()
    for y in range(0, h, 3):
        for x in range(w):
            r, g, b = px[x, y]
            px[x, y] = (int(r * 0.6), int(g * 0.6), int(b * 0.6))
    img.save(OUT / "inst_crt_text.png")


def decal():
    """The stencilled M.E.G. mark on equipment: letters in a ring, sprayed through a stencil."""
    w = 256
    img = Image.new("RGB", (w, w), (214, 210, 196))
    d = ImageDraw.Draw(img)
    d.ellipse((20, 20, 236, 236), outline=(28, 40, 86), width=16)
    d.text((54, 86), "M.E.G.", fill=(28, 40, 86), font=font(54, True))
    rng = random.Random(4)
    for _ in range(600):
        x, y = rng.randrange(w), rng.randrange(w)
        img.putpixel((x, y), (200, 196, 182))
    img.save(OUT / "inst_decal.png")


def biohazard():
    """A yellow warning label with the trefoil, as on the containment doors."""
    w = 256
    img = Image.new("RGB", (w, w), (226, 184, 30))
    d = ImageDraw.Draw(img)
    c = w // 2
    for a in (90, 210, 330):
        x, y = c + 46 * math.cos(math.radians(a)), c - 46 * math.sin(math.radians(a))
        d.ellipse((x - 40, y - 40, x + 40, y + 40), fill=(24, 22, 20))
        x2, y2 = c + 62 * math.cos(math.radians(a)), c - 62 * math.sin(math.radians(a))
        d.ellipse((x2 - 26, y2 - 26, x2 + 26, y2 + 26), fill=(226, 184, 30))
    d.ellipse((c - 18, c - 18, c + 18, c + 18), fill=(226, 184, 30))
    d.ellipse((c - 10, c - 10, c + 10, c + 10), fill=(24, 22, 20))
    d.rectangle((6, 6, w - 7, w - 7), outline=(24, 22, 20), width=8)
    img.save(OUT / "inst_biohazard.png")


def specimen():
    """What floats in a tank: murky green, bubbles, a curled dark shape with too many limbs."""
    w, h = 256, 512
    img = Image.new("RGB", (w, h), (52, 86, 58))
    d = ImageDraw.Draw(img)
    for y in range(h):
        shade = int(18 * math.sin(y / 40.0))
        d.line((0, y, w, y), fill=(52 + shade // 2, 86 + shade, 58 + shade // 2))
    d.ellipse((60, 150, 196, 330), fill=(22, 30, 24))
    d.ellipse((90, 110, 160, 175), fill=(26, 34, 28))
    for a in range(7):
        ang = math.radians(200 + a * 22)
        x0, y0 = 128 + 50 * math.cos(ang), 250 + 70 * math.sin(ang)
        d.line((x0, y0, x0 + 70 * math.cos(ang + 0.6), y0 + 90 * math.sin(ang + 0.6)), fill=(24, 32, 26), width=9)
    rng = random.Random(7)
    for _ in range(40):
        x, y, r = rng.randrange(w), rng.randrange(h), rng.randrange(2, 7)
        d.ellipse((x - r, y - r, x + r, y + r), outline=(150, 200, 160), width=1)
    img = img.filter(ImageFilter.GaussianBlur(2.5))
    img.save(OUT / "inst_specimen.png")


def threshold():
    """Through the gate: a yellow room going on too long, its lights in rows, as the cameras show Level 0."""
    w, h = 384, 640
    img = Image.new("RGB", (w, h), (196, 172, 88))
    d = ImageDraw.Draw(img)
    vx, vy = w // 2, int(h * 0.46)
    d.polygon([(0, 0), (w, 0), (vx + 30, vy - 30), (vx - 30, vy - 30)], fill=(214, 204, 150))
    d.polygon([(0, h), (w, h), (vx + 30, vy + 40), (vx - 30, vy + 40)], fill=(150, 128, 60))
    d.polygon([(0, 0), (vx - 30, vy - 30), (vx - 30, vy + 40), (0, h)], fill=(186, 160, 78))
    d.polygon([(w, 0), (vx + 30, vy - 30), (vx + 30, vy + 40), (w, h)], fill=(176, 152, 72))
    d.rectangle((vx - 30, vy - 30, vx + 30, vy + 40), fill=(60, 52, 26))
    for i in range(6):
        t = 0.12 + i * 0.15
        y = int(t * (vy - 30))
        half = int((1 - t) * 90) + 8
        d.rectangle((vx - half, y, vx + half, y + max(3, int(14 * (1 - t)))), fill=(255, 250, 220))
    img = img.filter(ImageFilter.GaussianBlur(1.5))
    img.save(OUT / "inst_threshold.png")


def clock_face():
    """The street clock's dial: black numerals on white, the hands at two minutes to midnight."""
    w = 256
    img = Image.new("RGB", (w, w), (236, 232, 220))
    d = ImageDraw.Draw(img)
    c = w // 2
    d.ellipse((6, 6, w - 7, w - 7), outline=(30, 30, 30), width=8)
    f = font(30, True)
    for h in range(1, 13):
        a = math.radians(90 - h * 30)
        x, y = c + 92 * math.cos(a), c - 92 * math.sin(a)
        text = str(h)
        box = d.textbbox((0, 0), text, font=f)
        d.text((x - (box[2] - box[0]) / 2, y - (box[3] - box[1]) / 2 - 4), text, fill=(24, 24, 24), font=f)
    for m in range(60):
        a = math.radians(90 - m * 6)
        r0 = 110 if m % 5 else 104
        d.line((c + r0 * math.cos(a), c - r0 * math.sin(a), c + 116 * math.cos(a), c - 116 * math.sin(a)), fill=(30, 30, 30), width=2)
    for angle, length, width in ((90 - 359 * 0.5, 58, 8), (90 - 58 * 6, 90, 5)):
        a = math.radians(angle)
        d.line((c, c, c + length * math.cos(a), c - length * math.sin(a)), fill=(20, 20, 20), width=width)
    d.ellipse((c - 7, c - 7, c + 7, c + 7), fill=(160, 30, 24))
    img.save(OUT / "town_clock_face.png")


def shelf_goods():
    """A shelf front of tins and boxes: faded labels, a few gaps."""
    w, h = 256, 128
    img = Image.new("RGB", (w, h), (70, 60, 48))
    d = ImageDraw.Draw(img)
    rng = random.Random(11)
    x = 4
    colours = [(170, 40, 34), (210, 180, 60), (60, 110, 150), (200, 200, 190), (90, 140, 70), (220, 120, 40)]
    while x < w - 10:
        wide = rng.choice((14, 18, 22, 30))
        if rng.random() < 0.12:
            x += wide
            continue
        top = rng.randrange(18, 50)
        colour = rng.choice(colours)
        d.rectangle((x, top, x + wide - 2, h - 4), fill=colour)
        d.rectangle((x, top + (h - top) // 3, x + wide - 2, top + (h - top) // 3 + 8), fill=(235, 230, 215))
        x += wide
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    img.save(OUT / "town_shelf_goods.png")


def store_sign():
    """The corner store's sign: cream letters on a faded red board, OPEN 24 HRS under them."""
    w, h = 640, 160
    img = Image.new("RGB", (w, h), (150, 34, 30))
    d = ImageDraw.Draw(img)
    d.rectangle((6, 6, w - 7, h - 7), outline=(230, 222, 196), width=6)
    f = font(78, True)
    box = d.textbbox((0, 0), "CORNER STORE", font=f)
    d.text(((w - (box[2] - box[0])) / 2, 14), "CORNER STORE", fill=(236, 228, 200), font=f)
    g = font(30, True)
    box = d.textbbox((0, 0), "OPEN 24 HRS", font=g)
    d.text(((w - (box[2] - box[0])) / 2, 106), "OPEN 24 HRS", fill=(236, 228, 200), font=g)
    img = img.filter(ImageFilter.GaussianBlur(0.5))
    img.save(OUT / "town_store_sign.png")


xray()
crt_text()
decal()
biohazard()
specimen()
threshold()
clock_face()
shelf_goods()
store_sign()
print("institute prints written to", OUT)
