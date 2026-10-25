#!/usr/bin/env bash
set -euo pipefail

TV_IP="${1:-}"
MODE="${2:-release}"
PACKAGE="id.karaoke.timerlauncher"
MAIN_ACTIVITY="${PACKAGE}/.ui.MainActivity"

if [[ -z "${TV_IP}" ]]; then
  echo "Usage: $0 <tv-ip-address> [release|debug]"
  echo "Example: $0 192.168.1.105"
  echo "Example: $0 192.168.1.105 release"
  echo "Example: $0 192.168.1.105 debug"
  exit 1
fi

LOWER_MODE=$(echo "${MODE}" | tr '[:upper:]' '[:lower:]')

case "${LOWER_MODE}" in
  debug|--debug|-d)
    BUILD_TYPE="debug"
    GRADLE_TASK="assembleDebug"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    ;;
  *)
    BUILD_TYPE="release"
    GRADLE_TASK="assembleRelease"
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
    ;;
esac

echo "==> Target TV IP   : ${TV_IP}:5555"
echo "==> Build Variant  : ${BUILD_TYPE}"
echo "==> Connecting to TV at ${TV_IP}:5555..."
adb connect "${TV_IP}:5555"

if [[ ! -f "${APK_PATH}" ]]; then
  echo "==> Building ${BUILD_TYPE} APK via ${GRADLE_TASK}..."
  ./gradlew "${GRADLE_TASK}"
fi

echo "==> Installing ${BUILD_TYPE} APK (${APK_PATH}) to TV..."
adb -s "${TV_IP}:5555" install -r "${APK_PATH}"

echo "==> Granting SYSTEM_ALERT_WINDOW (overlay permission)..."
adb -s "${TV_IP}:5555" shell appops set "${PACKAGE}" SYSTEM_ALERT_WINDOW allow

echo "==> Whitelisting from battery optimizations (device idle)..."
adb -s "${TV_IP}:5555" shell dumpsys deviceidle whitelist +"${PACKAGE}"

echo "==> Setting default launcher / home activity..."
adb -s "${TV_IP}:5555" shell cmd package set-home-activity "${MAIN_ACTIVITY}"

echo "==> Disabling Google TV default launcher (launcherx)..."
adb -s "${TV_IP}:5555" shell pm disable-user --user 0 com.google.android.apps.tv.launcherx || true

echo "==> Starting application..."
adb -s "${TV_IP}:5555" shell am start -n "${MAIN_ACTIVITY}"

echo ""
echo "=========================================================="
echo " [OK] Installation completed successfully!"
echo " Next steps:"
echo " 1. Complete on-screen Setup Wizard on the TV (Room name & Admin PIN)."
echo " 2. Scan the Admin QR code displayed on TV."
echo " 3. IMPORTANT: Disable Developer Options / ADB Debugging on TV."
echo "=========================================================="
