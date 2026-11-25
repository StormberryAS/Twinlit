# b1: Sunset, moon above

**Concept.** The sun sinks into the horizon at golden hour, with its reflection on the water below, while the crescent moon and one star already stand in the night sky above. One picture holds the whole day's turn from the Sun tab to the Moon tab, which is the moment Twinlit's alarms are built around.

**Colours.** Background: a vertical linear gradient, night blue #080C18 at the top, #0D142E at 34 %, a golden hour brown #3A2410 at 60 % (the horizon glow, a lighter step of #301D0C) and #2A1A0C at the bottom. Sun: radial gradient #FFF7A1 to #FFD700 to #FFB347; rays #FFD700. Horizon line and reflections: warm orange #FFB347. Moon and star: silver #C8D8F0. Monochrome: all foreground shapes in #FFFFFF.

**Why it reads at small size.** The half sun on a thick horizon line is a strong, well known sunset sign, and the crescent sits alone in the darkest part of the sky, so at 48 px and at 24 px the eye still finds a warm half disc below and a pale crescent above. All strokes are 2.4 to 3 units wide with round caps (at least 1 px at 48 px), the art stays inside the 66 unit safe circle (checked by pixel test), and the monochrome version keeps the same silhouette.

**Layers.** background.svg, foreground.svg, monochrome.svg on a 108 x 108 viewBox. Only paths, fills, round-cap strokes and user-space linear and radial gradients, so each converts to an Android vector drawable. Source: designer B's script (outside this repository); since 2026-11-22 these three SVG files are the source and ../build-launcher-icon.py generates every form of the icon from them.
