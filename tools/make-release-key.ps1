# Creates the RuStore publish key and puts it into the GitHub Actions secrets
# (docs/publishing.md, "Как сделать ключ публикации"). Run it yourself:
#   powershell -ExecutionPolicy Bypass -File tools\make-release-key.ps1
# The password is typed here and goes straight to keytool and gh; it is not
# printed and not written to disk. The key file lands outside the repository.

$ErrorActionPreference = 'Stop'

$dir = Join-Path $HOME 'itogo-release'
$keystore = Join-Path $dir 'itogo-release.keystore'
$alias = 'itogo'

if (Test-Path $keystore) {
    Write-Host "Ключ уже есть: $keystore. Второй раз не создаю, чтобы не потерять первый." -ForegroundColor Yellow
    exit 1
}
New-Item -ItemType Directory -Force $dir | Out-Null

$p1 = Read-Host 'Пароль ключа (не короче 6 знаков)' -AsSecureString
$p2 = Read-Host 'Ещё раз' -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p1))
$check = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p2))
if ($plain -ne $check) { Write-Host 'Пароли не совпали.' -ForegroundColor Red; exit 1 }
if ($plain.Length -lt 6) { Write-Host 'Пароль короче 6 знаков.' -ForegroundColor Red; exit 1 }

# PKCS12: the key password equals the store password, so one password serves both secrets.
$env:ITOGO_KS_PASS = $plain
try {
    keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias $alias `
        -keyalg RSA -keysize 4096 -validity 10000 `
        -storepass:env ITOGO_KS_PASS -keypass:env ITOGO_KS_PASS `
        -dname 'CN=Itogo, O=Itogo, C=RU'
    if ($LASTEXITCODE -ne 0) { throw 'keytool не создал ключ' }

    $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
    $b64 | gh secret set SIGNING_KEYSTORE_BASE64 --repo maxrubtsoff/itogo
    $plain | gh secret set SIGNING_STORE_PASSWORD --repo maxrubtsoff/itogo
    $alias | gh secret set SIGNING_KEY_ALIAS --repo maxrubtsoff/itogo
    $plain | gh secret set SIGNING_KEY_PASSWORD --repo maxrubtsoff/itogo
} finally {
    Remove-Item Env:ITOGO_KS_PASS -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "Готово. Ключ: $keystore" -ForegroundColor Green
Write-Host 'Сохрани этот файл и пароль в менеджер паролей и в офлайн-копию.'
Write-Host 'Без них обновление в RuStore больше не выпустить.'
gh secret list --repo maxrubtsoff/itogo
