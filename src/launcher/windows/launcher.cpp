#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <bcrypt.h>
#include <dwmapi.h>
#include <setupapi.h>
#include <shellapi.h>
#include <shlobj.h>

#include <array>
#include <atomic>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_set>
#include <vector>

#include "launcher_config.h"

namespace fs = std::filesystem;

namespace {
constexpr int kCabinetResource = 101;
constexpr int kManifestResource = 102;
constexpr wchar_t kProductName[] = L"Downlet";
constexpr wchar_t kPayloadLeaseSuffix[] = L".lease-v1";
constexpr size_t kSha256HexLength = 64;
constexpr UINT kHideSplash = WM_APP + 1;
constexpr UINT kApplicationExited = WM_APP + 2;
constexpr UINT kStartupStatus = WM_APP + 3;
constexpr ULONGLONG kSplashDelay = 250;
HWND splashWindow = nullptr;
HANDLE startupReady = nullptr;
HFONT titleFont = nullptr;
HFONT statusFont = nullptr;
HBRUSH splashBackground = nullptr;
HICON splashIcon = nullptr;
COLORREF titleColor = 0;
COLORREF statusColor = 0;
ULONGLONG launcherStartedAt = 0;

enum class StartupStatus { Starting, FirstLaunch, Repairing };
enum class SplashAction { None, Show, Hide };

struct StartupPanelState {
    bool visible = false;
    bool dismissed = false;
    StartupStatus status = StartupStatus::Starting;

    SplashAction Advance(ULONGLONG elapsed, bool ready) {
        if (dismissed) return SplashAction::None;
        if (ready) {
            dismissed = true;
            visible = false;
            return SplashAction::Hide;
        }
        if (!visible && elapsed >= kSplashDelay) {
            visible = true;
            return SplashAction::Show;
        }
        return SplashAction::None;
    }

