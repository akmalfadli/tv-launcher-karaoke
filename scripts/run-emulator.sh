#!/usr/bin/env bash
# ==============================================================================
# Skrip Menjalankan Aplikasi di Emulator Android TV
# ==============================================================================

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

EMULATOR_BIN="$HOME/Library/Android/sdk/emulator/emulator"
ADB_BIN="$HOME/Library/Android/sdk/platform-tools/adb"

if ! command -v adb &> /dev/null; then
    if [ -f "$ADB_BIN" ]; then
        export PATH="$(dirname "$ADB_BIN"):$PATH"
    fi
fi

echo "========================================================"
echo " 📺 Menyiapkan Emulator Android TV & Menjalankan App"
echo "========================================================"

# 1. Cek apakah ada emulator yang sedang berjalan
RUNNING_DEVICE=$(adb devices | grep "emulator-" | head -n 1 | awk '{print $1}')

if [ -z "$RUNNING_DEVICE" ]; then
    echo "⚠️  Tidak ada emulator yang aktif."
    echo "🔍 Mencari AVD TV yang tersedia..."
    
    if [ -f "$EMULATOR_BIN" ]; then
        TV_AVD=$("$EMULATOR_BIN" -list-avds | grep -iE "tv|television" | head -n 1)
        if [ -z "$TV_AVD" ]; then
            TV_AVD=$("$EMULATOR_BIN" -list-avds | head -n 1)
        fi
        
        if [ -n "$TV_AVD" ]; then
            echo "🚀 Menjalankan emulator: $TV_AVD..."
            "$EMULATOR_BIN" -avd "$TV_AVD" -no-snapshot-load &
            echo "⏳ Menunggu emulator selesai boot..."
            adb wait-for-device
            while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
                sleep 2
            done
            RUNNING_DEVICE=$(adb devices | grep "emulator-" | head -n 1 | awk '{print $1}')
            echo "✅ Emulator siap: $RUNNING_DEVICE"
        else
            echo "❌ Tidak ditemukan AVD emulator. Silakan buat AVD Android TV terlebih dahulu di Android Studio."
            exit 1
        fi
    else
        echo "❌ Biner emulator tidak ditemukan di $EMULATOR_BIN"
        exit 1
    fi
else
    echo "✅ Menggunakan emulator yang sedang berjalan: $RUNNING_DEVICE"
fi

# 2. Kompilasi APK Debug
echo "🔨 Mengompilasi APK debug..."
./gradlew assembleDebug -q

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK tidak ditemukan di $APK_PATH"
    exit 1
fi

# 3. Install APK ke Emulator
echo "📲 Memasang APK ke emulator ($RUNNING_DEVICE)..."
adb -s "$RUNNING_DEVICE" install -r "$APK_PATH"

# 4. Berikan Izin Overlay dan Whitelist Baterai
echo "🔓 Memberikan izin overlay dan mengabaikan optimasi baterai..."
adb -s "$RUNNING_DEVICE" shell appops set id.karaoke.timerlauncher SYSTEM_ALERT_WINDOW allow
adb -s "$RUNNING_DEVICE" shell dumpsys deviceidle whitelist +id.karaoke.timerlauncher

# 5. Siapkan Port Forwarding agar Port 8080 diakses dari Laptop / HP
echo "🌐 Menghubungkan port 8080 (adb forward)..."
adb -s "$RUNNING_DEVICE" forward tcp:8080 tcp:8080

# 6. Jalankan MainActivity
echo "🚀 Membuka aplikasi Karaoke Launcher di TV..."
adb -s "$RUNNING_DEVICE" shell am start -n id.karaoke.timerlauncher/.ui.MainActivity

# Dapatkan IP Laptop
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")

echo ""
echo "========================================================"
echo " 🎉 APLIKASI BERHASIL BERJALAN DI EMULATOR TV!"
echo "========================================================"
echo "🎮 Kontrol Remote Emulator di Layar Komputer:"
echo "   - D-Pad Panah   : Tombol Panah Keyboard (Up / Down / Left / Right)"
echo "   - Tombol Pilih  : Enter (DPAD_CENTER)"
echo "   - Tombol Kembali: Backspace / Esc (BACK)"
echo ""
echo "📱 Mengakses Remote Web Operator:"
echo "   - Dari Browser Laptop : http://localhost:8080"
echo "   - Dari HP Sungguhan   : http://$LOCAL_IP:8080 (HP & Laptop harus di WiFi yang sama)"
echo "========================================================"
