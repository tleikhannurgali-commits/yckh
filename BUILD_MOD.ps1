$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$log = Join-Path $PSScriptRoot 'build-log.txt'
$tools = Join-Path $PSScriptRoot '.build-tools'
$dist = Join-Path $tools 'gradle-9.5.0\bin\gradle.bat'
$zip = Join-Path $tools 'gradle-9.5.0-bin.zip'
$transcriptStarted = $false

try {
    Start-Transcript -Path $log -Force | Out-Null
    $transcriptStarted = $true
    Write-Host '[1/4] Checking Java 21 ...'
    if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) {
        throw 'Java is missing. Install JDK 21, then rerun BUILD_MOD.cmd.'
    }
    $javaVersion = (& cmd.exe /c 'java -version 2>&1' | Out-String)
    Write-Host $javaVersion
    if ($javaVersion -notmatch 'version\s+"21(?:\.|\")') {
        throw 'The selected Java is not version 21. Please install/select JDK 21.'
    }
    if (-not (Get-Command javac.exe -ErrorAction SilentlyContinue)) {
        throw 'JDK compiler javac.exe is missing. Install JDK 21 (not JRE).'
    }

    Write-Host '[2/4] Checking Gradle 9.5.0 ...'
    if (-not (Test-Path -LiteralPath $dist)) {
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $url = 'https://services.gradle.org/distributions/gradle-9.5.0-bin.zip'
        Write-Host "Downloading Gradle from $url (internet required)..."
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -MaximumRedirection 10
        } catch {
            Write-Host "PowerShell download failed: $($_.Exception.Message)"
            Write-Host 'Trying curl.exe fallback ...'
            if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) { throw }
            & curl.exe -f -L --retry 2 --connect-timeout 30 -o $zip $url
            if ($LASTEXITCODE -ne 0) { throw "Gradle download failed with curl exit code $LASTEXITCODE" }
        }
        Write-Host 'Unpacking Gradle ...'
        Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
        if (-not (Test-Path -LiteralPath $dist)) { throw 'Gradle archive did not unpack correctly.' }
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
    }

    Write-Host '[3/4] Building the Forge mod (first launch downloads Forge/Minecraft) ...'
    $fullLog = Join-Path $PSScriptRoot 'gradle-full.log'
    # PowerShell 5.1 sometimes treats native stderr as error records.
    # Temporarily allow them so we can capture ALL Gradle output with Tee-Object.
    $savedErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $dist build --no-daemon --stacktrace --console=plain 2>&1 | Tee-Object -FilePath $fullLog
        $buildExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedErrorAction
    }
    if ($buildExitCode -ne 0) {
        throw "Gradle build failed with exit code $buildExitCode. Full output: gradle-full.log"
    }

    $jar = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'build\libs') -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '(?i)(-sources|-javadoc)\.jar$' } |
        Select-Object -First 1
    if (-not $jar) { throw 'Build finished but no JAR was found in build\libs.' }

    Write-Host '[4/4] SUCCESS!'
    Write-Host "Mod JAR: $($jar.FullName)"
    Start-Process explorer.exe -ArgumentList ('"'+$jar.DirectoryName+'"')
    exit 0
}
catch {
    Write-Host ''
    Write-Host '******** BUILD ERROR ********' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host 'Detailed report saved in build-log.txt'
    exit 1
}
finally {
    if ($transcriptStarted) { Stop-Transcript | Out-Null }
}