    void Dismiss() { dismissed = true; visible = false; }
    void Update(StartupStatus next) { if (!dismissed) status = next; }
};
StartupPanelState panelState;

const wchar_t* StartupText(StartupStatus status) {
    switch (status) {
        case StartupStatus::FirstLaunch: return L"Preparing first launch\u2026";
        case StartupStatus::Repairing: return L"Preparing Downlet\u2026";
        default: return L"Starting\u2026";
    }
}

void DismissSplash(HWND window) {
    panelState.Dismiss();
    KillTimer(window, 1);
    ShowWindow(window, SW_HIDE);
}

void UpdateSplashTheme(HWND window) {
    HIGHCONTRASTW contrast{sizeof(contrast)};
    const bool highContrast = SystemParametersInfoW(SPI_GETHIGHCONTRAST, sizeof(contrast), &contrast, 0) &&
        (contrast.dwFlags & HCF_HIGHCONTRASTON) != 0;
    DWORD light = 1;
    DWORD size = sizeof(light);
    if (RegGetValueW(HKEY_CURRENT_USER,
        L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", L"AppsUseLightTheme",
        RRF_RT_REG_DWORD, nullptr, &light, &size) != ERROR_SUCCESS) light = 1;
    const BOOL dark = !highContrast && light == 0;
    // Jewel's neutral window, foreground and secondary-text palette.
    COLORREF background = dark ? RGB(43, 45, 48) : RGB(247, 248, 250);
    titleColor = dark ? RGB(223, 225, 229) : RGB(30, 31, 34);
    statusColor = dark ? RGB(157, 160, 168) : RGB(108, 112, 126);
    if (highContrast) {
        background = GetSysColor(COLOR_WINDOW);
        titleColor = statusColor = GetSysColor(COLOR_WINDOWTEXT);
    }
    if (splashBackground) DeleteObject(splashBackground);
    splashBackground = CreateSolidBrush(background);
    DwmSetWindowAttribute(window, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark, sizeof(dark));
    const DWM_WINDOW_CORNER_PREFERENCE corners = highContrast ? DWMWCP_DONOTROUND : DWMWCP_ROUND;
    DwmSetWindowAttribute(window, DWMWA_WINDOW_CORNER_PREFERENCE, &corners, sizeof(corners));
    InvalidateRect(window, nullptr, TRUE);
    for (int id : {1, 2, 3}) {
        if (HWND child = GetDlgItem(window, id)) InvalidateRect(child, nullptr, TRUE);
    }
}

void LayoutSplash(HWND window) {
    const int dpi = static_cast<int>(GetDpiForWindow(window));
    auto scale = [dpi](int value) { return MulDiv(value, dpi, 96); };
    RECT area{};
    SystemParametersInfoW(SPI_GETWORKAREA, 0, &area, 0);
    SetWindowPos(window, nullptr,
        area.left + (area.right - area.left - scale(320)) / 2,
        area.top + (area.bottom - area.top - scale(112)) / 2,
        scale(320), scale(112), SWP_NOZORDER | SWP_NOACTIVATE);
    if (titleFont) DeleteObject(titleFont);
    if (statusFont) DeleteObject(statusFont);
    titleFont = CreateFontW(-scale(16), 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    statusFont = CreateFontW(-scale(14), 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
    if (splashIcon) DestroyIcon(splashIcon);
    splashIcon = static_cast<HICON>(LoadImageW(GetModuleHandleW(nullptr), MAKEINTRESOURCEW(1),
        IMAGE_ICON, scale(32), scale(32), LR_DEFAULTCOLOR));
    SendDlgItemMessageW(window, 1, STM_SETICON, reinterpret_cast<WPARAM>(splashIcon), 0);
    SendDlgItemMessageW(window, 2, WM_SETFONT, reinterpret_cast<WPARAM>(titleFont), TRUE);
    SendDlgItemMessageW(window, 3, WM_SETFONT, reinterpret_cast<WPARAM>(statusFont), TRUE);
    MoveWindow(GetDlgItem(window, 1), scale(24), scale(40), scale(32), scale(32), TRUE);
    MoveWindow(GetDlgItem(window, 2), scale(72), scale(32), scale(224), scale(24), TRUE);
    MoveWindow(GetDlgItem(window, 3), scale(72), scale(56), scale(224), scale(24), TRUE);
}

LRESULT CALLBACK SplashProcedure(HWND window, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
        case WM_ERASEBKGND: {
            RECT area{};
            GetClientRect(window, &area);
            FillRect(reinterpret_cast<HDC>(wParam), &area, splashBackground ? splashBackground : GetSysColorBrush(COLOR_WINDOW));
            return 1;
        }
        case WM_CTLCOLORSTATIC:
            SetBkMode(reinterpret_cast<HDC>(wParam), TRANSPARENT);
            SetTextColor(reinterpret_cast<HDC>(wParam), GetDlgCtrlID(reinterpret_cast<HWND>(lParam)) == 3 ? statusColor : titleColor);
            return reinterpret_cast<LRESULT>(splashBackground);
        case WM_SETTINGCHANGE:
        case WM_THEMECHANGED:
        case WM_SYSCOLORCHANGE:
            UpdateSplashTheme(window);
            return 0;
        case WM_DPICHANGED:
            LayoutSplash(window);
            return 0;
        case WM_DESTROY:
            DismissSplash(window);
            if (titleFont) DeleteObject(titleFont);
            if (statusFont) DeleteObject(statusFont);
            if (splashBackground) DeleteObject(splashBackground);
            if (splashIcon) DestroyIcon(splashIcon);
            titleFont = statusFont = nullptr;
            splashBackground = nullptr;
            splashIcon = nullptr;
            return 0;
        case WM_TIMER: {
            const auto action = panelState.Advance(GetTickCount64() - launcherStartedAt,
                WaitForSingleObject(startupReady, 0) == WAIT_OBJECT_0);
            if (action == SplashAction::Hide) DismissSplash(window);
            if (action == SplashAction::Show) {
                ShowWindow(window, SW_SHOWNOACTIVATE);
                UpdateWindow(window);
            }
            return 0;
        }
        case kStartupStatus:
            panelState.Update(static_cast<StartupStatus>(wParam));
            SetDlgItemTextW(window, 3, StartupText(panelState.status));
            return 0;
        case kHideSplash:
            DismissSplash(window);
            return 0;
        case kApplicationExited:
            DismissSplash(window);
            DestroyWindow(window);
            PostQuitMessage(static_cast<int>(wParam));
            return 0;
        case WM_MOUSEACTIVATE:
            return MA_NOACTIVATE;
        case WM_CLOSE:
            return 0;
        default:
            return DefWindowProcW(window, message, wParam, lParam);
    }
}

HWND CreateSplash(HINSTANCE instance) {
    SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    WNDCLASSW windowClass{};
    windowClass.style = CS_DROPSHADOW;
    windowClass.lpfnWndProc = SplashProcedure;
    windowClass.hInstance = instance;
    windowClass.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    windowClass.hIcon = LoadIconW(instance, MAKEINTRESOURCEW(1));
    windowClass.lpszClassName = L"DownletStartup";
    if (!RegisterClassW(&windowClass) && GetLastError() != ERROR_CLASS_ALREADY_EXISTS) return nullptr;
    HWND window = CreateWindowExW(
        WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE, windowClass.lpszClassName, L"Starting Downlet", WS_POPUP | WS_BORDER,
        0, 0, 320, 112, nullptr, nullptr, instance, nullptr);
    if (!window) return nullptr;
    for (int id : {1, 2, 3}) {
        HWND child = CreateWindowExW(0, L"STATIC", id == 2 ? kProductName : (id == 3 ? StartupText(panelState.status) : nullptr),
            WS_CHILD | WS_VISIBLE | (id == 1 ? SS_ICON : SS_LEFT | SS_CENTERIMAGE),
            0, 0, 0, 0, window, reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)), instance, nullptr);
        if (!child) { DestroyWindow(window); return nullptr; }
    }
    LayoutSplash(window);
    UpdateSplashTheme(window);
    if (!SetTimer(window, 1, 16, nullptr)) { DestroyWindow(window); return nullptr; }
    return window;
}

