package downlet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import downlet.generated.resources.Res
import downlet.generated.resources.app_icon
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
import org.jetbrains.jewel.ui.component.ToggleableIconActionButton
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.styling.TitleBarStyle

private const val PRODUCT_WINDOW_TITLE = "Downlet"

fun main() =
    application {
        val scope = rememberCoroutineScope()
        val stateHolder = remember(scope) { DownloadStateHolder(scope, runtime = YtDlpDownloadRuntime()) }
        val systemTheme = if (isSystemInDarkTheme()) DownletTheme.Dark else DownletTheme.Light
        var theme by remember { mutableStateOf(systemTheme) }
        val animationsEnabled = remember { windowsAnimationsEnabled() }

        ProductWindow(
            stateHolder = stateHolder,
            theme = theme,
            onThemeChange = { theme = it },
            onCloseRequest = ::exitApplication,
            animationsEnabled = animationsEnabled,
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod")
internal fun ProductWindow(
    stateHolder: DownloadStateHolder,
    theme: DownletTheme,
    onThemeChange: (DownletTheme) -> Unit,
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
        val typography = downletTypography()
        CompositionLocalProvider(
            LocalDownletTypography provides typography,
            LocalTextStyle provides typography.body,
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
                    val darkMode = theme == DownletTheme.Dark
                    val themeAction = if (darkMode) "Use light theme" else "Use dark theme"
                    Row(
                        modifier = Modifier.align(Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(appIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(PRODUCT_WINDOW_TITLE, style = typography.titleBar)
                    }
                    ToggleableIconActionButton(
                        key = AllIconsKeys.MeetNewUi.DarkTheme,
                        contentDescription = themeAction,
                        value = darkMode,
                        onValueChange = { enabled ->
                            onThemeChange(if (enabled) DownletTheme.Dark else DownletTheme.Light)
                        },
                        modifier = Modifier.size(28.dp),
                        iconModifier = Modifier.size(16.dp),
                        tooltipModifier = Modifier.align(Alignment.End),
                    ) {
                        Text(themeAction)
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
