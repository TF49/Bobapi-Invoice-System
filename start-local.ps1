[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$backendPath = Join-Path $PSScriptRoot 'backend'
$localPath = Join-Path $backendPath '.local'
$secretPath = Join-Path $localPath 'jwt-secret'

if (-not (Test-Path -LiteralPath $backendPath -PathType Container)) {
    throw "找不到后端目录：$backendPath"
}

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    if (Test-Path -LiteralPath $secretPath -PathType Leaf) {
        $env:JWT_SECRET = (Get-Content -LiteralPath $secretPath -Raw -Encoding UTF8).Trim()
    }
    else {
        New-Item -ItemType Directory -Path $localPath -Force | Out-Null

        $secretBytes = New-Object byte[] 32
        $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $random.GetBytes($secretBytes)
        }
        finally {
            $random.Dispose()
        }

        $env:JWT_SECRET = [Convert]::ToBase64String($secretBytes)
        Set-Content -LiteralPath $secretPath -Value $env:JWT_SECRET -Encoding UTF8 -NoNewline
        Write-Host "已生成本地 JWT 密钥：$secretPath"
    }
}

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    throw 'JWT_SECRET 为空，无法启动后端。'
}

$secretByteLength = [System.Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET)
if ($secretByteLength -lt 32) {
    throw 'JWT_SECRET 长度不足，至少需要 32 字节。'
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw '未找到 Maven（mvn），请先安装 Maven 并加入 PATH。'
}

Push-Location $backendPath
try {
    & mvn spring-boot:run
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
