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
    const val TOOL_REPAIR_FAILURE_MESSAGE =
        "Couldn't repair the required tools. Check your connection and try again."
    const val TOOL_SETUP_CONSENT_TEXT =
        "I choose to download these tools and accept the tool terms."
    const val DOWNLOAD_AUTHORIZATION_TEXT = "I own this media or have permission to download it."
    const val SHOW_IN_FOLDER_ACKNOWLEDGEMENT = "File reveal is unavailable in this design preview."
    const val SHOW_IN_FOLDER_FAILURE_MESSAGE = "Couldn't show the completed file in its folder."

    val audioQualityAnswers =
        linkedMapOf(
            "Which format preserves the source?" to
                "Choose Original to keep YouTube's audio stream without re-encoding or changing its volume. " +
                "Original means the stream available from YouTube, not the uploader's original recording.",
            "Does FLAC improve YouTube audio?" to
                "No. FLAC preserves the audio it receives without further loss, but cannot restore detail " +
                "already lost in YouTube's compression. A larger file or higher sample rate does not recover it. " +
                "Downlet currently offers Original and MP3.",
            "Does a higher MP3 bitrate mean better quality?" to
                "MP3 re-encodes the source for compatibility and may add quality loss. A higher output bitrate " +
                "can reduce that extra loss, but cannot improve the source. Bitrates across different codecs " +
                "are not a direct quality comparison.",
            "Why can a download sound louder or better?" to
                "YouTube may turn down audio during playback. A local player may use different volume settings " +
                "or sound effects. Louder audio can seem fuller even when no detail was added. " +
                "Compare at matched listening volume with player effects disabled.",
            "Can volume increase without sacrificing quality?" to
                "Only while there is room below the clipping limit. Beyond that, boosting requires changing " +
                "the dynamics or risks distortion. Downlet does not boost, compress, or normalize exports.",
            "What about ReplayGain?" to
                "ReplayGain tags store a playback volume suggestion without changing the encoded audio. " +
                "Compatible players can use it to even out loudness; other players ignore it. It may turn " +
                "loud tracks down. Positive gain still needs peak-aware playback to avoid clipping. " +
                "Downlet does not currently add ReplayGain tags.",
        )

    private val botChallengeFailure =
        DownloadFailureCopy(
            "YouTube wants to verify this session.",
            "This can happen with public videos; it doesn’t mean the video is age-restricted. " +
                "Open the video in your browser and complete any YouTube check, " +
                "then choose that browser below. " +
                "Downlet temporarily uses its cookies without storing them. You can also wait and retry.",
        )

    fun downloadFailure(
        kind: DownloadErrorKind,
        reason: DownloadFailureReason,
    ): DownloadFailureCopy =
        when (reason) {
            DownloadFailureReason.BotChallenge -> {
                botChallengeFailure
            }

            DownloadFailureReason.Authentication -> {
                DownloadFailureCopy(
                    "Sign in required.",
                    "Choose a browser where you can watch this video while signed in. yt-dlp temporarily reads " +
                        "that browser profile’s cookies; Downlet doesn’t store them.",
                )
            }

            DownloadFailureReason.Availability -> {
                DownloadFailureCopy(
                    "This media isn’t available to Downlet.",
                    "It may be private, restricted, removed, or unavailable in your region. " +
                        "Check the link or use another accessible video.",
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
                    "Check any configured yt-dlp or FFmpeg tools, then try again.",
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

    fun toolRepairDescription(toolNames: String): String =
        "Downlet found a damaged managed copy of $toolNames. It is replacing it with the same pinned, " +
            "SHA-256-verified version. This does not download the media."

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
                        "downloads only missing or damaged pinned copies of yt-dlp and FFmpeg, verifies each " +
                        "SHA-256 hash, " +
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
