# Nyx 🌑

Nyx is a lightweight screen filter application for Android. It provides dimming and blue light filter.

### About the Name 🏛️
The app is named after **Nyx**, the Greek primordial goddess of the **Night**.

## Features ✨

- **System-wide Overlay**: Applies the filter everywhere using Android's Accessibility Service.
- **Color Temperature (Kelvin)**: Adjust the screen warmth from 1000K (Candlelight) up to 6500K (Daylight) for optimal sleep hygiene.
- **Black Level (OLED-friendly)**: An overlay can only approximate a color filter, and warming the picture lifts black pixels. This slider sets how much: at 0% blacks stay truly black (the Blue Light Filter then simply darkens evenly), higher values add warm fill at the cost of lifted blacks. LCD screens, whose blacks are already backlit, can afford a higher setting.
- **Live Preview**: A white-to-black strip in the app sits under the overlay like everything else, so it always shows exactly what your settings do to whites and blacks.
- **Deep Dimming**: Go beyond the system's minimum brightness for comfortable nighttime reading.
- **Material 3 Palette, Light/Dark/System Themes**: A clean UI built with Android Views (ViewBinding) and AppCompat. One tap on the toolbar button switches between light and dark; a fresh install follows the system theme.
- **Persistent Settings**: Preferences are saved automatically using SharedPreferences.
- **Optimized Size**: Lightweight footprint with no unnecessary large dependencies.

## Technical Details 🛠️

- **Language**: Kotlin
- **UI Framework**: Android Views + ViewBinding (AppCompat)
- **Theming**: AppCompat DayNight with a Material 3 color palette
- **Data Storage**: SharedPreferences
- **Background Work**: Accessibility Service for high-level UI overlays
- **Build System**: Kotlin DSL (build.gradle.kts)
- **Compatibility**:
  - **Minimum SDK**: Android 7.0 (API level 24)
  - **Target SDK**: API level 37

## Installation 📲

1. Download the latest APK from the **GitHub Releases** section of this repository.
2. Open the downloaded APK file on your Android device.
3. Follow the on-screen instructions to install and open **Nyx**.
4. Grant the **Accessibility Service** permission when prompted to enable the system-wide filter.
5. **Note for Android 13+**: Since the app is installed from outside the Play Store, you may need to "Allow Restricted Settings" to grant accessibility access:
   - Go to `Settings > Applications > Nyx`
   - Tap the **3 vertical dots** in the top right and select **Allow Restricted Settings**.

## Filter vs. Service 🔌

Nyx keeps the *accessibility service* and the *filter* separate:

- The **switch in the app** only turns the filter (overlay) on and off. The service stays enabled, so switching back on is instant and needs no trip to Settings. While the filter is off, the sliders are hidden and nothing is drawn, so an idle service costs next to nothing (no accessibility events are subscribed, no layer is composited); the process just stays resident.
- Enabling or disabling the **service itself** is done the usual Android way: the accessibility shortcut (e.g. the volume-key or Side + Volume up shortcut), Settings, or, when the filter is off but the service is still running, the **Disable service** button in the app.
- **Enabling the service always turns the filter on** (one shortcut press = filter on). If Android merely restarts a running service (the process was killed, or after a reboot), your last on/off choice is kept, so a filter you switched off does not light up again by itself.
- Note: the volume-key style shortcuts are handled by Android itself, which flips the service on or off. Pressing one while the service is on but the filter is off therefore turns the service off; it cannot turn the overlay back on.

## Troubleshooting 🩺

**The filter disappears after a while.** Some manufacturers' battery managers (Xiaomi, Huawei, Oppo, OnePlus, Samsung's "sleeping apps", and others) kill accessibility services in the background. Exempt Nyx from battery optimization, allow it to auto-start / run in the background, and lock it in the recent apps list if your phone offers that. The exact menus vary per brand; [dontkillmyapp.com](https://dontkillmyapp.com) lists the steps for many devices.

## How it Works 🧠

Nyx uses a transparent accessibility overlay. The Kelvin temperature is approximated as an RGB filter, and the whole filter (Kelvin color, Blue Light Filter strength, dimming) is solved into a single alpha-blended layer: the blue channel is matched exactly, and the red/green compensation is capped by the Black Level budget so that black pixels are not lit up. See `OverlayColorCalculator` for the math.

### Multi-Display & Foldables 📱

- **Primary & Default Display**: The overlay attaches using the Accessibility Service's default-display `WindowManager` (`Display.DEFAULT_DISPLAY`).
- **Foldables (Main vs. Cover Screen)**: On most book-style foldables (e.g., Galaxy Z Fold, Pixel Fold), folding and unfolding updates the metrics and configuration of `Display.DEFAULT_DISPLAY` dynamically. The overlay resizes automatically to fit the active display.
- **External & Secondary Displays**: External monitors (via USB-C DisplayPort, Samsung DeX, or wireless projection) and standalone secondary cover screens (e.g., on clamshell flip devices) operate on separate `Display` instances. These secondary displays currently remain unfiltered.

## License 📄

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Note: This app requires Accessibility Service permissions to function as a system-wide screen filter.*
