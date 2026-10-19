$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot)
try {
    & .\gradlew.bat :core:test :app:assembleDebug :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build or checks failed' }
    New-Item -ItemType Directory -Force dist,evidence | Out-Null
    $version = [regex]::Match((Get-Content app\build.gradle -Raw), "versionName\s+'([^']+)'").Groups[1].Value
    if (!$version) { throw 'Missing versionName' }
    $artifact = "dist\Ghost-Magic-Android-$version.apk"
    Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination $artifact
    (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash | Set-Content "$artifact.sha256" -Encoding ascii
    Write-Output (Get-Item -LiteralPath $artifact).FullName
} finally { Pop-Location }