struct ResourceBytes {
    const unsigned char* data;
    DWORD size;
};

struct ManifestEntry {
    std::string hash;
    fs::path relativePath;
};

struct CabinetContext {
    fs::path root;
    std::wstring error;
};

struct PayloadLease {
    fs::path directory;
    HANDLE handle;
};

std::wstring WindowsError(DWORD code) {
    wchar_t* message = nullptr;
    FormatMessageW(
        FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
        nullptr,
        code,
        0,
        reinterpret_cast<wchar_t*>(&message),
        0,
        nullptr);
    std::wstring result = message ? message : L"Unknown Windows error.";
    if (message) LocalFree(message);
    while (!result.empty() && (result.back() == L'\r' || result.back() == L'\n')) result.pop_back();
    return result;
}

[[noreturn]] void Fail(const std::wstring& message) {
    if (splashWindow) SendMessageW(splashWindow, kHideSplash, 0, 0);
#ifdef DOWNLET_NATIVE_TESTS
    fwprintf(stderr, L"%ls\n", message.c_str());
#else
    MessageBoxW(nullptr, message.c_str(), kProductName, MB_OK | MB_ICONERROR | MB_TASKMODAL);
#endif
    ExitProcess(1);
}

ResourceBytes LoadEmbeddedResource(int id) {
    HRSRC resource = FindResourceW(nullptr, MAKEINTRESOURCEW(id), RT_RCDATA);
    if (!resource) Fail(L"The Downlet payload is missing.");
    HGLOBAL loaded = LoadResource(nullptr, resource);
    if (!loaded) Fail(L"The Downlet payload could not be loaded.");
    auto* bytes = static_cast<const unsigned char*>(LockResource(loaded));
    DWORD size = SizeofResource(nullptr, resource);
    if (!bytes || size == 0) Fail(L"The Downlet payload is empty.");
    return {bytes, size};
}

std::wstring Utf8ToWide(const std::string& value) {
    if (value.empty()) return {};
    int length = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()), nullptr, 0);
    if (length <= 0) Fail(L"The Downlet payload manifest is invalid.");
    std::wstring result(length, L'\0');
    MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()), result.data(), length);
    return result;
}

