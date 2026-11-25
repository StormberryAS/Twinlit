#!/usr/bin/env python3
"""Twinlit's launcher icon, option b1 "Sunset, moon above" (Marcos, 2026-11-22, D9, DECISIONS D114).

One source: the three SVG layers in tools/launcher-icon/ (background.svg, foreground.svg and
monochrome.svg, a 108 x 108 adaptive icon; the design and its reasons are in launcher-icon/notes.md).
They hold only paths, fills, round-cap strokes and user-space gradients, so each one converts to an
Android vector drawable here and renders to a PNG with headless Chromium.

Writes, from those three files:
  app/src/main/res/drawable/ic_launcher_background.xml   adaptive background (the sky gradient)
  app/src/main/res/drawable/ic_launcher_foreground.xml   adaptive foreground (sun, horizon, moon, star)
  app/src/main/res/drawable/ic_launcher_monochrome.xml   the themed-icon layer (Android 13 and later)
  app/src/main/res/drawable/labs_switcher_twinlit.xml    the Labs carousel card's icon (the whole tile)
  app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png and ic_launcher_round.png
                                                          the icon on API 24 and 25, where no adaptive icon exists
  ../fastlane/metadata/android/en-US/images/icon.png      the 512 px store icon (Play and Zapstore)
  ../fastlane/metadata/android/en-US/images/featureGraphic.png  the 1024 x 500 Play feature graphic

Run from the android/ directory:  python3 tools/build-launcher-icon.py
Needs Chromium (CHROMIUM=/path to override /usr/bin/chromium) for the PNGs and Pillow to compose and
save them (python3 -m pip install pillow). Nothing else, no network. To change the icon, change the
three SVG files, run this again and commit the output.
"""
import os
import pathlib
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = pathlib.Path(__file__).resolve().parent / "launcher-icon"
RES = ROOT / "app/src/main/res"
STORE = ROOT.parent / "fastlane/metadata/android/en-US/images"
FONTS = RES / "font"
CHROMIUM = os.environ.get("CHROMIUM", "/usr/bin/chromium")

SVG = "{http://www.w3.org/2000/svg}"
NIGHT = "#080C18"
SILVER = "#C8D8F0"
GOLD = "#FFD700"
VIEWPORT = 108
# A launcher shows the middle 72 of the 108 units of an adaptive icon, whatever the mask. The legacy
# PNGs and the store icon show the same window, so every form of the icon looks alike.
WINDOW = 72
TILE_RADIUS = 0.22  # the corner radius of the square legacy icon and of the carousel card, as a share of its side


# ---------- SVG layer to Android vector drawable ----------

def argb(colour):
    """#RRGGBB as the #AARRGGBB an Android resource wants."""
    if not re.fullmatch(r"#[0-9A-Fa-f]{6}", colour):
        raise ValueError(f"unsupported colour {colour!r}")
    return "#FF" + colour[1:].upper()


def num(text):
    return f"{float(text):g}"


def gradients(root):
    out = {}
    for g in root.iter():
        tag = g.tag.replace(SVG, "")
        if tag not in ("linearGradient", "radialGradient"):
            continue
        if g.get("gradientUnits") != "userSpaceOnUse":
            raise ValueError(f"gradient {g.get('id')} must use userSpaceOnUse")
        stops = [(num(s.get("offset")), argb(s.get("stop-color"))) for s in g if s.tag == SVG + "stop"]
        out[g.get("id")] = (tag, dict(g.attrib), stops)
    return out


def gradient_xml(grad, indent, attr):
    tag, a, stops = grad
    pad = " " * indent
    if tag == "linearGradient":
        head = (f'android:type="linear" android:startX="{num(a["x1"])}" android:startY="{num(a["y1"])}" '
                f'android:endX="{num(a["x2"])}" android:endY="{num(a["y2"])}"')
    else:
        head = (f'android:type="radial" android:centerX="{num(a["cx"])}" android:centerY="{num(a["cy"])}" '
                f'android:gradientRadius="{num(a["r"])}"')
    items = "\n".join(f'{pad}        <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    return (f'{pad}<aapt:attr name="android:{attr}">\n{pad}    <gradient {head}>\n{items}\n'
            f'{pad}    </gradient>\n{pad}</aapt:attr>')


def paint(value, grads):
    """('colour', '#FFRRGGBB'), ('gradient', definition) or None for 'none' or a missing value."""
    if value is None or value == "none":
        return None
    m = re.fullmatch(r"url\(#([^)]+)\)", value)
    if m:
        return ("gradient", grads[m.group(1)])
    return ("colour", argb(value))


