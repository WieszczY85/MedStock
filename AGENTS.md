# AGENTS.md

## Project type

This is an Android Gradle project.

## Current CI state

The repository currently does not contain a checked-in CI pipeline configuration (`.github/workflows`, `.gitlab-ci.yml`, `bitrise.yml`, `.circleci`, `Jenkinsfile`, or `azure-pipelines*.yml`).

For Codex Cloud work, treat the bootstrap and validation routine below as the required CI-equivalent gate after every code change.

## Environment

The project requires Android SDK. In Codex Cloud, the environment must provide:

- ANDROID_HOME
- JDK 21 for the Gradle daemon toolchain
- Android SDK command-line tools
- platform-tools
- Android platform matching compileSdk
- Android build-tools

Do not assume that Android Studio is installed.

## Codex Cloud bootstrap (run once per fresh machine)

Use this block to make environment setup non-interactive (auto-accept SDK licenses/certificates) and ensure required SDK packages are present:

```bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  CMDLINE_TOOLS_URL="$(curl -fsSL https://developer.android.com/studio | grep -Eo 'https://dl.google.com/android/repository/commandlinetools-linux-[0-9]+_latest.zip' | head -n 1)"
  TMP_DIR="$(mktemp -d)"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$TMP_DIR/cmdline-tools.zip"
  unzip -q "$TMP_DIR/cmdline-tools.zip" -d "$TMP_DIR/unzipped"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$TMP_DIR/unzipped/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$TMP_DIR"
fi

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail
sdkmanager --install "platform-tools" "platforms;android-36.1" "build-tools;36.1.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## Codex Cloud verification after every fix

After each code change, rerun the environment preparation and validation below. This block is intentionally idempotent, so it is safe both on a fresh machine and on an already prepared cloud runner.

```bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  CMDLINE_TOOLS_URL="$(curl -fsSL https://developer.android.com/studio | grep -Eo 'https://dl.google.com/android/repository/commandlinetools-linux-[0-9]+_latest.zip' | head -n 1)"
  TMP_DIR="$(mktemp -d)"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$TMP_DIR/cmdline-tools.zip"
  unzip -q "$TMP_DIR/cmdline-tools.zip" -d "$TMP_DIR/unzipped"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$TMP_DIR/unzipped/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$TMP_DIR"
fi

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail
sdkmanager --install "platform-tools" "platforms;android-36.1" "build-tools;36.1.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:lintDebug
```

This matches the current project setup:

- `app/build.gradle.kts` requires Android platform `36.1` (`compileSdk` release `36` with `minorApiLevel = 1`)
- `gradle/gradle-daemon-jvm.properties` pins the Gradle daemon toolchain to JDK `21`
- `gradle/wrapper/gradle-wrapper.properties` uses Gradle `9.5.1`

## Validation commands

Use these commands when validating changes:

```bash
./gradlew --no-daemon :app:assembleDebug
```
Avoid running plain ./gradlew build unless explicitly requested, because it may trigger tasks that are not required for basic validation.

Local files

Do not commit local.properties.

If local.properties is missing in the Codex environment, create it dynamically:
```
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## i18n policy

All newly added user-facing texts must be added to Android string resources (`res/values/strings.xml` and translated variants like `res/values-pl/strings.xml`) and referenced by `R.string.*`. Do not hardcode user-visible strings in Kotlin/Java/XML layouts (except app name when explicitly required).

- Locale behavior: on startup, app should resolve language from system locale and apply only supported translations; keep fallback to default resources. Future manual language settings must override this auto mode through the same i18n layer (AppCompatDelegate locales).

## File creation and structure policy
All created files should be segregated according to their purpose, e.g., interface-related files in the ui directory, download classes in download, parsing in custom, etc.