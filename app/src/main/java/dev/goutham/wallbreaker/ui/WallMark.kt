package dev.goutham.wallbreaker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

// The "Throughline" mark palette (matches the adaptive launcher icon).
private val Paper = Color(0xFFF8F1E6)
private val Ink = Color(0xFF3A1E15)
private val Amber = Color(0xFFF2A33C)
private val Brick = Color(0xFFE8503A)

/**
 * Wallbreaker's mark — the "Throughline" logo: article lines meet the split
 * coral wall and one bursts through the gap into an amber arrow. Drawn in the
 * 512-unit space of the launcher icon, on its own paper tile so the dark lines
 * read on any surface. [progress] (0→1) animates the amber line+arrow shooting
 * through the wall; it defaults to 1 (fully drawn) for static use.
 */
@Composable
fun WallMark(modifier: Modifier = Modifier, progress: Float = 1f) {
    Canvas(modifier) {
        val k = size.minDimension / 512f
        translate((size.width - 512f * k) / 2f, (size.height - 512f * k) / 2f) {
            scale(k, k, pivot = Offset.Zero) { drawThroughline(progress.coerceIn(0f, 1f)) }
        }
    }
}

private fun DrawScope.drawThroughline(progress: Float) {
    // Paper tile.
    drawRoundRect(Paper, size = Size(512f, 512f), cornerRadius = CornerRadius(115f, 115f))

    // Dark article lines.
    drawRoundRect(Ink, Offset(118f, 181f), Size(134f, 30f), CornerRadius(15f, 15f))
    drawRoundRect(Ink, Offset(118f, 301f), Size(134f, 30f), CornerRadius(15f, 15f))
    drawRoundRect(Ink, Offset(118f, 241f), Size(146f, 30f), CornerRadius(15f, 15f))  // middle line

    // The breakthrough line + arrow, revealed left→right by [progress].
    clipRect(left = 0f, top = 0f, right = 118f + progress * (420f - 118f), bottom = 512f) {
        drawRect(Amber, Offset(252f, 241f), Size(120f, 30f))
        drawPath(
            Path().apply {
                moveTo(366f, 214f); lineTo(420f, 256f); lineTo(366f, 298f); close()
            },
            Amber,
        )
    }

    // The split coral wall halves.
    drawRoundRect(Brick, Offset(270f, 112f), Size(72f, 120f), CornerRadius(18f, 18f))
    drawRoundRect(Brick, Offset(258f, 280f), Size(72f, 124f), CornerRadius(18f, 18f))

    // Knocked-loose brick sparks.
    rotate(20f, pivot = Offset(361f, 203f)) {
        drawRoundRect(Brick, Offset(354f, 196f), Size(14f, 14f), CornerRadius(3f, 3f))
    }
    rotate(-16f, pivot = Offset(353f, 315f)) {
        drawRoundRect(Brick, Offset(348f, 310f), Size(10f, 10f), CornerRadius(2f, 2f))
    }
}

/**
 * The route/unlock glyph — a compact amber arrow echoing the mark's breakthrough
 * arrow. Used with discipline as the "unlocked via Freedium" indicator.
 */
@Composable
fun RouteArrow(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            tint,
            topLeft = Offset(0.06f * w, 0.42f * h),
            size = Size(0.52f * w, 0.16f * h),
            cornerRadius = CornerRadius(0.04f * w, 0.04f * w),
        )
        drawPath(
            Path().apply {
                moveTo(0.50f * w, 0.20f * h)
                lineTo(0.94f * w, 0.50f * h)
                lineTo(0.50f * w, 0.80f * h)
                close()
            },
            tint,
        )
    }
}
