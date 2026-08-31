package downlet

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class DownletTypographyTest {
    @Test
    fun `all roles use the native Windows sans serif family`() {
        assertEquals(FontFamily.SansSerif, DOWNLET_FONT_FAMILY)
    }

    @Test
    fun `type roles keep the approved Windows scale`() {
        assertRole(DownletTypeRoles.titleBar, 15.sp, 19.sp, FontWeight.SemiBold, (-0.1).sp)
        assertRole(DownletTypeRoles.sectionHeading, 17.sp, 22.sp, FontWeight.SemiBold, (-0.1).sp)
        assertRole(DownletTypeRoles.mediaTitle, 16.sp, 21.sp, FontWeight.SemiBold)
        assertRole(DownletTypeRoles.body, 16.sp, 22.sp, FontWeight.Normal)
        assertRole(DownletTypeRoles.legal, 15.sp, 21.sp, FontWeight.Normal)
        assertRole(DownletTypeRoles.formLabel, 14.sp, 18.sp, FontWeight.SemiBold)
        assertRole(DownletTypeRoles.metadata, 14.sp, 18.sp, FontWeight.Normal)
        assertRole(
            DownletTypeRoles.progressNumber,
            15.sp,
            20.sp,
            FontWeight.SemiBold,
            features = DOWNLET_NUMERIC_FEATURES,
        )
    }

    @Test
    fun `OpenType features distinguish normal numeric and exact text`() {
        assertEquals("\"tnum\"", DOWNLET_NUMERIC_FEATURES)
        assertEquals("\"liga\" 0, \"clig\" 0", DOWNLET_EXACT_TEXT_FEATURES)
    }
}

private fun assertRole(
    role: DownletTypeRole,
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight,
    tracking: TextUnit = TextUnit.Unspecified,
    features: String? = null,
) {
    assertEquals(size, role.fontSize)
    assertEquals(lineHeight, role.lineHeight)
    assertEquals(weight, role.weight)
    assertEquals(tracking, role.letterSpacing)
    assertEquals(features, role.fontFeatureSettings)
}
