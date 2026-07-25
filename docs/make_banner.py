#!/usr/bin/env python3
"""Generate docs/banner.png for the Wallbreaker README.

Regenerable: rasterises the app's own Throughline mark, fetches the Freedium
icon, reuses the bundled Instapaper badge, and composes the header image.
Run from the repo root:  python3 docs/make_banner.py

Requires: PIL (pip/uv), macOS `qlmanage` (Quick Look, for SVG -> PNG), `curl`.
"""
import os
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "banner.png")
LOGO_SVG = os.path.join(ROOT, "docs", "logo-concepts", "2-throughline.svg")
INSTAPAPER_PNG = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "ic_instapaper.png")

W, H = 1280, 344
ACCENT = (232, 80, 58)          # Wallbreaker coral, from the Throughline mark
INK, GREY, LINE = (20, 23, 28), (100, 110, 124), (223, 228, 234)

FONTS = "/System/Library/Fonts/Supplemental/"
f_title = ImageFont.truetype(FONTS + "Arial Bold.ttf", 64)
f_tag = ImageFont.truetype(FONTS + "Arial.ttf", 22)
f_sub = ImageFont.truetype(FONTS + "Arial.ttf", 17)
f_lbl = ImageFont.truetype(FONTS + "Arial Bold.ttf", 15)

tmp = tempfile.mkdtemp(prefix="wb-banner-")


def download(url, dest):
    """curl, not urllib — some CDNs 403 a default urllib agent."""
    subprocess.run(["curl", "-fsSL", "-A", "Mozilla/5.0", "-o", dest, url], check=True)
    return dest


def rasterize_svg(svg_path, size=512):
    """Quick Look thumbnail: fine here because the mark is opaque (has its own
    background rect), so there's no white-matte-over-transparency problem."""
    subprocess.run(["qlmanage", "-t", "-s", str(size), "-o", tmp, svg_path],
                    capture_output=True, check=True)
    return Image.open(os.path.join(tmp, os.path.basename(svg_path) + ".png")).convert("RGBA")


def rounded(im, size, radius):
    im = im.convert("RGBA").resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius, fill=255)
    im.putalpha(mask)
    return im


# --- canvas -----------------------------------------------------------------
img = Image.new("RGB", (W, H), (255, 255, 255))
d = ImageDraw.Draw(img)
for y in range(H):                                   # subtle vertical wash
    t = y / H
    d.line([(0, y), (W, y)], fill=(int(255 - 5 * t), int(255 - 12 * t), int(255 - 14 * t)))
d.rectangle([0, H - 5, W, H], fill=ACCENT)           # brand hairline

# --- left: identity -----------------------------------------------------
mark = rasterize_svg(LOGO_SVG)
icon = rounded(mark, 88, 20)
img.paste(icon, (64, 78), icon)

d.text((172, 82), "Wallbreaker", font=f_title, fill=INK)
d.text((176, 158), "Paywall-free saves, straight to Instapaper.", font=f_tag, fill=ACCENT)
d.text((176, 192), "Any article Freedium can unlock, not just Medium —", font=f_sub, fill=GREY)
d.text((176, 214), "saved with the clean link and the full text.", font=f_sub, fill=GREY)

# True canvas centre — and the right-side pipeline is centred within its own
# half below, so the divider reads as an actual 50/50 split, not a line that
# just happens to clear the left column's text.
d.line([(640, 74), (640, 254)], fill=LINE, width=2)

# --- right: the pipeline -----------------------------------------------
FREEDIUM = Image.open(download("https://freedium-mirror.cfd/apple-touch-icon.png",
                                os.path.join(tmp, "freedium.png"))).convert("RGBA")
INSTAPAPER = Image.open(INSTAPAPER_PNG).convert("RGBA")

CHIP = 92
steps = [(FREEDIUM, "Freedium"), (INSTAPAPER, "Instapaper")]
xs = [850, 1070]     # centred within the right half (640-1280, midpoint 960)
cy = 158

for (glyph, label), cx in zip(steps, xs):
    box = [cx - CHIP // 2, cy - CHIP // 2, cx + CHIP // 2, cy + CHIP // 2]
    d.rounded_rectangle(box, 20, fill=(255, 255, 255), outline=LINE, width=2)  # chip plate
    inner = 64
    art = rounded(glyph, inner, 14)
    img.paste(art, (cx - inner // 2, cy - inner // 2), art)
    tw = d.textlength(label, font=f_lbl)
    d.text((cx - tw / 2, cy + CHIP // 2 + 16), label, font=f_lbl, fill=GREY)

# arrow between chips
x0, x1 = xs[0] + CHIP // 2 + 20, xs[1] - CHIP // 2 - 20
d.line([(x0, cy), (x1 - 8, cy)], fill=(196, 203, 212), width=3)
d.polygon([(x1, cy), (x1 - 10, cy - 6), (x1 - 10, cy + 6)], fill=(196, 203, 212))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", OUT, img.size)
