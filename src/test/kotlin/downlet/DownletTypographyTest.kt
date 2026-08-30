package downlet

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class DownletTypographyTest {
    @Test
    fun `type roles keep the approved Mona scale and axes`() {
        assertRole(DownletTypeRoles.titleBar, 14.sp, 18.sp, FontWeight.SemiBold, 108f, 14.sp, (-0.1).sp)
        assertRole(DownletTypeRoles.sectionHeading, 17.sp, 21.sp, FontWeight.SemiBold, 102f, 17.sp, (-0.1).sp)
        assertRole(DownletTypeRoles.mediaTitle, 16.sp, 20.sp, FontWeight.SemiBold, 102f, 16.sp)
        assertRole(DownletTypeRoles.body, 14.sp, 18.sp, FontWeight.Normal, 100f, 14.sp)
        assertRole(DownletTypeRoles.legal, 14.sp, 20.sp, FontWeight.Normal, 100f, 14.sp)
        assertRole(DownletTypeRoles.formLabel, 13.sp, 16.sp, FontWeight.SemiBold, 100f, 13.sp)
        assertRole(DownletTypeRoles.metadata, 13.sp, 16.sp, FontWeight.Normal, 94f, 13.sp)
        assertRole(
            DownletTypeRoles.progressNumber,
            14.sp,
            18.sp,
            FontWeight.SemiBold,
            100f,
            14.sp,
            features = DOWNLET_NUMERIC_FEATURES,
        )
    }

    @Test
    fun `OpenType features distinguish normal numeric and exact text`() {
        assertEquals("\"ss03\", \"ss05\", \"ss06\"", DOWNLET_FONT_FEATURES)
        assertEquals("$DOWNLET_FONT_FEATURES, \"tnum\", \"ss08\"", DOWNLET_NUMERIC_FEATURES)
        assertEquals("$DOWNLET_FONT_FEATURES, \"liga\" 0, \"clig\" 0", DOWNLET_EXACT_TEXT_FEATURES)
    }
}

private fun assertRole(
    role: DownletTypeRole,
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight,
    width: Float,
    opticalSize: TextUnit,
    tracking: TextUnit = TextUnit.Unspecified,
    features: String = DOWNLET_FONT_FEATURES,
) {
    assertEquals(size, role.fontSize)
    assertEquals(lineHeight, role.lineHeight)
    assertEquals(weight, role.weight)
    assertEquals(width, role.width)
    assertEquals(opticalSize, role.opticalSize)
    assertEquals(tracking, role.letterSpacing)
    assertEquals(features, role.fontFeatureSettings)
}
