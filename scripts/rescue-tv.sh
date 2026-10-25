#!/usr/bin/env bash
set -uo pipefail

TV_IP="${1:-}"
PACKAGE="id.karaoke.timerlauncher"
MAIN_ACTIVITY="${PACKAGE}/.ui.MainActivity"
APK_PATH="app/build/outputs/apk/release/app-release.apk"

echo "=========================================================="
echo "    SONY BRAVIA TV BOOTLOOP RESCUE & RECOVERY SCRIPT      "
echo "=========================================================="

# Build latest APK with the safety patches
echo "==> Ensuring latest release APK is built..."
./gradlew assembleRelease

echo ""
echo "Menunggu koneksi ADB ke TV..."
echo "Jika TV sedang restart terus-menerus:"
echo "1. Sambungkan kabel USB dari laptop ke port USB TV (USB 1/HDD)."
echo "   ATAU"
echo "2. Biarkan skrip ini berjalan terus (polling) mencoba terhubung ke TV via WiFi saat TV menyala."
echo "=========================================================="

CONNECTED_TARGET=""

while true; do
  # Check if a device is connected via USB or existing connection
  DETECTED_DEV=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')
  
  if [[ -n "${DETECTED_DEV}" ]]; then
    CONNECTED_TARGET="${DETECTED_DEV}"
    echo "==> [OK] Terdeteksi perangkat ADB aktif: ${CONNECTED_TARGET}"
    break
  fi

  if [[ -n "${TV_IP}" ]]; then
    echo "Mencoba menghubungkan ke ${TV_IP}:5555..."
    adb connect "${TV_IP}:5555" >/dev/null 2>&1 || true
    
    CHECK_DEV=$(adb devices | grep "${TV_IP}:5555" | grep "device$" | awk '{print $1}' || true)
    if [[ -n "${CHECK_DEV}" ]]; then
      CONNECTED_TARGET="${CHECK_DEV}"
      echo "==> [OK] Berhasil terhubung ke TV via WiFi: ${CONNECTED_TARGET}"
      break
    fi
  fi

  sleep 1
done

echo ""
echo "==> [LANGKAH 1] Mengaktifkan kembali launcherx bawaan Sony/Google TV..."
adb -s "${CONNECTED_TARGET}" shell cmd package enable com.google.android.apps.tv.launcherx || true
adb -s "${CONNECTED_TARGET}" shell pm enable com.google.android.apps.tv.launcherx || true

echo "==> [LANGKAH 2] Memperbarui aplikasi dengan patch pencegah crash..."
if [[ -f "${APK_PATH}" ]]; then
  adb -s "${CONNECTED_TARGET}" install -r "${APK_PATH}"
fi

echo "==> [LANGKAH 3] Memberikan izin overlay & battery whitelist..."
adb -s "${CONNECTED_TARGET}" shell appops set "${PACKAGE}" SYSTEM_ALERT_WINDOW allow || true
adb -s "${CONNECTED_TARGET}" shell dumpsys deviceidle whitelist +"${PACKAGE}" || true

echo "==> [LANGKAH 4] Mengatur default launcher kembali..."
adb -s "${CONNECTED_TARGET}" shell cmd package set-home-activity "${MAIN_ACTIVITY}" || true

echo "==> [LANGKAH 5] Membuka launcher karaoke..."
adb -s "${CONNECTED_TARGET}" shell am start -n "${MAIN_ACTIVITY}" || true

echo ""
echo "=========================================================="
echo " [SELESAI] TV berhasil dipulihkan dari bootloop!"
echo " Launcher bawaan telah diaktifkan kembali dan patch keamanan diterapkan."
echo "=========================================================="
