package downlet

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import downlet.generated.resources.Res
import downlet.generated.resources.app_icon
import downlet.generated.resources.mona_sans_regular
import downlet.generated.resources.mona_sans_semibold
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.light
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

private const val PRODUCT_WINDOW_TITLE = "Downlet"

fun main() =
    application {
        val scope = rememberCoroutineScope()
        val stateHolder = remember(scope) { DownloadStateHolder(scope, runtime = YtDlpDownloadRuntime()) }
        val detectedDarkTheme = isSystemInDarkTheme()
        val startupTheme = remember { if (detectedDarkTheme) DownletTheme.Dark else DownletTheme.Light }
        val animationsEnabled = remember { windowsAnimationsEnabled() }

        ProductWindow(
            stateHolder = stateHolder,
            theme = startupTheme,
            onCloseRequest = ::exitApplication,
            animationsEnabled = animationsEnabled,
        )
    }

@Composable
@Suppress("LongMethod")
internal fun ProductWindow(
    stateHolder: DownloadStateHolder,
    theme: DownletTheme,
    onCloseRequest: () -> Unit,
    initialPosition: WindowPosition = WindowPosition.PlatformDefault,
    animationsEnabled: Boolean = true,
) {
    val initialTier = WindowPresentationTier.Compact
    val windowState =
        rememberWindowState(
            position = initialPosition,
            width = initialTier.preferredSize.width,
            height = initialTier.preferredSize.height,
        )

    IntUiTheme(
        theme =
            when (theme) {
                DownletTheme.Light -> JewelTheme.lightThemeDefinition()
                DownletTheme.Dark -> JewelTheme.darkThemeDefinition()
            },
        styling =
            when (theme) {
                DownletTheme.Light -> {
                    ComponentStyling.default().decoratedWindow(
                        DecoratedWindowStyle.light(),
                        TitleBarStyle.lightWithLightHeader(),
                    )
                }

                DownletTheme.Dark -> {
                    ComponentStyling.default().decoratedWindow(
                        DecoratedWindowStyle.dark(),
                        TitleBarStyle.dark(),
                    )
                }
            },
    ) {
        val downletFontFamily =
            FontFamily(
                Font(Res.font.mona_sans_regular, FontWeight.Normal),
                Font(Res.font.mona_sans_semibold, FontWeight.SemiBold),
            )
        CompositionLocalProvider(
            LocalTextStyle provides
                JewelTheme.defaultTextStyle.copy(
                    fontFamily = downletFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                ),
        ) {
            val appIcon = painterResource(Res.drawable.app_icon)
            DecoratedWindow(
                onCloseRequest = onCloseRequest,
                state = windowState,
                title = PRODUCT_WINDOW_TITLE,
                icon = appIcon,
                resizable = false,
            ) {
                ManageProductWindowSizing(
                    window = window,
                    windowState = windowState,
                    tier = stateHolder.state.windowPresentationTier,
                    animationsEnabled = animationsEnabled,
                )

                TitleBar {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(appIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(PRODUCT_WINDOW_TITLE, fontWeight = FontWeight.SemiBold)
                    }
                }

                ProductSurface(
                    stateHolder = stateHolder,
                    animationsEnabled = animationsEnabled,
                )
            }
        }
    }
}
