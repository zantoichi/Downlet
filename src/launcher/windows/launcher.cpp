#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <bcrypt.h>
#include <setupapi.h>
#include <shellapi.h>
#include <shlobj.h>

#include <array>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>
#include <unordered_set>
#include <vector>

#include "launcher_config.h"

namespace fs = std::filesystem;

namespace {
constexpr int kCabinetResource = 101;
constexpr int kManifestResource = 102;
constexpr wchar_t kProductName[] = L"Downlet";

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
    MessageBoxW(nullptr, message.c_str(), kProductName, MB_OK | MB_ICONERROR | MB_TASKMODAL);
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
    BCRYPT_ALG_HANDLE algorithm = nullptr;
    BCRYPT_HASH_HANDLE hash = nullptr;
    DWORD objectSize = 0;
    DWORD hashSize = 0;
    DWORD written = 0;
    if (BCryptOpenAlgorithmProvider(&algorithm, BCRYPT_SHA256_ALGORITHM, nullptr, 0) != 0 ||
        BCryptGetProperty(algorithm, BCRYPT_OBJECT_LENGTH, reinterpret_cast<PUCHAR>(&objectSize), sizeof(objectSize), &written, 0) != 0 ||
        BCryptGetProperty(algorithm, BCRYPT_HASH_LENGTH, reinterpret_cast<PUCHAR>(&hashSize), sizeof(hashSize), &written, 0) != 0) {
        if (algorithm) BCryptCloseAlgorithmProvider(algorithm, 0);
        return {};
    }
    std::vector<unsigned char> object(objectSize);
    std::vector<unsigned char> digest(hashSize);
    if (BCryptCreateHash(algorithm, &hash, object.data(), objectSize, nullptr, 0, 0) != 0) {
        BCryptCloseAlgorithmProvider(algorithm, 0);
        return {};
    }
    std::array<unsigned char, 64 * 1024> buffer{};
    while (input) {
        input.read(reinterpret_cast<char*>(buffer.data()), buffer.size());
        auto count = input.gcount();
        if (count > 0 && BCryptHashData(hash, buffer.data(), static_cast<ULONG>(count), 0) != 0) {
            BCryptDestroyHash(hash);
            BCryptCloseAlgorithmProvider(algorithm, 0);
            return {};
        }
    }
    if (BCryptFinishHash(hash, digest.data(), hashSize, 0) != 0) digest.clear();
    BCryptDestroyHash(hash);
    BCryptCloseAlgorithmProvider(algorithm, 0);
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

bool ValidatePayload(const fs::path& root, const std::vector<ManifestEntry>& entries) {
    std::error_code error;
    if (!fs::is_directory(root, error) || error) return false;
    std::unordered_set<std::wstring> expectedFiles;
    for (const auto& entry : entries) {
        expectedFiles.insert(PathKey(entry.relativePath));
        fs::path file = root / entry.relativePath;
        if (!fs::is_regular_file(file, error) || error || Sha256(file) != entry.hash) return false;
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
    return !error && expectedFiles.empty();
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

fs::path EnsurePayload(ResourceBytes cabinet, ResourceBytes manifestResource) {
    auto manifest = ParseManifest(manifestResource);
    std::string payloadHash = Sha256(manifestResource.data, manifestResource.size);
    std::wstring payloadHashWide(payloadHash.begin(), payloadHash.end());
    fs::path runtimeRoot = LocalAppData() / L"Downlet" / L"runtime";
    fs::path finalDirectory = runtimeRoot / (std::wstring(DOWNLET_VERSION) + L"-" + payloadHashWide);

    std::wstring mutexName = L"Local\\Downlet-runtime-" + payloadHashWide;
    HANDLE mutex = CreateMutexW(nullptr, FALSE, mutexName.c_str());
    if (!mutex) Fail(L"Downlet could not create its runtime lock.");
    DWORD wait = WaitForSingleObject(mutex, INFINITE);
    if (wait != WAIT_OBJECT_0 && wait != WAIT_ABANDONED) {
        CloseHandle(mutex);
        Fail(L"Downlet could not acquire its runtime lock.");
    }

    if (!ValidatePayload(finalDirectory, manifest)) {
        std::error_code error;
        fs::create_directories(runtimeRoot, error);
        if (error) {
            ReleaseMutex(mutex);
            CloseHandle(mutex);
            Fail(L"Downlet could not create its local runtime folder.");
        }
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
    }

    ReleaseMutex(mutex);
    CloseHandle(mutex);
    return finalDirectory;
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
    return exitCode;
}
}  // namespace

int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    ResourceBytes cabinet = LoadEmbeddedResource(kCabinetResource);
    ResourceBytes manifest = LoadEmbeddedResource(kManifestResource);
    return static_cast<int>(LaunchApplication(EnsurePayload(cabinet, manifest)));
}
