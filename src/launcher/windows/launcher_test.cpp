// Console-only checks: no visible windows or application launches.
#define DOWNLET_NATIVE_TESTS
#include "launcher.cpp"
#include <cassert>
#include <iostream>

int main() {
    StartupPanelState fast;
    assert(fast.Advance(100, true) == SplashAction::Hide);
    assert(fast.Advance(500, false) == SplashAction::None);
    StartupPanelState delayed;
    assert(delayed.Advance(249, false) == SplashAction::None);
    delayed.Update(StartupStatus::FirstLaunch);
    assert(delayed.status == StartupStatus::FirstLaunch);
    assert(delayed.Advance(250, false) == SplashAction::Show);
    delayed.Update(StartupStatus::Starting);
    assert(delayed.status == StartupStatus::Starting);
    assert(delayed.Advance(300, true) == SplashAction::Hide);
    assert(delayed.Advance(600, false) == SplashAction::None);
    for (bool visible : {false, true}) {
        StartupPanelState failed;
        if (visible) assert(failed.Advance(250, false) == SplashAction::Show);
        failed.Dismiss();
        failed.Update(StartupStatus::Repairing);
        assert(failed.status == StartupStatus::Starting);
        assert(failed.Advance(1000, false) == SplashAction::None);
        assert(!failed.visible);
    }
    assert(std::wstring(StartupText(StartupStatus::Repairing)) == L"Preparing Downlet\u2026");
    startupReady = CreateEventW(nullptr, TRUE, TRUE, nullptr);
    launcherStartedAt = GetTickCount64();
    splashWindow = CreateSplash(GetModuleHandleW(nullptr));
    assert(splashWindow && !IsWindowVisible(splashWindow));
    SendMessageW(splashWindow, kStartupStatus, static_cast<WPARAM>(StartupStatus::FirstLaunch), 0);
    wchar_t status[80]{};
    GetDlgItemTextW(splashWindow, 3, status, 80);
    assert(std::wstring(status) == StartupText(StartupStatus::FirstLaunch));
    SendMessageW(splashWindow, WM_TIMER, 1, 0);
    assert(panelState.dismissed && !IsWindowVisible(splashWindow));
    SendMessageW(splashWindow, kStartupStatus, static_cast<WPARAM>(StartupStatus::Repairing), 0);
    SendMessageW(splashWindow, WM_TIMER, 1, 0);
    assert(!IsWindowVisible(splashWindow));

    const fs::path testRoot = fs::current_path() / (L"native-test-" + std::to_wstring(GetCurrentProcessId()));
    assert(!fs::exists(testRoot));
    ResourceBytes cabinet = LoadEmbeddedResource(kCabinetResource);
    ResourceBytes manifest = LoadEmbeddedResource(kManifestResource);
    auto entries = ParseManifest(manifest);
    auto fresh = EnsurePayload(cabinet, manifest, testRoot);
    assert(ValidatePayload(fresh.directory, entries));
    const auto originalTime = fs::last_write_time(fresh.directory / entries.front().relativePath);
    CloseHandle(fresh.handle);
    auto cached = EnsurePayload(cabinet, manifest, testRoot);
    assert(fs::last_write_time(cached.directory / entries.front().relativePath) == originalTime);
    CloseHandle(cached.handle);
    std::ofstream(cached.directory / entries.front().relativePath, std::ios::binary | std::ios::app) << "damage";
    assert(!ValidatePayload(cached.directory, entries));
    auto repaired = EnsurePayload(cabinet, manifest, testRoot);
    assert(ValidatePayload(repaired.directory, entries));
    CloseHandle(repaired.handle);
    // Both workers contend for the same fresh payload, then retain independent leases.
    const fs::path concurrentRoot = testRoot / L"concurrent";
    PayloadLease first{}, second{};
    std::thread one([&] { first = EnsurePayload(cabinet, manifest, concurrentRoot); });
    std::thread two([&] { second = EnsurePayload(cabinet, manifest, concurrentRoot); });
    one.join(); two.join();
    assert(first.directory == second.directory && ValidatePayload(first.directory, entries));
    CloseHandle(first.handle); CloseHandle(second.handle);
    fs::remove_all(testRoot);
    DestroyWindow(splashWindow);
    CloseHandle(startupReady);
    std::cout << "Native checks passed: timing, status, dismissal, hidden window, fresh/cache/repair/concurrent payloads.\n";
}
