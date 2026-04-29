################################################################################
## build_and_copy.ps1 (v2)
## Автоматизация сборки проекта и копирования артефакта в папку модов PrismLauncher
################################################################################
param(
  [string]$repoRoot = "D:\\projects\\Phaze Client",  # корень проекта
  [string]$instanceName = "",                          # имя версии/инстанса PrismLauncher (например '1.21.4')
  [string]$modsDest = ""                                 # путь к папке модов; если пусто, попытаемся auto-detect
)

$logFile = Join-Path $repoRoot "build_and_copy.log"

function Write-Log {
  param([string]$line)
  $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
  "$ts`t$line" | Out-File -FilePath $logFile -Append -Encoding UTF8
}

Write-Log "Starting build and copy process"

# Auto-detect a Java 21+ JDK and configure JAVA_HOME for Gradle/Maven builds
$java21Paths = @(
  "C:\Program Files\Java\jdk-21",
  "C:\Program Files\Java\openjdk-21",
  "C:\Program Files\OpenJDK\jdk-21",
  "C:\Program Files\AdoptOpenJDK\jdk-21",
  "C:\Program Files\Zulu\zulu-21\jdk-21"
)
foreach ($p in $java21Paths) {
  if (Test-Path $p) {
    $env:JAVA_HOME = $p
    $env:Path = "$(Join-Path $p 'bin');$env:Path"
    Write-Log "Detected Java 21+ at $p; JAVA_HOME set."
    break
  }
}
if (-not $env:JAVA_HOME) {
  Write-Log "No Java 21+ JDK detected in common locations. Please install JDK 21+ and set JAVA_HOME manually if build fails."
}

if (-Not (Test-Path $repoRoot)) {
  Write-Log "Repo root not found: $repoRoot"
  throw "Repo root not found"
}

## Определение билд-системы (gradle/maven)
$gradleBat = Join-Path $repoRoot "gradlew.bat"
$gradleSh  = Join-Path $repoRoot "gradlew"
 $buildDir = $null
 $gradleCmd = $null
if (Test-Path $gradleBat) { $gradleCmd = $gradleBat }
elseif (Test-Path $gradleSh) { $gradleCmd = $gradleSh }

if ($gradleCmd) {
  Write-Log "Using Gradle wrapper at $gradleCmd"
  & $gradleCmd -version 2>$null | Out-Null
  Write-Log "Running Gradle build..."
  & $gradleCmd clean assemble 2>&1 | Tee-Object -FilePath $logFile -Append
  $buildDir = Join-Path $repoRoot "build\\libs"
} elseif (Test-Path (Join-Path $repoRoot "pom.xml")) {
  Write-Log "Gradle wrapper not found. Using Maven..."
  if (-Not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Log "Maven not found in PATH"
    throw "Maven required"
  }
  & mvn -q -DskipTests package 2>&1 | Tee-Object -FilePath $logFile -Append
  $buildDir = Join-Path $repoRoot "target"
} else {
  Write-Log "No build system detected (gradle/pom.xml)."
  throw "Cannot build: no gradle wrapper or pom.xml"
}

if (-not (Test-Path $buildDir)) {
  Write-Log "Build directory not found: $buildDir"
  throw "Build output directory missing"
}

## Поиск артефакта-jar
$jar = Get-ChildItem -Path $buildDir -Filter "*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
  Write-Log "No jar found in $buildDir"
  throw "Build did not produce a jar"
}
Write-Log "Found jar: $($jar.FullName)"

## Разрешение destinaton модов
if ([string]::IsNullOrWhiteSpace($modsDest)) {
  $prismBase = Join-Path $env:APPDATA "PrismLauncher"
  if (Test-Path $prismBase) {
    $instancesRoot = Get-ChildItem -Directory -Path (Join-Path $prismBase "instances") -ErrorAction SilentlyContinue
    if ($instancesRoot) {
      $sel = $null
      if (-not [string]::IsNullOrWhiteSpace($instanceName)) {
        $sel = $instancesRoot | Where-Object { $_.Name -like "*$instanceName*" } | Select-Object -First 1
      }
      if (-not $sel) {
        $sel = $instancesRoot | Where-Object { $_.Name -eq "1.21.4" } | Select-Object -First 1
        if (-not $sel) { $sel = $instancesRoot | Select-Object -First 1 }
      }
      if ($sel) {
        $candidate = Join-Path $sel.FullName "minecraft\\mods"
        if (Test-Path $candidate) {
          $modsDest = $candidate
        }
      }
    }
  }
}
if ([string]::IsNullOrWhiteSpace($modsDest)) {
  Write-Log "Mods destination not provided and auto-detection failed."
  throw "Mods destination missing"
}
Write-Log "Mods destination resolved to: $modsDest"

## Создать директорию, если нужно
if (-not (Test-Path $modsDest)) {
  New-Item -ItemType Directory -Path $modsDest -Force | Out-Null
  Write-Log "Created mods dest directory: $modsDest"
}

## Копирование артефакта в папку модов
$destPath = Join-Path $modsDest $jar.Name
Copy-Item -Path $jar.FullName -Destination $destPath -Force
Write-Log "Copied to $destPath"

Write-Log "Done"
