package downlet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import downlet.generated.resources.Res
import downlet.generated.resources.mona_sans_variable
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.theme.JewelTheme

internal const val DOWNLET_FONT_FEATURES = "\"ss03\", \"ss05\", \"ss06\""
internal const val DOWNLET_NUMERIC_FEATURES = "$DOWNLET_FONT_FEATURES, \"tnum\", \"ss08\""
internal const val DOWNLET_EXACT_TEXT_FEATURES = "$DOWNLET_FONT_FEATURES, \"liga\" 0, \"clig\" 0"

@Immutable
internal data class DownletTypeRole(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val weight: FontWeight,
    val width: Float,
    val opticalSize: TextUnit,
    val letterSpacing: TextUnit = TextUnit.Unspecified,
    val fontFeatureSettings: String = DOWNLET_FONT_FEATURES,
)

internal object DownletTypeRoles {
    val titleBar = DownletTypeRole(14.sp, 18.sp, FontWeight.SemiBold, 108f, 14.sp, (-0.1).sp)
    val sectionHeading = DownletTypeRole(16.sp, 20.sp, FontWeight.SemiBold, 102f, 16.sp, (-0.1).sp)
    val mediaTitle = DownletTypeRole(15.sp, 19.sp, FontWeight.SemiBold, 102f, 15.sp)
    val body = DownletTypeRole(14.sp, 18.sp, FontWeight.Normal, 100f, 14.sp)
    val legal = DownletTypeRole(14.sp, 20.sp, FontWeight.Normal, 100f, 14.sp)
    val formLabel = DownletTypeRole(13.sp, 16.sp, FontWeight.SemiBold, 100f, 13.sp)
    val metadata = DownletTypeRole(13.sp, 16.sp, FontWeight.Normal, 94f, 13.sp)
    val progressNumber =
        DownletTypeRole(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            weight = FontWeight.SemiBold,
            width = 100f,
            opticalSize = 14.sp,
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
    val metadata = DownletTypeRoles.metadata.toTextStyle(secondaryColor)
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
        fontFamily =
            FontFamily(
                Font(
                    resource = Res.font.mona_sans_variable,
                    weight = weight,
                    style = FontStyle.Normal,
                    variationSettings =
                        FontVariation.Settings(
                            weight,
                            FontStyle.Normal,
                            FontVariation.width(width),
                            FontVariation.opticalSizing(opticalSize),
                        ),
                ),
            ),
        fontWeight = weight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        fontFeatureSettings = fontFeatureSettings,
    )