def path_xml(el, grads, indent):
    pad = " " * indent
    fill, stroke = paint(el.get("fill"), grads), paint(el.get("stroke"), grads)
    attrs = [f'android:pathData="{el.get("d")}"']
    inner = []
    if fill:
        if fill[0] == "colour":
            attrs.append(f'android:fillColor="{fill[1]}"')
        else:
            inner.append(gradient_xml(fill[1], indent + 4, "fillColor"))
    if stroke:
        if stroke[0] == "colour":
            attrs.append(f'android:strokeColor="{stroke[1]}"')
        else:
            inner.append(gradient_xml(stroke[1], indent + 4, "strokeColor"))
        attrs.append(f'android:strokeWidth="{num(el.get("stroke-width"))}"')
        if el.get("stroke-linecap"):
            attrs.append(f'android:strokeLineCap="{el.get("stroke-linecap")}"')
        if el.get("stroke-linejoin"):
            attrs.append(f'android:strokeLineJoin="{el.get("stroke-linejoin")}"')
    lines = "\n".join(f"{pad}    {a}" for a in attrs)
    if inner:
        return f"{pad}<path\n{lines}>\n" + "\n".join(inner) + f"\n{pad}</path>"
    return f"{pad}<path\n{lines} />"


def layer_paths(name, indent):
    root = ET.parse(SRC / name).getroot()
    if root.get("viewBox") != f"0 0 {VIEWPORT} {VIEWPORT}":
        raise ValueError(f"{name}: the viewBox must be 0 0 {VIEWPORT} {VIEWPORT}")
    grads = gradients(root)
    return "\n".join(path_xml(p, grads, indent) for p in root.iter(SVG + "path"))


def rounded_square(x, y, side, radius):
    r, s = radius, side
    return (f"M{x + r:g},{y:g}H{x + s - r:g}A{r:g},{r:g} 0 0,1 {x + s:g},{y + r:g}V{y + s - r:g}"
            f"A{r:g},{r:g} 0 0,1 {x + s - r:g},{y + s:g}H{x + r:g}A{r:g},{r:g} 0 0,1 {x:g},{y + s - r:g}"
            f"V{y + r:g}A{r:g},{r:g} 0 0,1 {x + r:g},{y:g}Z")


HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- {note}\n'
          '     Generated by android/tools/build-launcher-icon.py from android/tools/launcher-icon/{src}; do not edit. -->\n')
OPEN = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="{w}dp"\n    android:height="{w}dp"\n'
        '    android:viewportWidth="{v}"\n    android:viewportHeight="{v}">\n')


def write_vectors():
    layers = {
        "ic_launcher_background": ("background.svg", "The adaptive icon's background: the sky from night blue to golden hour brown."),
        "ic_launcher_foreground": ("foreground.svg", "The adaptive icon's foreground: the sinking sun, the horizon and its reflection, the crescent moon and a star."),
        "ic_launcher_monochrome": ("monochrome.svg", "The themed icon (Android 13 and later): the foreground's shapes in one colour; the system tints it."),
    }
    for stem, (src, note) in layers.items():
        text = (HEADER.format(note=note, src=src) + OPEN.format(w=VIEWPORT, v=VIEWPORT)
                + layer_paths(src, 4) + "\n</vector>\n")
        (RES / f"drawable/{stem}.xml").write_text(text, encoding="utf-8")
    # The carousel card: the window of 72 units a launcher shows, as a rounded tile, 32 dp.
    off = (VIEWPORT - WINDOW) // 2
    clip = rounded_square(off, off, WINDOW, WINDOW * TILE_RADIUS)
    body = (f'    <group android:translateX="{-off}" android:translateY="{-off}">\n'
            f'        <clip-path android:pathData="{clip}" />\n'
            + layer_paths("background.svg", 8) + "\n" + layer_paths("foreground.svg", 8) + "\n    </group>\n")
    card = (HEADER.format(note="The Twinlit card of the Labs carousel: the launcher icon as a rounded tile, 32 dp.",
                          src="background.svg and foreground.svg")
            + OPEN.format(w=32, v=WINDOW) + body + "</vector>\n")
    (RES / "drawable/labs_switcher_twinlit.xml").write_text(card, encoding="utf-8")


# ---------- PNGs, drawn by headless Chromium ----------

