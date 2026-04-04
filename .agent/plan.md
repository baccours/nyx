# Project Plan

Nyx: A Screen Dimmer & Blue Light Filter Android app. It uses a WindowManager overlay service to provide ultra-dimming and a blue light filter. It features a Foreground Service for persistence, Material 3 Dark Theme UI with sliders for dimming and blue light intensity, and handles necessary permissions (Overlay, Foreground Service).

## Project Brief

# Project Brief: Nyx

Nyx is a specialized utility application designed to enhance user comfort during nighttime device usage. By leveraging system-level overlays, it provides a more flexible and powerful dimming solution than the standard Android brightness controls.

## Features

*   **Ultra-Dimming Overlay:** Implements a system-wide transparent black overlay using `WindowManager` to reduce screen brightness below the hardware's minimum levels.
*   **Blue Light Filter:** Provides an adjustable amber/warm color overlay to filter out blue light, helping to reduce eye strain and improve sleep quality.
*   **Persistent Foreground Service:** Ensures the dimming and filtering effects remain active even when the app is in the background, with quick-access controls via a persistent notification.
*   **Material 3 Control Dashboard:** A modern, vibrant UI featuring sliders for real-time adjustment of dimming intensity and blue light temperature.
*   **Permission Management Flow:** A streamlined onboarding process to request and manage the necessary "Display over other apps" (Overlay) and "Foreground Service" permissions.

## High-Level Technical Stack

*   **Kotlin:** The primary language for robust and expressive Android development.
*   **Jetpack Compose:** For building a modern, declarative UI with Material 3 components and energetic styling.
*   **Kotlin Coroutines:** To handle background tasks and UI updates asynchronously.
*   **WindowManager API:** The core component used to create and manage the system-level overlay layers.
*   **Android Foreground Services:** To maintain service persistence and provide the required notification interface.
*   **KSP (Kotlin Symbol Processing):** Used for efficient code generation, specifically for library integrations like Room or Moshi if expanded.

## Implementation Steps
**Total Duration:** 21m 7s

### Task_1_CoreServiceAndPermissions: Implement permission handling and the core Foreground Service with WindowManager overlay logic.
- **Status:** COMPLETED
- **Updates:** Implemented permission handling (Overlay, Notifications) and the core Foreground Service (`NyxService`) with WindowManager overlay logic for dimming and blue light filtering. The service is correctly managed and shows a persistent notification. The overlay's alpha and color are updated in real-time as users adjust the sliders. Built a basic dashboard with toggle and sliders for dimming and blue light intensity. Added Edge-to-Edge support.
- **Acceptance Criteria:**
  - Overlay permission and Post Notification permission (for Android 13+) are requested and handled
  - Foreground Service is implemented with a persistent notification
  - WindowManager overlay is functional and can apply color filters/alpha dimming
  - Service lifecycle is correctly managed
- **Duration:** 13m 38s

### Task_2_ControlDashboard: Develop the Material 3 Control Dashboard using Jetpack Compose to manage dimming and filtering settings.
- **Status:** COMPLETED
- **Updates:** Developed a polished Material 3 dashboard using Jetpack Compose with sliders for Dimming Intensity and Blue Light Temperature. Integrated Jetpack Preferences DataStore to persist user settings (dimming intensity, blue light intensity, and service state). The UI communicates real-time updates to the `NyxService` using `StateFlow`. Added an auto-start mechanism in `MainActivity` to restore the service state if it was previously enabled. Refined the app's visual style with vibrant Material 3 colors and a custom adaptive icon.
- **Acceptance Criteria:**
  - UI features sliders for Dimming Intensity and Blue Light Temperature
  - UI communicates real-time updates to the Foreground Service
  - State is persisted (e.g., using DataStore or SharedPreferences)
  - Material 3 components and vibrant energetic styling are used
- **Duration:** 5m 1s

### Task_3_DesignRefinement: Refine the app's visual identity with a custom theme, adaptive icon, and Edge-to-Edge support.
- **Status:** COMPLETED
- **Updates:** Refined the app's visual identity with a custom Material 3 theme, adaptive icon, and full Edge-to-Edge support. Updated `Color.kt` and `Theme.kt` with a vibrant color palette (Light/Dark/Dynamic). Implemented `enableEdgeToEdge()` in `MainActivity` and handled `WindowInsets` for all UI components. Created a custom adaptive icon (background and foreground) representing the app's function. Checked and verified all components.
- **Acceptance Criteria:**
  - Full Edge-to-Edge display is implemented
  - Adaptive app icon matching the app's function is created
  - Vibrant Material 3 color scheme for Light and Dark modes is applied
- **Duration:** 2m 28s

### Task_4_RunAndVerify: Finalize the application, ensuring stability and alignment with requirements.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - The project builds successfully
  - Application is stable with no crashes during service start/stop or overlay adjustments
  - Service persists correctly in the background
  - Critic_agent verifies application stability and alignment with requirements
- **StartTime:** 2026-03-29 15:55:08 CEST

