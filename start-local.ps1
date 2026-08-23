[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$backendPath = Join-Path $PSScriptRoot 'backend'
$localPath = Join-Path $backendPath '.local'
$secretPath = Join-Path $localPath 'jwt-secret'

if (-not (Test-Path -LiteralPath $backendPath -PathType Container)) {
    throw "Backend directory not found: $backendPath"
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
        Write-Host "Generated local JWT secret: $secretPath"
    }
}

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    throw "JWT_SECRET is empty."
}

$secretByteLength = [System.Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET)
if ($secretByteLength -lt 32) {
    throw "JWT_SECRET length is insufficient (min 32 bytes)."
}

$aiKeyPath = Join-Path $localPath 'ai-api-key'
if ([string]::IsNullOrWhiteSpace($env:AI_API_KEY)) {
    $userKey = [Environment]::GetEnvironmentVariable('AI_API_KEY', 'User')
    $machineKey = [Environment]::GetEnvironmentVariable('AI_API_KEY', 'Machine')
    if (-not [string]::IsNullOrWhiteSpace($userKey)) {
        $env:AI_API_KEY = $userKey
    } elseif (-not [string]::IsNullOrWhiteSpace($machineKey)) {
        $env:AI_API_KEY = $machineKey
    } elseif (Test-Path -LiteralPath $aiKeyPath -PathType Leaf) {
        $env:AI_API_KEY = (Get-Content -LiteralPath $aiKeyPath -Raw -Encoding UTF8).Trim()
    }
}
if (-not [string]::IsNullOrWhiteSpace($env:AI_API_KEY)) {
    $env:AI_ENABLED = 'true'
    Write-Host "AI Parse Service is enabled with key from environment."
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven (mvn) command not found in PATH."
}

Push-Location $backendPath
try {
    & mvn spring-boot:run
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
