# Local Test Mode (Bypass telnet/Frida)

To test the application and its WebView (Instrument Projector) on standard Android emulators or non-vehicle devices, you can enable **Local Test Mode**.

## How to Enable

1.  In **Android Studio**, go to **Run** > **Edit Configurations...**.
2.  Select your app's run configuration.
3.  In the **General** (or **Miscellaneous**) tab, find the **Launch Options** section.
4.  In the **Intent Extras** field, add:
    ```bash
    --ez localTestMode true
    ```
5.  Click **OK** and **Run** the app on your emulator.

## What is Bypassed

- **Telnet Check**: The app skips the retry loop for high-level telnet connectivity.
- **Shizuku Initialization**: Bypasses Shizuku and hardware binder checks.
- **Frida Hooks**: Skips Frida initialization (hooks).

## Mock Data provided

- **Main Screen Status**: Returns `true` (ON) to force the WebView to load immediately.
- **Car Info**: Returns "Haval (Local)" as the brand name.
- **Data Requests**: `getData()` and `getUpdatedData()` return `null` safely when binders are missing.

---
*This file is maintained for guiding AI agents and developers. See [DEVELOPER_NOTES.md](file:///c:/Users/marce/StudioProjects/haval-app-tool-multimidia/DEVELOPER_NOTES.md) for general developer info.*
