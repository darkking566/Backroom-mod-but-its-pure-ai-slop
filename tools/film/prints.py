"""The film's fabrics, pictures and signs, painted as images for make_film_props.py.

Fabrics tile seamlessly (projected onto the furniture); pictures and signs are whole images laid on one face.
Run: py prints.py <out_dir>
"""
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(sys.argv[1] if len(sys.argv) > 1 else "prints")
OUT.mkdir(parents=True, exist_ok=True)
FONTS = Path("C:/Windows/Fonts")


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def tiled(draw_fn, size=512, bg=(0, 0, 0), seed=0, blur=0.0):
    """Draws shapes 3x3 times around the tile so that whatever crosses an edge comes back on the other side."""
    big = Image.new("RGB", (size * 3, size * 3), bg)
    d = ImageDraw.Draw(big)
    rnd = random.Random(seed)
    shapes = draw_fn(rnd, size)
    for ox in (0, size, 2 * size):
        for oy in (0, size, 2 * size):
            for shape in shapes:
                shape(d, ox, oy)
    if blur:
        big = big.filter(ImageFilter.GaussianBlur(blur))
    return big.crop((size, size, size * 2, size * 2))


def rotated_ellipse(cx, cy, rx, ry, angle, colour):
    pts = []
    for i in range(24):
        t = i / 24 * math.tau
        x, y = math.cos(t) * rx, math.sin(t) * ry
        pts.append((cx + x * math.cos(angle) - y * math.sin(angle), cy + x * math.sin(angle) + y * math.cos(angle)))
    return lambda d, ox, oy: d.polygon([(px + ox, py + oy) for px, py in pts], fill=colour)


def tropical():
    """Mary's couch: big pale-blue leaves and brush strokes on a grey-blue ground."""
    def shapes(rnd, s):
        out = []
        for _ in range(26):
            cx, cy = rnd.uniform(0, s), rnd.uniform(0, s)
            a = rnd.uniform(0, math.pi)
            col = rnd.choice([(150, 172, 192), (120, 146, 170), (196, 208, 214), (96, 122, 150)])
            out.append(rotated_ellipse(cx, cy, rnd.uniform(40, 90), rnd.uniform(9, 20), a, col))
            for k in range(6):
                out.append(rotated_ellipse(cx + math.cos(a) * (k - 3) * 14, cy + math.sin(a) * (k - 3) * 14,
                                           rnd.uniform(18, 30), 3, a + 1.0, (82, 106, 134)))
        return out
    return tiled(shapes, bg=(176, 190, 202), seed=1, blur=1.2)


def floral():
    """A pastel loveseat print: peach and rose blooms, sage leaves, on a pale mint ground."""
    def shapes(rnd, s):
        out = []
        for _ in range(40):
            cx, cy, a = rnd.uniform(0, s), rnd.uniform(0, s), rnd.uniform(0, math.tau)
            out.append(rotated_ellipse(cx, cy, 26, 9, a, (138, 160, 128)))
        for _ in range(22):
            cx, cy = rnd.uniform(0, s), rnd.uniform(0, s)
            col = rnd.choice([(226, 168, 150), (210, 140, 146), (236, 196, 170)])
            for k in range(5):
                t = k / 5 * math.tau
                out.append(rotated_ellipse(cx + math.cos(t) * 11, cy + math.sin(t) * 11, 13, 9, t, col))
            out.append(rotated_ellipse(cx, cy, 7, 7, 0, (182, 104, 110)))
        return out
    return tiled(shapes, bg=(206, 220, 206), seed=2, blur=1.0)


def diamond():
    """The accent chair and ottoman: small gold diamonds on olive."""
    s = 512
    img = Image.new("RGB", (s, s), (104, 96, 58))
    d = ImageDraw.Draw(img)
    n = 16
    step = s // n
    for i in range(n + 1):
        for j in range(n + 1):
            cx, cy = i * step + (step // 2 if j % 2 else 0), j * step
            r = step * 0.36
            d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=(176, 150, 82))
            d.point((cx, cy), fill=(70, 62, 34))
    return img


