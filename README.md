# Nyx 🌑

Nyx is a lightweight screen filter application for Android. It provides dimming and blue light filter.

### About the Name 🏛️
The app is named after **Nyx**, the Greek primordial goddess of the **Night**.

## Features ✨

- **System-wide Overlay**: Applies the filter everywhere using Android's Accessibility Service.
- **Color Temperature (Kelvin)**: Adjust the screen warmth from 1000K (Candlelight) up to 7000K (Daylight) for optimal sleep hygiene.
- **Black Level (OLED-friendly)**: An overlay can only approximate a color filter, and warming the picture lifts black pixels. This slider sets how much: at 0% blacks stay truly black (the Blue Light Filter then simply darkens evenly), higher values add warm fill at the cost of lifted blacks. LCD screens, whose blacks are already backlit, can afford a higher setting.
- **Deep Dimming**: Go beyond the system's minimum brightness for comfortable night-time reading.
- **Material 3 Palette, Light/Dark/System Themes**: A clean UI built with Android Views (ViewBinding) and AppCompat. One tap on the toolbar button switches between light and dark; a fresh install follows the system theme.
- **Persistent Settings**: Preferences are saved automatically using Jetpack DataStore.
- **Optimized Size**: Lightweight footprint with no unnecessary large dependencies.

## Technical Details 🛠️

- **Language**: Kotlin
- **UI Framework**: Android Views + ViewBinding (AppCompat)
- **Theming**: AppCompat DayNight with a Material 3 color palette
- **Data Storage**: Jetpack DataStore (Preferences)
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

## How it Works 🧠

Nyx uses a transparent accessibility overlay. The Kelvin temperature is approximated as an RGB filter, and the whole filter (Kelvin color, Blue Light Filter strength, dimming) is solved into a single alpha-blended layer: the blue channel is matched exactly, and the red/green compensation is capped by the Black Level budget so that black pixels are not lit up. See `OverlayColorCalculator` for the math.

## License 📄

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Note: This app requires Accessibility Service permissions to function as a system-wide screen filter.*