std::string Hex(const std::vector<unsigned char>& bytes) {
    constexpr char digits[] = "0123456789abcdef";
    std::string result;
    result.reserve(bytes.size() * 2);
    for (unsigned char byte : bytes) {
        result.push_back(digits[byte >> 4]);
        result.push_back(digits[byte & 0x0f]);
    }
    return result;
}

std::string Sha256(const unsigned char* data, size_t size) {
    BCRYPT_ALG_HANDLE algorithm = nullptr;
    BCRYPT_HASH_HANDLE hash = nullptr;
    DWORD objectSize = 0;
    DWORD hashSize = 0;
    DWORD written = 0;
    if (BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM, nullptr, 0) != 0 ||
        BCryptGetProperty(algorithm, BCRYPT_OBJECT_LENGTH, reinterpret_cast<PUCHAR>(&objectSize), sizeof(objectSize), &written, 0) != 0 ||
        BCryptGetProperty(algorithm, BCRYPT_HASH_LENGTH, reinterpret_cast<PUCHAR>(&hashSize), sizeof(hashSize), &written, 0) != 0) {
        if (algorithm) BCryptCloseAlgorithmProvider(algorithm, 0);
        Fail(L"Windows SHA-256 support is unavailable.");
    }
    std::vector<unsigned char> object(objectSize);
    std::vector<unsigned char> digest(hashSize);
    if (BCryptCreateHash(algorithm, &hash, object.data(), objectSize, nullptr, 0, 0) != 0 ||
        BCryptHashData(hash, const_cast<PUCHAR>(data), static_cast<ULONG>(size), 0) != 0 ||
        BCryptFinishHash(hash, digest.data(), hashSize, 0) != 0) {
        if (hash) BCryptDestroyHash(hash);
        BCryptCloseAlgorithmProvider(algorithm, 0);
        Fail(L"The Downlet payload could not be verified.");
    }
    BCryptDestroyHash(hash);
    BCryptCloseAlgorithmProvider(algorithm, 0);
    return Hex(digest);
}

std::string Sha256(const fs::path& path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) return {};
    BCRYPT_HASH_HANDLE hash = nullptr;
    // Windows 10's shared provider avoids opening an algorithm provider for every file.
    if (BCryptCreateHash(BCRYPT_SHA256_ALG_HANDLE, &hash, nullptr, 0, nullptr, 0, 0) != 0) return {};
    std::vector<unsigned char> digest(32);
    std::array<unsigned char, 64 * 1024> buffer{};
    while (input) {
        input.read(reinterpret_cast<char*>(buffer.data()), buffer.size());
        auto count = input.gcount();
        if (count > 0 && BCryptHashData(hash, buffer.data(), static_cast<ULONG>(count), 0) != 0) {
            BCryptDestroyHash(hash);
            return {};
        }
    }
    if (!input.eof() || BCryptFinishHash(hash, digest.data(), static_cast<ULONG>(digest.size()), 0) != 0) digest.clear();
    BCryptDestroyHash(hash);
    return Hex(digest);
}
bool SafeRelativePath(const fs::path& path) {
    if (path.empty() || path.is_absolute() || path.has_root_name() || path.has_root_directory()) return false;
    for (const auto& part : path) {
        if (part == L".." || part == L"." || part.native().find(L':') != std::wstring::npos) return false;
    }
    return true;
}

std::wstring PathKey(const fs::path& path) {
    std::wstring result = path.lexically_normal().generic_wstring();
    CharLowerBuffW(result.data(), static_cast<DWORD>(result.size()));
    return result;
}

std::vector<ManifestEntry> ParseManifest(ResourceBytes manifest) {
    std::istringstream lines(std::string(reinterpret_cast<const char*>(manifest.data), manifest.size));
    std::vector<ManifestEntry> entries;
    std::string line;
    while (std::getline(lines, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.empty()) continue;
        auto separator = line.find('\t');
        if (separator != 64 || separator + 1 >= line.size()) Fail(L"The Downlet payload manifest is invalid.");
        std::string relative = line.substr(separator + 1);
        for (char& character : relative) {
            if (character == '/') character = '\\';
        }
        fs::path relativePath(Utf8ToWide(relative));
        if (!SafeRelativePath(relativePath)) Fail(L"The Downlet payload manifest contains an unsafe path.");
        entries.push_back({line.substr(0, separator), relativePath});
    }
    if (entries.empty()) Fail(L"The Downlet payload manifest is empty.");
    return entries;
}

