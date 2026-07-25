package dev.goutham.wallbreaker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.goutham.wallbreaker.R

/**
 * Display face: Bricolage Grotesque (OFL, bundled variable font) — warm and
 * slightly eccentric, and *bricolage* literally means DIY tinkering, the right
 * voice for a one-person tool named after bricks. Used only for display/headline
 * roles and titleLarge; everything else stays on the platform default (Roboto).
 */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Normal),
    Font(R.font.bricolage_grotesque, FontWeight.Medium),
    Font(R.font.bricolage_grotesque, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque, FontWeight.Bold),
)

val WallbreakerTypography: Typography = Typography().run {
    fun TextStyle.display() = copy(fontFamily = Bricolage, letterSpacing = (-0.25).sp)
    copy(
        displayLarge = displayLarge.display(),
        displayMedium = displayMedium.display(),
        displaySmall = displaySmall.display(),
        headlineLarge = headlineLarge.display(),
        headlineMedium = headlineMedium.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
    )
}
