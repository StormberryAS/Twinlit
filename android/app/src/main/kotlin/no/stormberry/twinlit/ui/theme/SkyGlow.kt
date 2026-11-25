package no.stormberry.twinlit.ui.theme

/**
 * One soft wash of the sky glow: its centre as a fraction of the box width and height, and its
 * radius as a fraction of the box's smaller side.
 */
data class Wash(val cx: Float, val cy: Float, val radius: Float)

/**
 * Where the three washes of `drawSkyGlow` sit, in one place so the drawing code and the contrast
 * test read the same numbers (R4, CHANGES-R4 section 7 item 4). The top wash is centred on the top
 * edge, the left one near the top left, the bottom one near the bottom right.
 */
object SkyGlowGeometry {
    val top = Wash(0.5f, 0f, 1.05f)
    val left = Wash(0.18f, 0.04f, 0.55f)
    val bottom = Wash(0.95f, 0.85f, 0.9f)
}
