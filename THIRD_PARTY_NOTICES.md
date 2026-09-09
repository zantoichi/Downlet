# Third-party notices

Downlet's original source code is licensed under the Zero-Clause BSD license in `LICENSE`. Libraries and tools written by other authors keep their own licenses.

## Redistributed components

The portable EXE and MSI redistribute the following component families as part of the application or its trimmed runtime. License and notice files supplied by those components remain in their JARs or runtime legal directories.

| Component family | Included components | License | Source |
| --- | --- | --- | --- |
| JetBrains Runtime and OpenJDK | Trimmed JetBrains Runtime built from OpenJDK | GPL-2.0-only with Classpath Exception 2.0 and component-specific notices | https://github.com/JetBrains/JetBrainsRuntime |
| Kotlin | Standard library, coroutines, and serialization | Apache License 2.0 | https://github.com/JetBrains/kotlin, https://github.com/Kotlin/kotlinx.coroutines, https://github.com/Kotlin/kotlinx.serialization |
| Compose and AndroidX | Compose Desktop, Skiko, and transitive AndroidX libraries | Apache License 2.0 | https://github.com/JetBrains/compose-multiplatform, https://github.com/JetBrains/skiko, https://github.com/androidx/androidx |
| Jewel and IntelliJ Platform icons | Jewel UI libraries and bundled SVG resources | Apache License 2.0 | https://github.com/JetBrains/jewel, https://github.com/JetBrains/intellij-community |
| JNA | JNA and JNA Platform | Apache License 2.0 or LGPL-2.1-or-later | https://github.com/java-native-access/jna |
| QuickJS-NG | Bundled Windows x86-64 executable 0.16.2 | MIT | https://github.com/quickjs-ng/quickjs |
| mimalloc | Statically linked into the QuickJS-NG executable | MIT | https://github.com/microsoft/mimalloc |
| Unicode data | Data included by QuickJS-NG and runtime components | Unicode License V3 | https://www.unicode.org/license.txt |

Downlet's 0BSD license does not relicense these components.

## Downloaded external tools

Downlet does not redistribute yt-dlp, FFmpeg, or FFprobe. When required tools are missing, Downlet asks for consent, downloads pinned upstream artifacts, verifies their SHA-256 hashes, stores them under the user's local application-data directory, and runs them as separate programs. Damaged Downlet-managed copies use the same pinned versions and may be repaired automatically. Override and `PATH` tools are never replaced.

| Component | Pinned artifact | License | Source |
| --- | --- | --- | --- |
| yt-dlp | Official `yt-dlp.exe` 2026.08.19 | GPLv3+ for the official Windows executable | https://github.com/yt-dlp/yt-dlp |
| FFmpeg and FFprobe | Gyan Windows essentials build 9.0.1 | GPLv3 | https://www.gyan.dev/ffmpeg/builds/ |

## QuickJS-NG license

Copyright (c) 2017-2026 Fabrice Bellard
Copyright (c) 2017-2024 Charlie Gordon
Copyright (c) 2023-2026 Ben Noordhuis
Copyright (c) 2023-2026 Saúl Ibarra Corretgé

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## mimalloc license

The bundled QuickJS-NG release executable statically includes mimalloc.

Copyright (c) 2018-2025 Microsoft Corporation, Daan Leijen

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Unicode License V3

Copyright © 1991-2026 Unicode, Inc.

NOTICE TO USER: Carefully read the following legal agreement. BY DOWNLOADING, INSTALLING, COPYING OR OTHERWISE USING DATA FILES, AND/OR SOFTWARE, YOU UNEQUIVOCALLY ACCEPT, AND AGREE TO BE BOUND BY, ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. IF YOU DO NOT AGREE, DO NOT DOWNLOAD, INSTALL, COPY, DISTRIBUTE OR USE THE DATA FILES OR SOFTWARE.

Permission is hereby granted, free of charge, to any person obtaining a copy of data files and any associated documentation (the "Data Files") or software and any associated documentation (the "Software") to deal in the Data Files or Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, and/or sell copies of the Data Files or Software, and to permit persons to whom the Data Files or Software are furnished to do so, provided that either (a) this copyright and permission notice appear with all copies of the Data Files or Software, or (b) this copyright and permission notice appear in associated Documentation.

THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF THIRD PARTY RIGHTS.

IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES, OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THE DATA FILES OR SOFTWARE.

Except as contained in this notice, the name of a copyright holder shall not be used in advertising or otherwise to promote the sale, use or other dealings in these Data Files or Software without prior written authorization of the copyright holder.

## Media responsibility

Downlet grants no rights to videos, audio, or other media. Before each media download, the user must confirm that they own the media or have permission to download it and accept responsibility for complying with applicable law and YouTube's terms. The user chooses the URL, content, destination, and use of each download.

Downlet and its authors provide the software without warranty and disclaim liability to the fullest extent permitted by applicable law. User consent cannot waive liability that applicable law does not permit a party to waive, cannot bind YouTube or a rights holder, and cannot override a platform's terms.
