#!/usr/bin/env bash
# build.sh - Build the launcher APK on macOS/Linux without Android Studio.
# The first run downloads a JDK, the Android SDK tools and Gradle into ./toolchain.
# It changes nothing on your TV. The result is ./out/TvHome.apk
#
#   bash build.sh            # build
#   bash build.sh --clean    # clean, then build
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$ROOT/toolchain"
DOWNLOAD="$TOOLS/_download"
JDK="$TOOLS/jdk"
SDK="$TOOLS/android-sdk"
GRADLE_VERSION="8.9"
GRADLE_DIR="$TOOLS/gradle-$GRADLE_VERSION"
PROJECT="$ROOT/launcher"
OUT_DIR="$ROOT/out"
ANDROID_API="34"
BUILD_TOOLS="34.0.0"

CLEAN=""
[ "${1:-}" = "--clean" ] && CLEAN="clean"

step() { printf '\n==> %s\n' "$1"; }

# ---- Work out which JDK / command-line tools to fetch for this machine --------
case "$(uname -s)" in
  Darwin) OS_JDK="mac";   OS_CLT="mac" ;;
  Linux)  OS_JDK="linux"; OS_CLT="linux" ;;
  *) echo "Unsupported OS: $(uname -s). Use build.ps1 on Windows." >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64|amd64) ARCH="x64" ;;
  arm64|aarch64) ARCH="aarch64" ;;
  *) echo "Unsupported CPU: $(uname -m)" >&2; exit 1 ;;
esac

JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/$OS_JDK/$ARCH/jdk/hotspot/normal/eclipse"
CLT_URL="https://dl.google.com/android/repository/commandlinetools-${OS_CLT}-13114758_latest.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1" >&2; exit 1; }; }
need curl; need unzip

fetch() { # url name -> echoes extracted dir
  local url="$1" name="$2" zip="$DOWNLOAD/$2.zip" dir="$DOWNLOAD/$2"
  rm -rf "$dir"; mkdir -p "$dir"
  echo "    downloading $url" >&2
  curl -fL --retry 3 -o "$zip" "$url"
  echo "    extracting" >&2
  unzip -q "$zip" -d "$dir"
  rm -f "$zip"
  echo "$dir"
}

mkdir -p "$TOOLS" "$DOWNLOAD" "$OUT_DIR"

# ---- 1. JDK 17 ---------------------------------------------------------------
find_java_home() { # root -> dir containing bin/java
  local d; d="$(find "$1" -type f -name java -path '*/bin/java' 2>/dev/null | head -n1)"
  [ -n "$d" ] && dirname "$(dirname "$d")"
}
if [ ! -x "$JDK/bin/java" ]; then
  step "Fetching JDK 17"
  x="$(fetch "$JDK_URL" jdk)"
  home="$(find_java_home "$x")"
  [ -n "$home" ] || { echo "Could not find java in the JDK archive." >&2; exit 1; }
  rm -rf "$JDK"; mv "$home" "$JDK"; rm -rf "$x"
fi
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

# ---- 2. Android SDK ----------------------------------------------------------
LATEST="$SDK/cmdline-tools/latest"
SDKMANAGER="$LATEST/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  step "Fetching Android command-line tools"
  x="$(fetch "$CLT_URL" cmdline-tools)"
  mkdir -p "$(dirname "$LATEST")"
  rm -rf "$LATEST"; mv "$x/cmdline-tools" "$LATEST"; rm -rf "$x"
fi
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

if [ ! -f "$SDK/licenses/android-sdk-license" ]; then
  step "Accepting Android SDK licenses"
  yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
fi

if [ ! -f "$SDK/platforms/android-$ANDROID_API/android.jar" ] || [ ! -x "$SDK/build-tools/$BUILD_TOOLS/aapt" ]; then
  step "Installing Android platform $ANDROID_API and build-tools $BUILD_TOOLS"
  "$SDKMANAGER" --sdk_root="$SDK" "platforms;android-$ANDROID_API" "build-tools;$BUILD_TOOLS"
fi

# ---- 3. Gradle ---------------------------------------------------------------
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  step "Fetching Gradle $GRADLE_VERSION"
  x="$(fetch "$GRADLE_URL" gradle)"
  rm -rf "$GRADLE_DIR"; mv "$x/gradle-$GRADLE_VERSION" "$GRADLE_DIR"; rm -rf "$x"
fi
export GRADLE_USER_HOME="$TOOLS/gradle-home"

# ---- 4. Build ----------------------------------------------------------------
step "Building the launcher"
( cd "$PROJECT" && "$GRADLE_DIR/bin/gradle" $CLEAN assembleRelease --no-daemon --console=plain )

# ---- 5. Result ---------------------------------------------------------------
BUILT="$PROJECT/app/build/outputs/apk/release/app-release.apk"
[ -f "$BUILT" ] || { echo "The build finished but $BUILT is missing." >&2; exit 1; }
cp -f "$BUILT" "$OUT_DIR/TvHome.apk"
step "Done"
printf '    %s\n' "$OUT_DIR/TvHome.apk"
