# SpotGo Mobile App

SpotGo Mobile App is the Android client for SpotGo, a parking management platform for drivers and parking administrators. It is built with Kotlin and Jetpack Compose and uses Material 3 Expressive for its interface.

## User roles

The application organizes navigation into two workspaces:

| Role | Navigation destinations |
| --- | --- |
| Driver | Explore, Reservations, Payments, Profile |
| Parking Admin | Dashboard, Occupancy, Alerts, More |

Each workspace has its own layout and navigation state. The login screen provides access to both workspaces.

Create account opens Driver registration. The registration form starts with empty fields and returns to login through Sign in or the back button.

Parking administrators access parking infrastructure from More. Infrastructure connects to floor-plan upload and the digital parking map, with dedicated navigation for each view.

The Parking Admin account button returns to login from Dashboard, Occupancy, Alerts, and Parking infrastructure. Driver returns to login through Sign out in Profile.

## Technology

- Kotlin
- Jetpack Compose
- Material 3 Expressive
- AndroidX Activity and Lifecycle
- Gradle with Kotlin DSL
- JUnit, AndroidX Test, and Compose UI testing

Dependency versions are maintained in `gradle/libs.versions.toml`. Material 3 Expressive APIs used by the application are experimental.

## Requirements

- Android Studio with Jetpack Compose support.
- Android SDK 37.
- JDK 25, matching `gradle/gradle-daemon-jvm.properties`.
- An Android device or emulator running Android 10 (API 29) or later.

## Getting started

1. Open the project directory in Android Studio.
2. Install Android SDK 37 through the SDK Manager if needed.
3. Sync the project with Gradle.
4. Select the `app` run configuration and an Android device.
5. Run the application.

The application ID is `com.yachiqo.spotgo`.

## Backend and Google sign-in configuration

The Android client reads `SPOTGO_API_BASE_URL` and `SPOTGO_GOOGLE_WEB_CLIENT_ID` from the process environment first, then from the repository-root `local.properties`; an environment variable takes precedence when both are set. Both values are optional and are exposed to the app through `BuildConfig`. `local.properties` is ignored by Git; do not put real configuration values in tracked files.

For local development, add these entries to the existing root `local.properties` (keep its `sdk.dir` entry). The client ID below is fictitious:

```properties
SPOTGO_API_BASE_URL=http://10.0.2.2:8080
SPOTGO_GOOGLE_WEB_CLIENT_ID=1234567890-example.apps.googleusercontent.com
```

`10.0.2.2` is the Android Emulator's host-machine address; do not use `localhost` as the backend host from the emulator. Set the API URL to the backend origin, without a Swagger or API path.

Google sign-in uses AndroidX Credential Manager and Google Identity directly. It does not require Firebase, Firebase Auth, the Google Services Gradle plugin, or `google-services.json`. The Web client ID is passed to Google as `serverClientId` to request an ID token for the backend's audience, and the account chooser allows accounts that have not previously authorized the app. If the ID is empty, Google sign-in is disabled with an explanation while email/password sign-in remains available. If the ID is configured but the backend has no `GOOGLE_CLIENT_ID`, the app displays the backend's not-configured (503) message.

To define the client ID as a Windows user environment variable in PowerShell, use a fictitious value as shown here and replace it locally with the Web client ID from Google Cloud:

```powershell
[Environment]::SetEnvironmentVariable(
  "SPOTGO_GOOGLE_WEB_CLIENT_ID",
  "1234567890-example.apps.googleusercontent.com",
  "User"
)
```

Restart Android Studio so Gradle inherits the updated environment, then stop existing Gradle daemons and sync/build the project:

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:assembleDebug
```

The backend needs its own `GOOGLE_CLIENT_ID` environment variable, set to the same Web client ID used by the Android client. In a PowerShell terminal opened in the backend repository, set it before starting the backend with that repository's normal command:

```powershell
$env:GOOGLE_CLIENT_ID = "1234567890-example.apps.googleusercontent.com"
# Start the backend using its documented command in this same terminal.
```

Firebase Cloud Messaging (FCM) is separate from Google sign-in and remains disabled unless `FCM_ENABLED=true` and all of `FCM_PROJECT_ID`, `FCM_APPLICATION_ID`, `FCM_API_KEY`, and `FCM_SENDER_ID` are configured. To enable it later, create/configure the Firebase project and Android app, provide those values through the environment or `local.properties`, and configure the backend's device-registration and push-delivery integration. No Firebase configuration is needed for Google sign-in.

## Build and test

Run the following commands from the project directory on Windows:

```powershell
# Build the debug APK
./gradlew.bat :app:assembleDebug

# Run static analysis
./gradlew.bat :app:lintDebug

# Run unit tests
./gradlew.bat :app:testDebugUnitTest

# Run instrumented UI tests
./gradlew.bat :app:connectedDebugAndroidTest
```

On macOS or Linux, use `./gradlew` instead of `./gradlew.bat`.

Instrumented tests require a connected Android device or a running emulator. The UI tests cover role navigation, tab selection, returning to the login screen, activity recreation, login and registration inputs, the administrator menu, and navigation between infrastructure, floor-plan upload, and the digital parking map.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Lint reports are generated under `app/build/reports/`.

## Project structure

```text
app/src/main/
|-- java/com/yachiqo/spotgo/
|   |-- MainActivity.kt
|   |-- presentation/
|   |   |-- SpotGoApp.kt
|   |   |-- login/
|   |   |-- driver/
|   |   |-- parking_admin/
|   |   `-- common/
|   `-- ui/theme/
`-- res/
    |-- drawable/
    |-- font/
    `-- values/
```

- `presentation/login`: login and registration interfaces and input state.
- `presentation/driver`: Driver workspace and navigation destinations.
- `presentation/parking_admin`: Parking Admin workspace and navigation destinations.
- `presentation/common`: shared interface components.
- `ui/theme`: application colors, typography, and Material theme.
- `res`: localized string resources, icons, and fonts.

Workspace layouts apply system insets, manage their navigation bars, and connect their screens through local navigation. The optional `content` callback supports composing screens within a workspace. Role, tab, and administrator view selections are preserved across activity recreation.

## Design

The interface follows the [SpotGo design in Figma](https://www.figma.com/design/sQ2XbvLctkCFweIj0w0SjQ/SpotGo-Design).

SpotGo uses a dark color palette with yellow accents and the Plus Jakarta Sans typeface. Fonts and icons are packaged with the application. Design exports and the font license are stored in `docs/design/`.

## Contributing

Keep source code, identifiers, comments, interface text, and documentation in English. Add user-facing text to string resources under `app/src/main/res/values/` and reuse the application theme and shared components.

Place screens in the appropriate role package and connect them to the corresponding workspace destination. Run the relevant build, lint, and test commands before submitting changes.