bool ValidatePayload(const fs::path& root, const std::vector<ManifestEntry>& entries, size_t workers = 4) {
    std::error_code error;
    if (!fs::is_directory(root, error) || error) return false;
    std::unordered_set<std::wstring> expectedFiles;
    for (const auto& entry : entries) {
        expectedFiles.insert(PathKey(entry.relativePath));
    }
    for (fs::recursive_directory_iterator iterator(root, error), end; iterator != end && !error; iterator.increment(error)) {
        DWORD attributes = GetFileAttributesW(iterator->path().c_str());
        if (attributes == INVALID_FILE_ATTRIBUTES || (attributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0) return false;
        if (iterator->is_regular_file(error)) {
            if (error || expectedFiles.erase(PathKey(fs::relative(iterator->path(), root, error))) != 1 || error) return false;
        } else if (!iterator->is_directory(error) || error) {
            return false;
        }
    }
    if (error || !expectedFiles.empty()) return false;
    std::atomic<size_t> next{0};
    std::atomic<bool> valid{true};
    const auto verify = [&] {
        while (valid) {
            const size_t index = next.fetch_add(1);
            if (index >= entries.size()) break;
            if (Sha256(root / entries[index].relativePath) != entries[index].hash) valid = false;
        }
    };
    if (workers <= 1) {
        verify();
    } else {
        std::vector<std::jthread> threads;
        for (size_t index = 0; index < workers && index < entries.size(); ++index) threads.emplace_back(verify);
    }
    return valid;
}

UINT CALLBACK CabinetCallback(PVOID rawContext, UINT notification, UINT_PTR parameter1, UINT_PTR) {
    auto& context = *static_cast<CabinetContext*>(rawContext);
    if (notification == SPFILENOTIFY_FILEINCABINET) {
        auto* info = reinterpret_cast<FILE_IN_CABINET_INFO_W*>(parameter1);
        fs::path relative(info->NameInCabinet);
        if (!SafeRelativePath(relative)) {
            context.error = L"The Downlet payload contains an unsafe path.";
            return FILEOP_ABORT;
        }
        fs::path target = context.root / relative;
        std::error_code error;
        fs::create_directories(target.parent_path(), error);
        if (error || target.native().size() >= MAX_PATH) {
            context.error = L"The Downlet payload destination is unavailable.";
            return FILEOP_ABORT;
        }
        wcscpy_s(info->FullTargetName, MAX_PATH, target.c_str());
        return FILEOP_DOIT;
    }
    if (notification == SPFILENOTIFY_FILEEXTRACTED) {
        auto* info = reinterpret_cast<FILEPATHS_W*>(parameter1);
        if (info->Win32Error != NO_ERROR) {
            context.error = L"The Downlet payload could not be extracted: " + WindowsError(info->Win32Error);
            return FILEOP_ABORT;
        }
    }
    return NO_ERROR;
}

void WriteFile(const fs::path& path, ResourceBytes bytes) {
    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    if (!output || !output.write(reinterpret_cast<const char*>(bytes.data), bytes.size)) {
        Fail(L"The Downlet payload could not be prepared for extraction.");
    }
}

fs::path LocalAppData() {
    PWSTR value = nullptr;
    HRESULT result = SHGetKnownFolderPath(FOLDERID_LocalAppData, KF_FLAG_CREATE, nullptr, &value);
    if (FAILED(result) || !value) Fail(L"The local application-data folder is unavailable.");
    fs::path path(value);
    CoTaskMemFree(value);
    return path;
}

std::wstring PayloadHashFromCacheName(const std::wstring& cacheName) {
    if (cacheName.size() <= kSha256HexLength || cacheName[cacheName.size() - kSha256HexLength - 1] != L'-') {
        return {};
    }
    std::wstring hash = cacheName.substr(cacheName.size() - kSha256HexLength);
    for (wchar_t character : hash) {
        bool hexadecimal =
            (character >= L'0' && character <= L'9') ||
            (character >= L'a' && character <= L'f') ||
            (character >= L'A' && character <= L'F');
        if (!hexadecimal) return {};
    }
    return hash;
}

fs::path PayloadLeasePath(const fs::path& runtimeRoot, const std::wstring& cacheName) {
    return runtimeRoot / (cacheName + kPayloadLeaseSuffix);
}

void EnsurePayloadLeaseFile(const fs::path& leasePath) {
    DWORD attributes = GetFileAttributesW(leasePath.c_str());
    if (attributes == INVALID_FILE_ATTRIBUTES) {
        DWORD error = GetLastError();
        if (error != ERROR_FILE_NOT_FOUND && error != ERROR_PATH_NOT_FOUND) {
            Fail(L"Downlet could not inspect its runtime lease.");
        }
        std::ofstream output(leasePath, std::ios::binary | std::ios::trunc);
        if (!output) Fail(L"Downlet could not create its runtime lease.");
        attributes = GetFileAttributesW(leasePath.c_str());
    }
    if (
        attributes == INVALID_FILE_ATTRIBUTES ||
        (attributes & (FILE_ATTRIBUTE_DIRECTORY | FILE_ATTRIBUTE_REPARSE_POINT)) != 0) {
        Fail(L"Downlet runtime lease is invalid.");
    }
}

HANDLE OpenPayloadLease(const fs::path& leasePath, DWORD shareMode) {
    HANDLE handle =
        CreateFileW(
            leasePath.c_str(),
            GENERIC_READ,
            shareMode,
            nullptr,
            OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL,
            nullptr);
    if (handle == INVALID_HANDLE_VALUE) {
        Fail(
            shareMode == 0
                ? L"Downlet cannot replace a runtime cache while it is in use."
                : L"Downlet could not acquire its runtime lease.");
    }
    return handle;
}

void PruneStalePayloads(const fs::path& runtimeRoot, const fs::path& currentDirectory) {
    std::error_code iterationError;
    fs::directory_iterator iterator(runtimeRoot, iterationError);
    fs::directory_iterator end;
    while (!iterationError && iterator != end) {
        fs::path leasePath = iterator->path();
        iterator.increment(iterationError);

        DWORD leaseAttributes = GetFileAttributesW(leasePath.c_str());
        if (
            leaseAttributes == INVALID_FILE_ATTRIBUTES ||
            (leaseAttributes & (FILE_ATTRIBUTE_DIRECTORY | FILE_ATTRIBUTE_REPARSE_POINT)) != 0) {
            continue;
        }

        std::wstring leaseName = leasePath.filename().wstring();
        std::wstring suffix = kPayloadLeaseSuffix;
        if (!leaseName.ends_with(suffix)) continue;
        std::wstring cacheName = leaseName.substr(0, leaseName.size() - suffix.size());
        if (cacheName == currentDirectory.filename().wstring()) continue;
        std::wstring payloadHash = PayloadHashFromCacheName(cacheName);
        if (payloadHash.empty()) continue;

        std::wstring mutexName = L"Local\\Downlet-runtime-" + payloadHash;
        HANDLE mutex = CreateMutexW(nullptr, FALSE, mutexName.c_str());
        if (!mutex) continue;
        DWORD wait = WaitForSingleObject(mutex, 0);
        if (wait != WAIT_OBJECT_0 && wait != WAIT_ABANDONED) {
            CloseHandle(mutex);
            continue;
        }

        HANDLE exclusiveLease =
            CreateFileW(
                leasePath.c_str(),
                GENERIC_READ,
                0,
                nullptr,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                nullptr);
        if (exclusiveLease != INVALID_HANDLE_VALUE) {
            fs::path candidate = runtimeRoot / cacheName;
            DWORD candidateAttributes = GetFileAttributesW(candidate.c_str());
            bool removed = candidateAttributes == INVALID_FILE_ATTRIBUTES;
            if (
                !removed &&
                (candidateAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0 &&
                (candidateAttributes & FILE_ATTRIBUTE_REPARSE_POINT) == 0) {
                std::error_code removeError;
                fs::remove_all(candidate, removeError);
                removed = !removeError;
            }
            CloseHandle(exclusiveLease);
            if (removed) {
                std::error_code markerError;
                fs::remove(leasePath, markerError);
            }
        }

        ReleaseMutex(mutex);
        CloseHandle(mutex);
    }
}

PayloadLease EnsurePayload(ResourceBytes cabinet, ResourceBytes manifestResource,
    const fs::path& runtimeRoot = LocalAppData() / L"Downlet" / L"runtime") {
    auto manifest = ParseManifest(manifestResource);
    std::string payloadHash = Sha256(manifestResource.data, manifestResource.size);
    std::wstring payloadHashWide(payloadHash.begin(), payloadHash.end());
    fs::path finalDirectory = runtimeRoot / (std::wstring(DOWNLET_VERSION) + L"-" + payloadHashWide);
    fs::path leasePath = PayloadLeasePath(runtimeRoot, finalDirectory.filename().wstring());

    std::wstring mutexName = L"Local\\Downlet-runtime-" + payloadHashWide;
    HANDLE mutex = CreateMutexW(nullptr, FALSE, mutexName.c_str());
    if (!mutex) Fail(L"Downlet could not create its runtime lock.");
    DWORD wait = WaitForSingleObject(mutex, INFINITE);
    if (wait != WAIT_OBJECT_0 && wait != WAIT_ABANDONED) {
        CloseHandle(mutex);
        Fail(L"Downlet could not acquire its runtime lock.");
    }

    const bool firstLaunch = !fs::exists(finalDirectory);
    if (!ValidatePayload(finalDirectory, manifest)) {
        PostMessageW(splashWindow, kStartupStatus,
            static_cast<WPARAM>(firstLaunch ? StartupStatus::FirstLaunch : StartupStatus::Repairing), 0);
        std::error_code error;
        fs::create_directories(runtimeRoot, error);
        if (error) {
            ReleaseMutex(mutex);
            CloseHandle(mutex);
            Fail(L"Downlet could not create its local runtime folder.");
        }
        EnsurePayloadLeaseFile(leasePath);
        HANDLE exclusiveLease = OpenPayloadLease(leasePath, 0);
        fs::remove_all(finalDirectory, error);
        if (error) {
            ReleaseMutex(mutex);
            CloseHandle(mutex);
            Fail(L"Downlet could not replace its invalid local runtime cache.");
        }

        std::wstring unique = std::to_wstring(GetCurrentProcessId()) + L"-" + std::to_wstring(GetTickCount64());
        fs::path temporaryDirectory = runtimeRoot / (L".extract-" + unique);
        fs::path cabinetPath = runtimeRoot / (L".payload-" + unique + L".cab");
        fs::create_directories(temporaryDirectory, error);
        if (error) {
            ReleaseMutex(mutex);
            CloseHandle(mutex);
            Fail(L"Downlet could not create its temporary runtime folder.");
        }
        WriteFile(cabinetPath, cabinet);
        CabinetContext context{temporaryDirectory, {}};
        BOOL extracted = SetupIterateCabinetW(cabinetPath.c_str(), 0, CabinetCallback, &context);
        fs::remove(cabinetPath, error);
        if (!extracted || !context.error.empty() || !ValidatePayload(temporaryDirectory, manifest)) {
            fs::remove_all(temporaryDirectory, error);
            ReleaseMutex(mutex);
            CloseHandle(mutex);
            Fail(context.error.empty() ? L"The Downlet payload failed verification." : context.error);
        }
        if (!MoveFileExW(temporaryDirectory.c_str(), finalDirectory.c_str(), MOVEFILE_WRITE_THROUGH)) {
            DWORD moveError = GetLastError();
            fs::remove_all(temporaryDirectory, error);
            ReleaseMutex(mutex);
            CloseHandle(mutex);
            Fail(L"Downlet could not activate its local runtime: " + WindowsError(moveError));
        }
        CloseHandle(exclusiveLease);
        PostMessageW(splashWindow, kStartupStatus, static_cast<WPARAM>(StartupStatus::Starting), 0);
    }

    EnsurePayloadLeaseFile(leasePath);
    HANDLE activeLease = OpenPayloadLease(leasePath, FILE_SHARE_READ);
    ReleaseMutex(mutex);
    CloseHandle(mutex);
    return {finalDirectory, activeLease};
}

std::wstring QuoteArgument(const std::wstring& argument) {
    if (argument.find_first_of(L" \t\"") == std::wstring::npos) return argument;
    std::wstring result = L"\"";
    size_t backslashes = 0;
    for (wchar_t character : argument) {
        if (character == L'\\') {
            ++backslashes;
        } else if (character == L'\"') {
            result.append(backslashes * 2 + 1, L'\\');
            result.push_back(L'\"');
            backslashes = 0;
        } else {
            result.append(backslashes, L'\\');
            backslashes = 0;
            result.push_back(character);
        }
    }
    result.append(backslashes * 2, L'\\');
    result.push_back(L'\"');
    return result;
}

DWORD LaunchApplication(const fs::path& runtimeDirectory) {
    fs::path executable = runtimeDirectory / L"Downlet.exe";
    if (!fs::is_regular_file(executable)) Fail(L"The extracted Downlet launcher is missing.");

    int argumentCount = 0;
    wchar_t** arguments = CommandLineToArgvW(GetCommandLineW(), &argumentCount);
    if (!arguments) Fail(L"Downlet could not read its command line.");
    std::wstring commandLine = QuoteArgument(executable.wstring());
    for (int index = 1; index < argumentCount; ++index) {
        commandLine.push_back(L' ');
        commandLine += QuoteArgument(arguments[index]);
    }
    LocalFree(arguments);

    std::vector<wchar_t> mutableCommandLine(commandLine.begin(), commandLine.end());
    mutableCommandLine.push_back(L'\0');
    STARTUPINFOW startup{sizeof(startup)};
    PROCESS_INFORMATION process{};
    if (!CreateProcessW(
            executable.c_str(),
            mutableCommandLine.data(),
            nullptr,
            nullptr,
            FALSE,
            CREATE_UNICODE_ENVIRONMENT,
            nullptr,
            runtimeDirectory.c_str(),
            &startup,
            &process)) {
        Fail(L"Downlet could not start: " + WindowsError(GetLastError()));
    }
    CloseHandle(process.hThread);
    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD exitCode = 1;
    GetExitCodeProcess(process.hProcess, &exitCode);
    CloseHandle(process.hProcess);
    PruneStalePayloads(runtimeDirectory.parent_path(), runtimeDirectory);
    if (WaitForSingleObject(startupReady, 0) != WAIT_OBJECT_0) {
        Fail(L"Downlet closed before its main window could open.");
    }
    return exitCode;
}
}  // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    launcherStartedAt = GetTickCount64();
    const std::wstring eventName = L"Local\\Downlet-startup-" + std::to_wstring(GetCurrentProcessId());
    startupReady = CreateEventW(nullptr, TRUE, FALSE, eventName.c_str());
    if (!startupReady || !SetEnvironmentVariableW(L"DOWNLET_STARTUP_EVENT", eventName.c_str())) {
        Fail(L"Downlet could not prepare its startup notification.");
    }
    splashWindow = CreateSplash(instance);
    if (!splashWindow) Fail(L"Downlet could not display its startup window.");
    std::thread worker([] {
        try {
            ResourceBytes cabinet = LoadEmbeddedResource(kCabinetResource);
            ResourceBytes manifest = LoadEmbeddedResource(kManifestResource);
            PayloadLease payload = EnsurePayload(cabinet, manifest);
            DWORD exitCode = LaunchApplication(payload.directory);
            CloseHandle(payload.handle);
            PostMessageW(splashWindow, kApplicationExited, exitCode, 0);
        } catch (...) {
            Fail(L"Downlet could not finish starting. Please try again.");
        }
    });
    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    worker.join();
    CloseHandle(startupReady);
    return static_cast<int>(message.wParam);
}