def page(size, files, radius_pct):
    """The WINDOW in the middle of the 108 layers, as launchers show it, at [size] px, clipped to [radius_pct]."""
    scale = size / WINDOW
    off = -(VIEWPORT - WINDOW) / 2 * scale
    imgs = "".join(
        f'<img src="file://{f}" style="position:absolute;left:{off}px;top:{off}px;'
        f'width:{VIEWPORT * scale}px;height:{VIEWPORT * scale}px">' for f in files)
    return ("<!doctype html><html><head><style>html,body{margin:0;padding:0;background:transparent}"
            "</style></head><body>"
            f'<div style="position:relative;width:{size}px;height:{size}px;overflow:hidden;'
            f'border-radius:{radius_pct}%">{imgs}</div></body></html>')


def render(out, size, radius_pct, work):
    """Writes the icon (background and foreground) at [size] px with transparent corners to [out]."""
    files = [str(SRC / "background.svg"), str(SRC / "foreground.svg")]
    html = pathlib.Path(work) / f"{out.stem}-{size}-{radius_pct}.html"
    html.write_text(page(size, files, radius_pct), encoding="utf-8")
    subprocess.run([CHROMIUM, "--headless", "--no-sandbox", "--disable-gpu", "--hide-scrollbars",
                    "--default-background-color=00000000", "--force-device-scale-factor=1",
                    f"--window-size={size},{size}", f"--screenshot={out}", f"file://{html}"],
                   check=True, capture_output=True)


def write_pngs():
    try:
        from PIL import Image
    except ImportError:
        sys.exit("Pillow is missing: python3 -m pip install pillow")
    if not pathlib.Path(CHROMIUM).exists():
        sys.exit(f"Chromium not found at {CHROMIUM}: set CHROMIUM to its path")
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    with tempfile.TemporaryDirectory() as work:
        for density, px in sizes.items():
            folder = RES / f"mipmap-{density}"
            folder.mkdir(parents=True, exist_ok=True)
            for name, radius in (("ic_launcher.png", TILE_RADIUS * 100), ("ic_launcher_round.png", 50)):
                target = folder / name
                render(target, px, radius, work)
                save_png(target, (px, px))
        STORE.mkdir(parents=True, exist_ok=True)
        icon = STORE / "icon.png"
        render(icon, 512, 0, work)    # the store applies its own mask
        save_png(icon, (512, 512), opaque=True)
        tile = pathlib.Path(work) / "tile.png"
        render(tile, 360, TILE_RADIUS * 100, work)
        write_feature_graphic(Image.open(tile).convert("RGBA"))


def save_png(path, size, opaque=False):
    from PIL import Image
    img = Image.open(path)
    if img.size != size:
        raise SystemExit(f"{path.name}: Chromium drew {img.size}, expected {size}")
    # Always 32-bit RGBA: Play asks for a 32-bit PNG store icon. An opaque image keeps alpha 255 everywhere.
    img = img.convert("RGB").convert("RGBA") if opaque else img.convert("RGBA")
    img.save(path, optimize=True)


def write_feature_graphic(tile):
    """The 1024 x 500 Play feature graphic: the icon tile, the name and two lines, on the icon's night blue."""
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
    ss = 4
    w, h = 1024 * ss, 500 * ss
    g = Image.new("RGBA", (w, h), NIGHT)
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((w * 0.0, h * 0.05, w * 0.42, h * 1.15), fill=(255, 179, 71, 34))   # the horizon's warm glow behind the tile
    gd.ellipse((w * 0.5, h * -0.4, w * 1.0, h * 0.5), fill=(80, 110, 190, 26))     # a cool night sky at the top right
    g.alpha_composite(glow.filter(ImageFilter.GaussianBlur(h * 0.12)))
    side = 360 * ss
    big = tile.resize((side, side), Image.LANCZOS)
    g.alpha_composite(big, (int(w * 0.075), (h - side) // 2))
    d = ImageDraw.Draw(g)
    title = ImageFont.truetype(str(FONTS / "inter_bold.ttf"), int(h * 0.20))
    sub = ImageFont.truetype(str(FONTS / "inter_regular.ttf"), int(h * 0.052))
    x = w * 0.45
    d.text((x, h * 0.30), "Twinlit", font=title, fill="#F2F5FC")
    d.text((x, h * 0.60), "Sun and moon alarms, times and widgets", font=sub, fill=SILVER)
    d.text((x, h * 0.69), "Computed on your device. No internet.", font=sub, fill=GOLD)
    g.resize((1024, 500), Image.LANCZOS).convert("RGB").save(STORE / "featureGraphic.png", optimize=True)


if __name__ == "__main__":
    write_vectors()
    write_pngs()
