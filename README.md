# Nyx 🌑

Nyx is a lightweight screen filter application for Android. It provides dimming and blue light filter.

### About the Name 🏛️
The app is named after **Nyx**, the Greek primordial goddess of the **Night**.

## Features ✨

- **System-wide Overlay**: Applies the filter everywhere using Android's Accessibility Service.
- **Color Temperature (Kelvin)**: Adjust the screen warmth from 1000K (Candlelight) up to 7000K (Daylight) for optimal sleep hygiene.
- **Deep Dimming**: Go beyond the system's minimum brightness for comfortable night-time reading.
- **Material 3 Design**: A clean, modern UI built with Jetpack Compose.
- **Persistent Settings**: Preferences are saved automatically using Jetpack DataStore.
- **Optimized Size**: Lightweight footprint with no unnecessary large dependencies.

## Technical Details 🛠️

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Theming**: Material 3
- **Data Storage**: Jetpack DataStore (Preferences)
- **Background Work**: Accessibility Service for high-level UI overlays
- **Build System**: Kotlin DSL (build.gradle.kts)
- **Compatibility**:
  - **Minimum SDK**: Android 7.0 (API level 24)
  - **Target SDK**: Android 15 (API level 36)

## Installation 📲

1. Download the latest APK from the **GitHub Releases** section of this repository.
2. Open the downloaded APK file on your Android device.
3. Follow the on-screen instructions to install and open **Nyx**.
4. Grant the **Accessibility Service** permission when prompted to enable the system-wide filter.
5. **Note for Android 13+**: Since the app is installed from outside the Play Store, you may need to "Allow Restricted Settings" to grant accessibility access:
   - Go to `Settings > Applications > Nyx`
   - Tap the **3 vertical dots** in the top right and select **Allow Restricted Settings**.

## How it Works 🧠

Nyx uses a transparent accessibility overlay to tint the screen. The color calculation uses a formula to approximate Kelvin temperatures into RGB values, which are then combined with your chosen dimming intensity and opacity to create the perfect night-time viewing experience.

## License 📄

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Note: This app requires Accessibility Service permissions to function as a system-wide screen filter.*
