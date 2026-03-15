#!/bin/bash

# Keystore Oluştur ve Tek Satırlı base64 Al
# Bu script Android keystore dosyası oluşturur ve base64 formatına çevirir

# Renkli çıktı için
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Android Keystore Oluşturucu${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Kullanıcıdan bilgileri al
read -p "Keystore şifresi (ANDROID_KEYSTORE_PASSWORD): " KEYSTORE_PASSWORD
read -p "Key şifresi (ANDROID_KEY_PASSWORD): " KEY_PASSWORD
read -p "Alias adı (ANDROID_KEYSTORE_ALIAS): " KEY_ALIAS

# DN bilgileri (örnek: CN=John Doe, OU=Development, O=MyCompany, L=Istanbul, ST=Istanbul, C=TR)
echo ""
echo -e "${YELLOW}DN (Distinguished Name) Bilgileri:${NC}"
echo -e "${YELLOW}(Boş bırakılamaz, kendi bilgilerinizi girin)${NC}"
echo ""
read -p "CN (Common Name) (örn: John Doe): " CN
if [ -z "$CN" ]; then echo -e "${RED}CN boş olamaz!${NC}"; exit 1; fi
read -p "OU (Organizational Unit) (örn: Development): " OU
if [ -z "$OU" ]; then echo -e "${RED}OU boş olamaz!${NC}"; exit 1; fi
read -p "O (Organization) (örn: MyCompany): " O
if [ -z "$O" ]; then echo -e "${RED}O boş olamaz!${NC}"; exit 1; fi
read -p "L (Locality/City) (örn: Istanbul): " L
if [ -z "$L" ]; then echo -e "${RED}L boş olamaz!${NC}"; exit 1; fi
read -p "ST (State/Province) (örn: Istanbul): " ST
if [ -z "$ST" ]; then echo -e "${RED}ST boş olamaz!${NC}"; exit 1; fi
read -p "C (Country Code - 2 harf) (örn: TR): " C
if [ -z "$C" ]; then echo -e "${RED}C boş olamaz!${NC}"; exit 1; fi

# Keystore dosya adı
KEYSTORE_FILE="my-release-key.jks"
BASE64_FILE="base64_keystore.txt"

echo ""
echo -e "${YELLOW}Keystore oluşturuluyor...${NC}"

# Keystore dosyası oluştur
keytool -genkeypair \
    -keystore "$KEYSTORE_FILE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -dname "CN=$CN, OU=$OU, O=$O, L=$L, ST=$ST, C=$C" \
    -keyalg RSA -keysize 2048 -validity 10000

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Keystore başarıyla oluşturuldu: $KEYSTORE_FILE${NC}"
else
    echo -e "${RED}✗ Keystore oluşturulurken hata oluştu!${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}Base64'e encode ediliyor...${NC}"

# Keystore'u base64'e encode et (tek satırlı)
base64 -w 0 "$KEYSTORE_FILE" > "$BASE64_FILE"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Base64 dosyası oluşturuldu: $BASE64_FILE${NC}"
else
    echo -e "${RED}✗ Base64 encode işlemi başarısız!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  BUNU SECRET'A TEK SATIR OLARAK YAPIŞTIR${NC}"
echo -e "${GREEN}  Secret Adı: ANDROID_KEYSTORE${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
cat "$BASE64_FILE"
echo ""
echo ""
echo -e "${YELLOW}Not: Base64 içeriği $BASE64_FILE dosyasına da kaydedildi.${NC}"
echo -e "${YELLOW}Diğer secret'lar için kullandığınız değerler:${NC}"
echo -e "  ANDROID_KEYSTORE_PASSWORD: $KEYSTORE_PASSWORD"
echo -e "  ANDROID_KEY_PASSWORD: $KEY_PASSWORD"
echo -e "  ANDROID_KEYSTORE_ALIAS: $KEY_ALIAS"