def plaid():
    """Dining chair seats: a muted green, tan and cream check."""
    s = 512
    a = np.zeros((s, s, 3), np.float32) + np.array([190, 176, 140], np.float32)
    x = np.arange(s)
    for period, width, col, k in ((128, 40, (88, 110, 84), 0.7), (128, 12, (150, 70, 60), 0.6), (64, 6, (238, 228, 200), 0.5)):
        band = ((x % period) < width).astype(np.float32)
        for axis in (0, 1):
            m = (band[:, None] if axis == 0 else band[None, :]) * k
            a = a * (1 - m[..., None] * 0.5) + np.array(col, np.float32) * (m[..., None] * 0.5)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def matelasse():
    """The cream sofas: quilted diamonds stitched into cream cloth."""
    s = 512
    img = Image.new("RGB", (s, s), (226, 220, 200))
    d = ImageDraw.Draw(img)
    step = 64
    for k in range(-s, 2 * s, step):
        d.line([(k, 0), (k + s, s)], fill=(186, 178, 156), width=5)
        d.line([(k + s, 0), (k, s)], fill=(186, 178, 156), width=5)
    for i in range(0, s + step, step):
        for j in range(0, s + step, step):
            d.ellipse([i - 6, j - 6, i + 6, j + 6], fill=(200, 192, 170))
    return img.filter(ImageFilter.GaussianBlur(1.5))


def comforter():
    """The showroom bed's comforter: a 90s teal, white and mauve geometric quilt."""
    def shapes(rnd, s):
        out = []
        for _ in range(30):
            cx, cy = rnd.uniform(0, s), rnd.uniform(0, s)
            w, h = rnd.uniform(30, 120), rnd.uniform(8, 30)
            col = rnd.choice([(60, 128, 130), (130, 90, 112), (210, 200, 180), (40, 90, 100)])
            a = rnd.choice([0.0, math.pi / 4, -math.pi / 4])
            out.append(rotated_ellipse(cx, cy, w, h, a, col) if rnd.random() < 0.3 else
                       (lambda cx=cx, cy=cy, w=w, h=h, col=col: (lambda d, ox, oy: d.rectangle([cx + ox - w / 2, cy + oy - h / 2, cx + ox + w / 2, cy + oy + h / 2], fill=col)))())
        return out
    return tiled(shapes, bg=(232, 226, 210), seed=3, blur=0.8)


def oriental():
    """A burgundy and navy rug with a border and a central medallion (one image across the rug)."""
    s = 512
    img = Image.new("RGB", (s, s), (122, 34, 40))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, s - 1, s - 1], outline=(36, 40, 74), width=46)
    d.rectangle([46, 46, s - 47, s - 47], outline=(214, 190, 140), width=10)
    for i in range(0, s, 32):
        for x, y in ((i, 23), (i, s - 23), (23, i), (s - 23, i)):
            d.polygon([(x, y - 10), (x + 10, y), (x, y + 10), (x - 10, y)], fill=(176, 130, 80))
    c = s // 2
    for r, col in ((150, (36, 40, 74)), (120, (214, 190, 140)), (96, (122, 34, 40)), (60, (36, 40, 74)), (24, (214, 190, 140))):
        d.polygon([(c, c - r), (c + r, c), (c, c + r), (c - r, c)], fill=col)
    for x, y in ((110, 110), (s - 110, 110), (110, s - 110), (s - 110, s - 110)):
        d.polygon([(x, y - 40), (x + 40, y), (x, y + 40), (x - 40, y)], fill=(36, 40, 74))
    return img.filter(ImageFilter.GaussianBlur(0.8))


def watercolor():
    """Mary's print: a pale watercolour of dune grass under a blue sky, on a white mat (the whole framed face)."""
    w, h = 512, 384
    img = Image.new("RGB", (w, h), (236, 232, 222))
    paint = Image.new("RGB", (w - 120, h - 120), (220, 226, 230))
    d = ImageDraw.Draw(paint)
    rnd = random.Random(4)
    pw, ph = paint.size
    for _ in range(60):
        x, y = rnd.uniform(0, pw), rnd.uniform(0, ph * 0.55)
        d.ellipse([x - 40, y - 14, x + 40, y + 14], fill=rnd.choice([(110, 146, 190), (150, 176, 210), (88, 120, 170)]))
    for _ in range(40):
        x, y = rnd.uniform(0, pw), rnd.uniform(ph * 0.62, ph)
        d.ellipse([x - 50, y - 10, x + 50, y + 10], fill=rnd.choice([(212, 150, 170), (190, 120, 150), (226, 180, 186)]))
    for _ in range(70):
        x = pw * 0.6 + rnd.uniform(-60, 60)
        d.line([(x, ph * 0.72), (x + rnd.uniform(-40, 40), ph * 0.72 - rnd.uniform(40, 110))], fill=(60, 82, 70), width=3)
    paint = paint.filter(ImageFilter.GaussianBlur(3))
    img.paste(paint, (60, 60))
    return img


