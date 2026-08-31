package downlet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme

internal const val DOWNLET_NUMERIC_FEATURES = "\"tnum\""
internal const val DOWNLET_EXACT_TEXT_FEATURES = "\"liga\" 0, \"clig\" 0"
internal val DOWNLET_FONT_FAMILY = FontFamily.SansSerif

@Immutable
internal data class DownletTypeRole(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val weight: FontWeight,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
    val fontFeatureSettings: String? = null,
)

internal object DownletTypeRoles {
    val titleBar = DownletTypeRole(15.sp, 19.sp, FontWeight.SemiBold, (-0.1).sp)
    val sectionHeading = DownletTypeRole(17.sp, 22.sp, FontWeight.SemiBold, (-0.1).sp)
    val mediaTitle = DownletTypeRole(16.sp, 21.sp, FontWeight.SemiBold)
    val body = DownletTypeRole(16.sp, 22.sp, FontWeight.Normal)
    val legal = DownletTypeRole(15.sp, 21.sp, FontWeight.Normal)
    val formLabel = DownletTypeRole(14.sp, 18.sp, FontWeight.SemiBold)
    val metadata = DownletTypeRole(14.sp, 18.sp, FontWeight.Normal)
    val progressNumber =
        DownletTypeRole(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            weight = FontWeight.SemiBold,
            fontFeatureSettings = DOWNLET_NUMERIC_FEATURES,
        )
}

@Immutable
internal data class DownletTypography(
    val titleBar: TextStyle,
    val sectionHeading: TextStyle,
    val mediaTitle: TextStyle,
    val body: TextStyle,
    val legal: TextStyle,
    val formLabel: TextStyle,
    val metadata: TextStyle,
    val numericMetadata: TextStyle,
    val progressNumber: TextStyle,
    val exactBody: TextStyle,
    val exactMetadata: TextStyle,
)

internal val LocalDownletTypography =
    staticCompositionLocalOf<DownletTypography> { error("Downlet typography is not installed") }

@Composable
internal fun downletTypography(): DownletTypography {
    val secondaryColor = JewelTheme.contentColor.copy(alpha = if (JewelTheme.isDark) 0.78f else 0.70f)
    val body = DownletTypeRoles.body.toTextStyle()
    val metadata = DownletTypeRoles.metadata.toTextStyle(color = secondaryColor)
    return DownletTypography(
        titleBar = DownletTypeRoles.titleBar.toTextStyle(),
        sectionHeading = DownletTypeRoles.sectionHeading.toTextStyle(),
        mediaTitle = DownletTypeRoles.mediaTitle.toTextStyle(),
        body = body,
        legal = DownletTypeRoles.legal.toTextStyle(),
        formLabel = DownletTypeRoles.formLabel.toTextStyle(),
        metadata = metadata,
        numericMetadata = metadata.copy(fontFeatureSettings = DOWNLET_NUMERIC_FEATURES),
        progressNumber = DownletTypeRoles.progressNumber.toTextStyle(),
        exactBody = body.copy(fontFeatureSettings = DOWNLET_EXACT_TEXT_FEATURES),
        exactMetadata = metadata.copy(fontFeatureSettings = DOWNLET_EXACT_TEXT_FEATURES),
    )
}

@Composable
private fun DownletTypeRole.toTextStyle(color: Color = Color.Unspecified): TextStyle =
    JewelTheme.defaultTextStyle.copy(
        color = color,
        fontFamily = DOWNLET_FONT_FAMILY,
        fontWeight = weight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        fontFeatureSettings = fontFeatureSettings,
    )
