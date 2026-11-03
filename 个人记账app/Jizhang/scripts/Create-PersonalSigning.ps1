$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$privateRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '..\私密签名'))
$keyPath = Join-Path $privateRoot '记账-release.jks'
$propertiesPath = Join-Path $projectRoot 'signing.properties'
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw '已存在签名密钥或配置，不会覆盖。请保存并继续使用原密钥。'
}
New-Item -ItemType Directory -Path $privateRoot -Force | Out-Null
$randomBytes = New-Object byte[] 24
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($randomBytes)
$keyPassword = [Convert]::ToHexString($randomBytes)
$env:JIZHANG_KEY_PASSWORD = $keyPassword
try {
    & keytool -genkeypair -keystore $keyPath -alias jizhang -keyalg RSA -keysize 3072 `
        -validity 36500 -dname 'CN=Personal Jizhang, OU=Personal, O=Personal, C=CN' `
        -storepass:env JIZHANG_KEY_PASSWORD -keypass:env JIZHANG_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw '生成签名密钥失败。' }
    # Properties.load(InputStream) reads Latin-1; escape the Chinese filename explicitly.
    $relativeKey = '../私密签名/记账-release.jks'
    $escapedKey = -join ($relativeKey.ToCharArray() | ForEach-Object {
        if ([int]$_ -gt 127) { '\u' + ('{0:x4}' -f [int]$_) } else { [string]$_ }
    })
    $content = "storeFile=$escapedKey`nstorePassword=$keyPassword`nkeyAlias=jizhang`nkeyPassword=$keyPassword`n"
    [IO.File]::WriteAllText($propertiesPath, $content, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $privateRoot '签名密钥说明.txt'),
        "应用：记账`n密钥文件：记账-release.jks`n别名：jizhang`n密码保存在旁边 Jizhang/signing.properties 中。`n请将密钥文件和 signing.properties 一起离线备份。它们不应加入源码仓库或公开分享。`n后续升级必须使用同一密钥；本脚本不会覆盖现有密钥。`n",
        [Text.UTF8Encoding]::new($false))
    Write-Output '个人签名密钥已创建；请独立备份“私密签名”目录和 signing.properties。'
} finally {
    Remove-Item Env:JIZHANG_KEY_PASSWORD -ErrorAction SilentlyContinue
    $keyPassword = $null
}
