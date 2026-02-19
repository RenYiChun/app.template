$ErrorActionPreference = "Stop"

function Build-Project {
    param (
        [string]$Path,
        [string]$Name
    )

    Write-Host "=========================================="
    Write-Host "Building $Name..."
    Write-Host "Path: $Path"
    Write-Host "=========================================="

    if (-not (Test-Path $Path)) {
        throw "Directory not found: $Path"
    }

    Push-Location $Path
    try {
        Write-Host "Running npm install..."
        # Using cmd /c to ensure npm is found and executed correctly in PowerShell
        cmd /c "npm install"
        if ($LASTEXITCODE -ne 0) { throw "npm install failed for $Name" }

        Write-Host "Running npm run build..."
        cmd /c "npm run build"
        if ($LASTEXITCODE -ne 0) { throw "npm run build failed for $Name" }
    }
    catch {
        Write-Error "Error building $Name : $_"
        throw
    }
    finally {
        Pop-Location
    }
    Write-Host "$Name built successfully!`n"
}

$root = $PSScriptRoot

# 1. Build Headless (Base Library)
Build-Project -Path "$root\template-platform-headless" -Name "template-platform-headless"

# 2. Build UI Component Library (Depends on Headless)
Build-Project -Path "$root\template-platform-ui" -Name "template-platform-ui"

# 3. Build Sample UI App (Depends on Headless and UI)
Build-Project -Path "$root\template-platform-sample-frontend" -Name "template-platform-sample-frontend"

Write-Host "All frontend projects built successfully!" -ForegroundColor Green
