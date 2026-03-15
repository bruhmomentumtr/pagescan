# Keystore Oluştur ve Tek Satırlı base64 Al
# Bu script Android keystore dosyası oluşturur ve base64 formatına çevirir
# Windows PowerShell - Ekstra araç gerektirmez

$ErrorActionPreference = "Stop"

# Renkli çıktı için fonksiyonlar
function Write-ColorText($text, $color) {
    Write-Host $text -ForegroundColor $color
}

Write-ColorText "========================================" Green
Write-ColorText "  Android Keystore Oluşturucu" Green
Write-ColorText "========================================" Green
Write-Host ""

# Kullanıcıdan bilgileri al
$KEYSTORE_PASSWORD = Read-Host "Keystore şifresi (ANDROID_KEYSTORE_PASSWORD)"
$KEY_PASSWORD = Read-Host "Key şifresi (ANDROID_KEY_PASSWORD)"
$KEY_ALIAS = Read-Host "Alias adı (ANDROID_KEYSTORE_ALIAS)"

# DN bilgileri
Write-Host ""
Write-ColorText "DN (Distinguished Name) Bilgileri:" Yellow
Write-ColorText "(Boş bırakılamaz, kendi bilgilerinizi girin)" Yellow
Write-Host ""

$CN = Read-Host "CN (Common Name) (örn: John Doe)"
if ([string]::IsNullOrWhiteSpace($CN)) { Write-ColorText "CN boş olamaz!" Red; exit 1 }

$OU = Read-Host "OU (Organizational Unit) (örn: Development)"
if ([string]::IsNullOrWhiteSpace($OU)) { Write-ColorText "OU boş olamaz!" Red; exit 1 }

$O = Read-Host "O (Organization) (örn: MyCompany)"
if ([string]::IsNullOrWhiteSpace($O)) { Write-ColorText "O boş olamaz!" Red; exit 1 }

$L = Read-Host "L (Locality/City) (örn: Istanbul)"
if ([string]::IsNullOrWhiteSpace($L)) { Write-ColorText "L boş olamaz!" Red; exit 1 }

$ST = Read-Host "ST (State/Province) (örn: Istanbul)"
if ([string]::IsNullOrWhiteSpace($ST)) { Write-ColorText "ST boş olamaz!" Red; exit 1 }

$C = Read-Host "C (Country Code - 2 harf) (örn: TR)"
if ([string]::IsNullOrWhiteSpace($C)) { Write-ColorText "C boş olamaz!" Red; exit 1 }

# Keystore dosya adı
$KEYSTORE_FILE = "my-release-key.jks"
$BASE64_FILE = "base64_keystore.txt"

Write-Host ""
Write-ColorText "Keystore oluşturuluyor..." Yellow

# Keystore dosyası oluştur (keytool Java ile birlikte gelir)
$dname = "CN=$CN, OU=$OU, O=$O, L=$L, ST=$ST, C=$C"

try {
    & keytool -genkeypair `
        -keystore $KEYSTORE_FILE `
        -storepass $KEYSTORE_PASSWORD `
        -keypass $KEY_PASSWORD `
        -alias $KEY_ALIAS `
        -dname $dname `
        -keyalg RSA -keysize 2048 -validity 10000

    if ($LASTEXITCODE -eq 0) {
        Write-ColorText "✓ Keystore başarıyla oluşturuldu: $KEYSTORE_FILE" Green
    } else {
        throw "keytool hata döndürdü"
    }
} catch {
    Write-ColorText "✗ Keystore oluşturulurken hata oluştu: $_" Red
    exit 1
}

Write-Host ""
Write-ColorText "Base64'e encode ediliyor..." Yellow

# Keystore'u base64'e encode et (tek satırlı) - Built-in .NET kullanır
try {
    $bytes = [System.IO.File]::ReadAllBytes($KEYSTORE_FILE)
    $base64 = [System.Convert]::ToBase64String($bytes)
    [System.IO.File]::WriteAllText($BASE64_FILE, $base64)
    
    Write-ColorText "✓ Base64 dosyası oluşturuldu: $BASE64_FILE" Green
} catch {
    Write-ColorText "✗ Base64 encode işlemi başarısız: $_" Red
    exit 1
}

Write-Host ""
Write-ColorText "========================================" Green
Write-ColorText "  BUNU SECRET'A TEK SATIR OLARAK YAPIŞTIR" Green
Write-ColorText "  Secret Adı: ANDROID_KEYSTORE_BASE64" Green
Write-ColorText "========================================" Green
Write-Host ""
Write-Host $base64
Write-Host ""
Write-Host ""
Write-ColorText "Not: Base64 içeriği $BASE64_FILE dosyasına da kaydedildi." Yellow
Write-ColorText "Diğer secret'lar için kullandığınız değerler:" Yellow
Write-Host "  ANDROID_KEYSTORE_PASSWORD: $KEYSTORE_PASSWORD"
Write-Host "  ANDROID_KEY_PASSWORD: $KEY_PASSWORD"
Write-Host "  ANDROID_KEYSTORE_ALIAS: $KEY_ALIAS"
Write-Host ""
Write-ColorText "secrets.ps1 dosyası oluşturmak ister misiniz? (E/H): " Yellow -NoNewline
$createSecrets = Read-Host
if ($createSecrets -eq "E" -or $createSecrets -eq "e") {
    $secretsContent = @"
# Android Signing Secrets
# Bu dosyayı .gitignore'a ekleyin!
`$env:ANDROID_KEYSTORE_PASSWORD = "$KEYSTORE_PASSWORD"
`$env:ANDROID_KEY_PASSWORD = "$KEY_PASSWORD"
`$env:ANDROID_KEYSTORE_ALIAS = "$KEY_ALIAS"
`$env:ANDROID_KEYSTORE_BASE64 = "$base64"
"@
    [System.IO.File]::WriteAllText("secrets.ps1", $secretsContent)
    Write-ColorText "✓ secrets.ps1 oluşturuldu!" Green
}