def sign(lines, size=(512, 384), bg=(244, 240, 228), border=None):
    """A store sign: lines of (text, font, size, colour, outline colour)."""
    img = Image.new("RGB", size, bg)
    d = ImageDraw.Draw(img)
    if border:
        d.rectangle([4, 4, size[0] - 5, size[1] - 5], outline=border, width=8)
    heights = []
    for text, fname, fsize, col, outline in lines:
        f = font(fname, fsize)
        box = d.textbbox((0, 0), text, font=f, stroke_width=4 if outline else 0)
        heights.append((box[2] - box[0], box[3] - box[1], box[1], f))
    total = sum(h for _, h, _, _ in heights) + 14 * (len(lines) - 1)
    y = (size[1] - total) / 2
    for (text, fname, fsize, col, outline), (tw, th, top, f) in zip(lines, heights):
        d.text(((size[0] - tw) / 2, y - top), text, font=f, fill=col, stroke_width=4 if outline else 0, stroke_fill=outline)
        y += th + 14
    return img


def burst(img, cx, cy, r, col):
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(24):
        rr = r if i % 2 == 0 else r * 0.62
        t = i / 24 * math.tau
        pts.append((cx + math.cos(t) * rr, cy + math.sin(t) * rr))
    d.polygon(pts, fill=col)
    return img


def main():
    tropical().save(OUT / "tropical.png")
    floral().save(OUT / "floral.png")
    diamond().save(OUT / "diamond.png")
    plaid().save(OUT / "plaid.png")
    matelasse().save(OUT / "matelasse.png")
    comforter().save(OUT / "comforter.png")
    oriental().save(OUT / "oriental.png")
    watercolor().save(OUT / "watercolor.png")
    red, orange, dark = (196, 30, 40), (226, 104, 30), (40, 30, 30)
    sign([("SALE", "COOPBL.TTF", 150, red, dark), ("UP TO 75%", "ariblk.ttf", 70, red, None)]).save(OUT / "sign_sale75.png")
    sign([("EVERYTHING", "ariblk.ttf", 66, red, None), ("MUST", "COOPBL.TTF", 110, orange, dark), ("GO!", "COOPBL.TTF", 110, orange, dark)]).save(OUT / "sign_must_go.png")
    on_sale = Image.new("RGB", (512, 384), (244, 240, 228))
    burst(on_sale, 70, 70, 70, (240, 196, 40))
    on_sale.paste(sign([("ON", "COOPBL.TTF", 120, orange, dark), ("SALE", "COOPBL.TTF", 150, orange, dark)],
                       bg=(244, 240, 228)).crop((0, 0, 512, 384)), (0, 0), None)
    burst(on_sale, 70, 70, 64, (240, 196, 40))
    on_sale.save(OUT / "sign_on_sale.png")
    sign([("NO CREDIT NEEDED!", "COOPBL.TTF", 78, red, dark)], size=(1024, 192), bg=(240, 236, 226)).save(OUT / "sign_no_credit.png")
    sign([("WAY", "ariblk.ttf", 70, red, None), ("DOWN", "ariblk.ttf", 70, red, None)], size=(256, 256), bg=(244, 236, 220)).save(OUT / "sign_way_down.png")
    tag = sign([("SALE", "ariblk.ttf", 44, (40, 40, 60), None), ("$199", "Inkfree.ttf", 76, (30, 30, 90), None)], size=(192, 192), bg=(238, 150, 170))
    tag.save(OUT / "price_tag.png")
    tag2 = sign([("NOW", "ariblk.ttf", 40, (40, 40, 60), None), ("$89", "Inkfree.ttf", 84, (30, 30, 90), None)], size=(192, 192), bg=(244, 222, 120))
    tag2.save(OUT / "price_tag_yellow.png")
    pirate = sign([("PIRATE", "COOPBL.TTF", 90, (236, 196, 60), dark), ("PRICES!", "COOPBL.TTF", 110, (236, 196, 60), dark),
                   ("ALL FURNITURE", "ariblk.ttf", 44, (240, 236, 226), None)], size=(768, 512), bg=(34, 44, 84), border=(236, 196, 60))
    pirate.save(OUT / "sign_pirate.png")
    print("PRINTS", len(list(OUT.glob("*.png"))))


main()
