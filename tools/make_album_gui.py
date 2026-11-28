"""The photo album's open book (core.field.PhotoScreen): leather cover, two aged paper pages. 320x200."""
import random
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/noclip_backrooms/textures/gui/photo_album.png"
W, H = 320, 200


def main():
    rng = random.Random(7)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, W - 1, H - 1], 7, fill=(84, 48, 30, 255), outline=(40, 22, 14, 255))
    d.rounded_rectangle([3, 3, W - 4, H - 4], 5, outline=(140, 100, 62, 255))
    d.rectangle([7, 7, W - 8, H - 8], fill=(214, 198, 160, 255))
    px = img.load()
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            if not a:
                continue
            n = rng.randint(-7, 7)
            paper = 7 <= x < W - 7 and 7 <= y < H - 7
            if paper:
                # darker toward the spine and the edges of the paper
                n -= max(0, 22 - abs(x - W // 2)) * 2 + max(0, 6 - min(x - 7, W - 8 - x, y - 7, H - 8 - y)) * 4
            px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), a)
    d.line([W // 2, 7, W // 2, H - 8], fill=(120, 96, 66, 255))
    for _ in range(14):  # age spots
        x, y, s = rng.randint(14, W - 20), rng.randint(14, H - 20), rng.randint(1, 3)
        spot = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(spot).ellipse([x, y, x + s, y + s], fill=(130, 96, 52, 60))
        img.alpha_composite(spot)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)


if __name__ == "__main__":
    main()
