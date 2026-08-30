package downlet

internal data class LegalSectionCopy(
    val title: String,
    val body: String,
)

internal data class DownloadFailureCopy(
    val title: String,
    val guidance: String,
)

internal object ProductCopy {
    const val INVALID_LINK_MESSAGE = "Enter a valid YouTube link."
    const val TOOL_SETUP_FAILURE_MESSAGE =
        "Couldn't install the required tools. Check your connection and try again."
    const val TOOL_SETUP_CONSENT_TEXT =
        "I choose to download these tools and accept the tool terms."
    const val DOWNLOAD_AUTHORIZATION_TEXT = "I own this media or have permission to download it."
    const val OPEN_FOLDER_ACKNOWLEDGEMENT = "Folder opening is unavailable in this design preview."
    const val OPEN_FOLDER_FAILURE_MESSAGE = "Couldn't open the download folder."

    fun downloadFailure(
        kind: DownloadErrorKind,
        reason: DownloadFailureReason,
    ): DownloadFailureCopy =
        when (reason) {
            DownloadFailureReason.Availability -> {
                DownloadFailureCopy(
                    "This media isn’t available to Downlet.",
                    "It may be private, restricted, removed, or require sign-in. Check the link or use another " +
                        "accessible video.",
                )
            }

            DownloadFailureReason.Network -> {
                DownloadFailureCopy(
                    "The connection was interrupted.",
                    "Check your connection, wait a moment, and try again.",
                )
            }

            DownloadFailureReason.Storage -> {
                DownloadFailureCopy(
                    "Downlet couldn’t save this file.",
                    "Choose a writable folder with enough free space, then try again.",
                )
            }

            DownloadFailureReason.Processing -> {
                DownloadFailureCopy(
                    "Downlet couldn’t finish this file.",
                    "Merging or conversion failed. Try the download again.",
                )
            }

            DownloadFailureReason.Tool -> {
                DownloadFailureCopy(
                    "A required download tool couldn’t run.",
                    "Restart Downlet and try again.",
                )
            }

            DownloadFailureReason.Unknown -> {
                if (kind == DownloadErrorKind.Resolution) {
                    DownloadFailureCopy(
                        "Couldn’t read this YouTube link.",
                        "Check that the link is available and try again.",
                    )
                } else {
                    DownloadFailureCopy(
                        "Couldn’t download this media.",
                        "Try again. If it keeps failing, check the link and save location.",
                    )
                }
            }
        }

    fun toolSetupDescription(
        toolNames: String,
        estimatedDownloadMegabytes: Int,
    ): String =
        "Downlet needs $toolNames to check formats and create your file. " +
            "They are not included with Downlet. Downloading them does not download this media. " +
            "Download size is about $estimatedDownloadMegabytes MB."

    val legalSections =
        listOf(
            LegalSectionCopy(
                title = "Before setup",
                body =
                    "After you enter a link, Downlet asks YouTube for its title, channel, and thumbnail so you can " +
                        "identify the media before deciding whether to install tools. It does not download media.",
            ),
            LegalSectionCopy(
                title = "Third-party tools",
                body =
                    "Downlet includes QuickJS-NG for YouTube JavaScript support. If you choose to continue, Downlet " +
                        "downloads only missing pinned copies of yt-dlp and FFmpeg, verifies each SHA-256 hash, " +
                        "stores " +
                        "them in your local application-data folder, and runs the tools as separate programs.",
            ),
            LegalSectionCopy(
                title = "Licenses",
                body =
                    "QuickJS-NG is MIT, the official yt-dlp Windows executable is GPLv3+, and the FFmpeg " +
                        "Windows build " +
                        "is GPLv3. Those licenses apply to those tools. Downlet's original code remains 0BSD.",
            ),
            LegalSectionCopy(
                title = "Your responsibility",
                body =
                    "You choose whether to install the tools, which URL to use, what to download, where to save it, " +
                        "and how to use it. Before each media download, you must confirm that you own the media " +
                        "or have " +
                        "permission and that your use follows applicable law and YouTube's terms.",
            ),
            LegalSectionCopy(
                title = "Limits",
                body =
                    "Downlet grants no rights to media and comes without warranty. Its authors disclaim liability to " +
                        "the fullest extent permitted by law. Your consent cannot bind YouTube or a rights holder, " +
                        "override law or platform terms, or waive liability that the law does not allow a party " +
                        "to waive.",
            ),
        )
}
