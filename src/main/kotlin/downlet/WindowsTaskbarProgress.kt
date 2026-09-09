package downlet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import java.awt.Window

internal enum class TaskbarProgressState {
    Off,
    Normal,
    Indeterminate,
    Error,
}

internal data class TaskbarProgress(
    val state: TaskbarProgressState,
    val value: Int? = null,
)

internal fun taskbarProgress(uiState: DownloadUiState): TaskbarProgress =
    when (uiState) {
        is DownloadUiState.Downloading -> {
            when (val progress = uiState.progress) {
                DownloadProgress.Preparing -> {
                    TaskbarProgress(TaskbarProgressState.Indeterminate)
                }

                is DownloadProgress.Processing -> {
                    TaskbarProgress(TaskbarProgressState.Indeterminate)
                }

                is DownloadProgress.Transferring -> {
                    progress.percent?.let { TaskbarProgress(TaskbarProgressState.Normal, it) }
                        ?: TaskbarProgress(TaskbarProgressState.Indeterminate)
                }
            }
        }

        is DownloadUiState.Error -> {
            when (uiState.kind) {
                DownloadErrorKind.Resolution -> TaskbarProgress(TaskbarProgressState.Off)
                DownloadErrorKind.Download -> TaskbarProgress(TaskbarProgressState.Error, MAX_TRANSFER_PERCENT)
            }
        }

        else -> {
            TaskbarProgress(TaskbarProgressState.Off)
        }
    }

@Composable
internal fun ManageWindowsTaskbarProgress(
    window: Window,
    uiState: DownloadUiState,
) {
    val taskbar = remember { supportedTaskbar() }
    val progress = taskbarProgress(uiState)

    LaunchedEffect(window, taskbar, progress) {
        taskbar?.apply(window, progress)
    }
    DisposableEffect(window, taskbar) {
        onDispose { taskbar?.apply(window, TaskbarProgress(TaskbarProgressState.Off)) }
    }
}

private fun supportedTaskbar(): Taskbar? =
    runCatching {
        if (GraphicsEnvironment.isHeadless() || !Taskbar.isTaskbarSupported()) {
            null
        } else {
            Taskbar.getTaskbar().takeIf { taskbar ->
                taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW) &&
                    taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)
            }
        }
    }.getOrNull()

private fun Taskbar.apply(
    window: Window,
    progress: TaskbarProgress,
) {
    EventQueue.invokeLater {
        runCatching {
            setWindowProgressState(
                window,
                when (progress.state) {
                    TaskbarProgressState.Off -> Taskbar.State.OFF
                    TaskbarProgressState.Normal -> Taskbar.State.NORMAL
                    TaskbarProgressState.Indeterminate -> Taskbar.State.INDETERMINATE
                    TaskbarProgressState.Error -> Taskbar.State.ERROR
                },
            )
            progress.value?.let { setWindowProgressValue(window, it) }
        }
    }
}
