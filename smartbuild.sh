#!/bin/bash

set -e # Hata olursa dur

# ---------------------------------------------------------
# YARDIM
# ---------------------------------------------------------
show_help() {
    echo "Kullanım: ./smartbuild.sh [SEÇENEK]"
    echo ""
    echo "Seçenekler:"
    echo "  (boş)      İmzalı release APK (secrets.sh gerekli)"
    echo "  debug      Debug APK"
    echo "  unsigned   İmzasız release APK"
    echo "  clean      Gradle ve Android cache temizliği"
    echo "  help       Bu yardım mesajını göster"
    echo ""
    exit 0
}

if [ "$1" == "help" ] || [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    show_help
fi

# ---------------------------------------------------------
# CACHE TEMİZLİĞİ
# ---------------------------------------------------------
if [ "$1" == "clean" ]; then
    echo "🧹 Cache temizliği başlatılıyor..."
    
    # Mevcut boyutları göster
    GRADLE_SIZE=$(du -sh ~/.gradle 2>/dev/null | cut -f1 || echo "0")
    ANDROID_SIZE=$(du -sh ~/.android 2>/dev/null | cut -f1 || echo "0")
    echo "📊 Mevcut boyutlar: .gradle: $GRADLE_SIZE, .android: $ANDROID_SIZE"
    
    # Gradle daemon'ları durdur
    echo "⏹️ Gradle daemon'ları durduruluyor..."
    ./gradlew --stop 2>/dev/null || true
    
    # Proje build temizliği
    echo "🗑️ Proje build temizleniyor..."
    ./gradlew clean 2>/dev/null || true
    
    # Gradle cache temizliği
    echo "🗑️ Gradle cache temizleniyor..."
    rm -rf ~/.gradle/caches/
    rm -rf ~/.gradle/daemon/
    
    # Android build cache temizliği
    echo "🗑️ Android build cache temizleniyor..."
    rm -rf ~/.android/build-cache/
    
    # Yeni boyutları göster
    GRADLE_SIZE_NEW=$(du -sh ~/.gradle 2>/dev/null | cut -f1 || echo "0")
    ANDROID_SIZE_NEW=$(du -sh ~/.android 2>/dev/null | cut -f1 || echo "0")
    echo ""
    echo "✅ Temizlik tamamlandı!"
    echo "📊 Yeni boyutlar: .gradle: $GRADLE_SIZE_NEW, .android: $ANDROID_SIZE_NEW"
    exit 0
fi

echo "🚀 Android Build Süreci Başlatılıyor..."

# ---------------------------------------------------------
# 1. KEYSTORE KONTROLÜ (Opsiyonel - Release build için)
# ---------------------------------------------------------
KEYSTORE_FILE="app/my-release-key.jks"

# secrets.sh varsa yükle (release build için)
if [ -f "secrets.sh" ]; then
    source secrets.sh
fi

# ---------------------------------------------------------
# 2. BUILD MODU SEÇİMİ
# ---------------------------------------------------------
BUILD_TYPE="${1:-release}"  # Varsayılan: release
SIGN_APK=true

# unsigned parametresi verilmişse imzasız release build yap
if [ "$BUILD_TYPE" == "unsigned" ]; then
    BUILD_TYPE="release"
    SIGN_APK=false
    echo "📦 İmzasız release build seçildi."
fi

if [ "$BUILD_TYPE" == "release" ] && [ "$SIGN_APK" == true ]; then
    # Release build için keystore kontrolü
    if [[ -z "$ANDROID_KEYSTORE_PASSWORD" || -z "$ANDROID_KEY_PASSWORD" || -z "$ANDROID_KEYSTORE_ALIAS" ]]; then
        echo "⚠️ Release için keystore şifreleri tanımlanmamış, debug build yapılacak..."
        BUILD_TYPE="debug"
    elif [ ! -f "$KEYSTORE_FILE" ]; then
        if [ -n "$ANDROID_KEYSTORE_BASE64" ]; then
            echo "🔐 Keystore dosyası Base64'ten oluşturuluyor..."
            echo "$ANDROID_KEYSTORE_BASE64" | base64 -d > "$KEYSTORE_FILE"
            echo "✅ Keystore oluşturuldu."
        else
            echo "⚠️ Keystore bulunamadı, debug build yapılacak..."
            BUILD_TYPE="debug"
        fi
    fi
    
    # key.properties oluştur (signing config için)
    if [ "$BUILD_TYPE" == "release" ]; then
        echo "📄 key.properties oluşturuluyor..."
        echo "storePassword=$ANDROID_KEYSTORE_PASSWORD" > key.properties
        echo "keyPassword=$ANDROID_KEY_PASSWORD" >> key.properties
        echo "keyAlias=$ANDROID_KEYSTORE_ALIAS" >> key.properties
        echo "storeFile=my-release-key.jks" >> key.properties
    fi
elif [ "$BUILD_TYPE" == "release" ] && [ "$SIGN_APK" == false ]; then
    # İmzasız build - key.properties dosyasını sil (varsa)
    if [ -f "key.properties" ]; then
        rm key.properties
        echo "🔓 key.properties silindi (imzasız build)."
    fi
fi

# ---------------------------------------------------------
# 3. GRADLE BUILD
# ---------------------------------------------------------
if [ "$BUILD_TYPE" == "release" ]; then
    echo "🔨 Release APK build başlatılıyor..."
    ./gradlew assembleRelease
    # İmzalı veya imzasız APK'yı kontrol et
    if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
        APK_PATH="app/build/outputs/apk/release/app-release.apk"
    else
        APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
    fi
else
    echo "🔨 Debug APK build başlatılıyor..."
    ./gradlew assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

# ---------------------------------------------------------
# 4. SONUÇ KONTROLÜ
# ---------------------------------------------------------
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo ""
    echo "✅ BUILD TAMAMLANDI!"
    echo "📦 APK: $APK_PATH"
    echo "📏 Boyut: $APK_SIZE"
    
    # İmza kontrolü (sadece imzalı release APK için)
    if [[ "$APK_PATH" == *"unsigned"* ]]; then
        echo ""
        echo "⚠️ APK imzasız. Yüklemeden önce imzalamanız gerekiyor."
    elif [ "$BUILD_TYPE" == "release" ] && command -v apksigner &> /dev/null; then
        echo ""
        echo "----------- APK İMZA DETAYLARI -----------"
        apksigner verify --print-certs "$APK_PATH" || true
    fi
else
    echo "❌ HATA: Build başarısız, APK oluşmadı."
    exit 1
fi