# ops/start-dev.ps1
param(
    [switch]$NoFrontend
)

$baseDir = "c:\VibeCode\EvenBetterHelp"
$gradle = "C:\Users\Midrasha Ezrachit 30\.gradle\wrapper\dists\gradle-9.5.1-bin\iq79hdu3mqx29lgffhp8bfmx\gradle-9.5.1\bin\gradle.bat"
$node = "C:\Users\Midrasha Ezrachit 30\AppData\Local\Temp\opencode\node-v22.23.2-win-x64\node.exe"
if (!(Test-Path $node)) {
    $node = "C:\Users\Midrasha Ezrachit 30\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
}
$java = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin\java.exe"

$nodeDir = Split-Path $node
$javaDir = Split-Path $java
$env:PATH = "$nodeDir;$javaDir;$env:PATH"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "  Starting EvenBetterHelp Stack on Windows..." -ForegroundColor Cyan
Write-Host "========================================================`n" -ForegroundColor Cyan

# 1. Start PostgreSQL
Write-Host "[1/4] Starting PostgreSQL on port 5432..." -ForegroundColor Yellow
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle = 'EvenBetterHelp - PostgreSQL (5432)'; cd '$baseDir'; & '$gradle' -p backend runPostgres"

Start-Sleep -Seconds 5

# 2. Start Ktor Backend
Write-Host "[2/4] Starting Ktor Backend on port 8080..." -ForegroundColor Yellow
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle = 'EvenBetterHelp - Backend (8080)'; cd '$baseDir'; Get-Content .env | ForEach-Object { if (`$_ -match '^\s*([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable(`$matches[1].Trim(), `$matches[2].Trim(), 'Process') } }; & '$gradle' -p backend run"

Start-Sleep -Seconds 4

# 3. Start Cobalt Gateway
Write-Host "[3/4] Starting Cobalt WhatsApp Gateway on port 8090..." -ForegroundColor Yellow
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle = 'EvenBetterHelp - WhatsApp Gateway (8090)'; cd '$baseDir'; Get-Content .env | ForEach-Object { if (`$_ -match '^\s*([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable(`$matches[1].Trim(), `$matches[2].Trim(), 'Process') } }; & '$gradle' -p gateway run"

Start-Sleep -Seconds 3

# 4. Start Next.js Frontend
if (!$NoFrontend) {
    $fe = Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue
    if (!$fe) {
        Write-Host "[4/4] Starting Next.js frontend on port 3000..." -ForegroundColor Yellow
        Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle = 'EvenBetterHelp - Frontend (3000)'; cd '$baseDir'; & '$node' frontend/node_modules/next/dist/bin/next dev -p 3000"
    } else {
        Write-Host "[4/4] Next.js frontend is already running on port 3000." -ForegroundColor Green
    }
}

# Generate Dev Token
$env:JWT_SECRET = "helpdesk-super-secret-jwt-key-2026-very-secure-random"
$env:JWT_ISSUER = "helpdesk-local"
$token = & "$node" "$baseDir\ops\issue-dev-token.mjs"

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host "  EVENBETTERHELP IS READY!" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host "`nWeb Application URL: http://localhost:3000" -ForegroundColor Cyan
Write-Host "`nYour 30-minute Sign-In Token:" -ForegroundColor Cyan
Write-Host "$token" -ForegroundColor White
Write-Host "`n(Copy the token above and paste it into the sign-in modal)`n" -ForegroundColor DarkGray

try {
    Start-Process "http://localhost:3000"
} catch {}
