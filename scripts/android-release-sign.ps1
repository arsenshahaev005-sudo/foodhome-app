# Owner-run Windows helper. Never put signing material in the repository.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Initialize', 'Sign')][string]$Action,
    [Parameter(Mandatory)][string]$KeyDirectory,
    [Parameter(Mandatory)][string]$JavaHome,
    [string]$BuildTools,
    [string]$UnsignedApk,
    [string]$SignedApk
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:OS -ne 'Windows_NT') { throw 'This helper uses Windows user-bound DPAPI.' }
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$keyRoot = [IO.Path]::GetFullPath($KeyDirectory)
if ($keyRoot.Equals($repo, [StringComparison]::OrdinalIgnoreCase) -or
    $keyRoot.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Signing material must be outside the repository.'
}
$keystore = Join-Path $keyRoot 'foodhome-android-release.p12'
$passwordFile = Join-Path $keyRoot 'password.dpapi'
$alias = 'foodhome-release'
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
if (!(Test-Path -LiteralPath $keytool)) { throw 'JDK keytool not found.' }
if (Test-Path Env:FOODHOME_SIGNING_PASSWORD) { throw 'Refusing to replace an existing password environment variable.' }
$oldJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    if ($Action -eq 'Initialize') {
        if (Test-Path -LiteralPath $keyRoot) { throw 'Key directory already exists. Never replace or regenerate an existing release key.' }
        New-Item -ItemType Directory -Path $keyRoot | Out-Null
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl = Get-Acl -LiteralPath $keyRoot
        $acl.SetAccessRuleProtection($true, $false)
        foreach ($sid in @($identity, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
            $rule = [Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
            $acl.AddAccessRule($rule)
        }
        Set-Acl -LiteralPath $keyRoot -AclObject $acl
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $env:FOODHOME_SIGNING_PASSWORD = [Convert]::ToBase64String($bytes)
        $secure = ConvertTo-SecureString $env:FOODHOME_SIGNING_PASSWORD -AsPlainText -Force
        # Persist before key generation so an interrupted run never loses its password.
        ConvertFrom-SecureString $secure | Set-Content -LiteralPath $passwordFile -Encoding ASCII
        & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias $alias -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname 'CN=FoodHome Android Release, O=FoodHome' -storepass:env FOODHOME_SIGNING_PASSWORD -keypass:env FOODHOME_SIGNING_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed; retained partial directory for manual recovery.' }
        & $keytool -exportcert -rfc -keystore $keystore -alias $alias -storepass:env FOODHOME_SIGNING_PASSWORD -file (Join-Path $keyRoot 'certificate.pem')
        if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
        Write-Output 'Permanent release key created. Back up the encrypted keystore AND a portable password in an owner-controlled password manager. DPAPI alone is not a portable backup.'
    } else {
        if (!(Test-Path -LiteralPath $keystore) -or !(Test-Path -LiteralPath $passwordFile)) { throw 'Existing owner key/password not found.' }
        if (!$UnsignedApk -or !$SignedApk -or !$BuildTools) { throw 'Sign requires BuildTools, UnsignedApk and SignedApk.' }
        if (Test-Path -LiteralPath $SignedApk) { throw 'Refusing to overwrite an existing signed artifact.' }
        $secure = Get-Content -LiteralPath $passwordFile -Raw | ConvertTo-SecureString
        $env:FOODHOME_SIGNING_PASSWORD = [Net.NetworkCredential]::new('', $secure).Password
        $aapt = Join-Path $BuildTools 'aapt.exe'
        $badging = & $aapt dump badging $UnsignedApk
        if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch "package: name='market.foodhome.app'" -or ($badging -join "`n") -match 'application-debuggable') {
            throw 'Input must be a non-debuggable FoodHome release APK.'
        }
        & (Join-Path $BuildTools 'zipalign.exe') -c -P 16 4 $UnsignedApk
        if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed.' }
        & (Join-Path $BuildTools 'apksigner.bat') sign --ks $keystore --ks-key-alias $alias --ks-pass env:FOODHOME_SIGNING_PASSWORD --key-pass env:FOODHOME_SIGNING_PASSWORD --v4-signing-enabled false --out $SignedApk $UnsignedApk
        if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
        & (Join-Path $BuildTools 'apksigner.bat') verify --verbose --print-certs $SignedApk
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
        Get-FileHash -LiteralPath $SignedApk -Algorithm SHA256
    }
} finally {
    Remove-Item Env:FOODHOME_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    $env:JAVA_HOME = $oldJavaHome
}
