# Requirements:
# - git
# - Java 17
# - Python 3.7+
# - DDS instance with pixi support setup (or Python 3.7+)
# - flatc v25.2+ (FlatBuffers compiler)

param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$Branch,
    [Parameter()]
    [string]$Repository = "https://github.com/SooratiLab/haris.git"
)

$ErrorActionPreference = "Stop"

Write-Host "Setting up Haris from branch: $Branch"
Write-Host "Repository: $Repository"
Write-Host ""

# Function to check if remote branch exists
function Test-RemoteBranch {
    param($Repo, $BranchName)
    
    try {
        $RemoteRefs = git ls-remote --heads $Repo
        $BranchExists = $RemoteRefs | Where-Object { $_ -match "refs/heads/$BranchName$" }
        return $BranchExists -ne $null
    } catch {
        Write-Host "Error checking remote repository: $_"
        return $false
    }
}

# -------------------------------
# Step 0: Check for package managers
# -------------------------------
$HasChocolatey = Get-Command choco -ErrorAction SilentlyContinue
$HasWinget = Get-Command winget -ErrorAction SilentlyContinue
$HasScoop = Get-Command scoop -ErrorAction SilentlyContinue

if ($HasChocolatey) {
    $PkgManager = "chocolatey"
    Write-Host "Using package manager: Chocolatey"
} elseif ($HasWinget) {
    $PkgManager = "winget"
    Write-Host "Using package manager: Windows Package Manager (winget)"
} elseif ($HasScoop) {
    $PkgManager = "scoop"
    Write-Host "Using package manager: Scoop"
} else {
    Write-Host "No package manager found. Installing Chocolatey..."
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    $PkgManager = "chocolatey"
    Write-Host "Chocolatey installed successfully"
}

# -------------------------------
# Step 1: Install dependencies
# -------------------------------

# Git
Write-Host "Checking for Git..."
if (Get-Command git -ErrorAction SilentlyContinue) {
    $GitVersion = git --version
    Write-Host "Found Git: $GitVersion"
} else {
    Write-Host "Git not found. Installing..."
    switch ($PkgManager) {
        "chocolatey" { choco install git -y }
        "winget" { winget install --id Git.Git -e --source winget }
        "scoop" { scoop install git }
    }
    # Refresh PATH
    $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
}

# Verify branch exists before proceeding
Write-Host "Checking if branch '$Branch' exists in repository..."
if (-not (Test-RemoteBranch -Repo $Repository -BranchName $Branch)) {
    Write-Host "ERROR: Branch '$Branch' does not exist in repository '$Repository'" -ForegroundColor Red
    Write-Host "Available branches:" -ForegroundColor Yellow
    try {
        $RemoteRefs = git ls-remote --heads $Repository
        $Branches = $RemoteRefs | ForEach-Object { 
            if ($_ -match "refs/heads/(.+)$") { 
                "  - $($matches[1])" 
            }
        }
        $Branches | Sort-Object | ForEach-Object { Write-Host $_ -ForegroundColor Yellow }
    } catch {
        Write-Host "Could not list available branches" -ForegroundColor Red
    }
    exit 1
}
Write-Host "Branch '$Branch' found in repository." -ForegroundColor Green

# Java
Write-Host "Checking for Java..."
$JavaInstalled = $false
try {
    $JavaVersion = java -version 2>&1
    if ($JavaVersion -match 'version "(\d+)') {
        $JavaMajorVersion = [int]$matches[1]
        if ($JavaMajorVersion -ge 17) {
            Write-Host "Found Java version $JavaMajorVersion (compatible)"
            $JavaInstalled = $true
        } else {
            Write-Host "Found Java version $JavaMajorVersion but need version 17+. Installing..."
        }
    }
} catch {
    Write-Host "Java not found. Installing OpenJDK 17..."
}

if (-not $JavaInstalled) {
    switch ($PkgManager) {
        "chocolatey" { choco install openjdk17 -y }
        "winget" { winget install --id Microsoft.OpenJDK.17 -e --source winget }
        "scoop" { 
            scoop bucket add java
            scoop install openjdk17
        }
    }
    # Refresh PATH
    $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
}

# Python
Write-Host "Checking for Python..."
$PythonInstalled = $false
try {
    $PythonVersion = python --version 2>&1
    if ($PythonVersion -match 'Python (\d+)\.(\d+)') {
        $PyMajor = [int]$matches[1]
        $PyMinor = [int]$matches[2]
        if ($PyMajor -eq 3 -and $PyMinor -ge 7) {
            Write-Host "Found Python $($matches[0])"
            $PythonInstalled = $true
        } else {
            Write-Host "Python version < 3.7 detected. Installing newer Python..."
        }
    }
} catch {
    Write-Host "Python not found. Installing Python..."
}

if (-not $PythonInstalled) {
    switch ($PkgManager) {
        "chocolatey" { choco install python3 -y }
        "winget" { winget install --id Python.Python.3.12 -e --source winget }
        "scoop" { scoop install python }
    }
    # Refresh PATH
    $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
}

# Python pip (usually comes with Python on Windows)
Write-Host "Checking for pip..."
try {
    $PipVersion = python -m pip --version
    Write-Host "Found pip: $PipVersion"
} catch {
    Write-Host "pip not found. Installing..."
    python -m ensurepip --upgrade
}

# FlatBuffers compiler (flatc)
Write-Host "Checking for flatc..."
$FlatcInstalled = $false
try {
    $FlatcOutput = flatc --version 2>&1
    if ($FlatcOutput -match '(\d+)\.(\d+)\.(\d+)') {
        $FlatcMajor = [int]$matches[1]
        $FlatcMinor = [int]$matches[2]
        if ($FlatcMajor -gt 25 -or ($FlatcMajor -eq 25 -and $FlatcMinor -ge 2)) {
            Write-Host "Found flatc v$($matches[0]) (meets requirement v25.2+)"
            $FlatcInstalled = $true
        } else {
            Write-Host "Found flatc v$($matches[0]) but need v25.2+. Installing newer version..."
        }
    }
} catch {
    Write-Host "flatc not found. Installing v25.2.10..."
}

if (-not $FlatcInstalled) {
    $FlatcVersion = "25.2.10"
    $FlatcUrl = "https://github.com/google/flatbuffers/releases/download/v$FlatcVersion/Windows.flatc.binary.zip"
    $TempDir = New-TemporaryFile | ForEach-Object { Remove-Item $_; New-Item -ItemType Directory -Path $_ }
    
    Write-Host "Downloading flatc v$FlatcVersion from $FlatcUrl..."
    $ZipPath = Join-Path $TempDir "flatc.zip"
    Invoke-WebRequest -Uri $FlatcUrl -OutFile $ZipPath
    
    # Extract
    Expand-Archive -Path $ZipPath -DestinationPath $TempDir
    
    # Find flatc.exe and install to a location in PATH
    $FlatcExe = Get-ChildItem -Path $TempDir -Name "flatc.exe" -Recurse | Select-Object -First 1
    if ($FlatcExe) {
        $FlatcSource = Join-Path $TempDir $FlatcExe
        $InstallDir = "C:\Program Files\flatc"
        New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
        Copy-Item $FlatcSource -Destination $InstallDir
        
        # Add to PATH if not already there
        $CurrentPath = [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
        if ($CurrentPath -notlike "*$InstallDir*") {
            [System.Environment]::SetEnvironmentVariable("PATH", "$CurrentPath;$InstallDir", "Machine")
            $env:PATH += ";$InstallDir"
        }
        
        Write-Host "flatc installed to $InstallDir"
    } else {
        Write-Host "flatc.exe not found in downloaded archive!"
        exit 1
    }
    
    # Cleanup
    Remove-Item -Path $TempDir -Recurse -Force
    
    # Verify installation
    try {
        $NewFlatcVersion = flatc --version 2>&1
        Write-Host "flatc installed successfully"
    } catch {
        Write-Host "Failed to install flatc"
        exit 1
    }
}

# -------------------------------
# Step 2: Setup or update haris repo
# -------------------------------
Set-Location $env:USERPROFILE

if (Test-Path "haris") {
    if (Test-Path "haris\.git") {
        Write-Host "Found existing haris git repository. Updating to branch '$Branch'..."
        Set-Location haris
        
        # Fetch all remote branches
        git fetch
        
        # Check if branch exists locally
        $LocalBranches = git branch --list $Branch
        $RemoteBranches = git branch -r --list "origin/$Branch"
        
        if ($LocalBranches) {
            # Local branch exists, switch to it
            git switch $Branch
        } elseif ($RemoteBranches) {
            # Remote branch exists, create local tracking branch
            git switch -c $Branch origin/$Branch
        } else {
            Write-Host "ERROR: Branch '$Branch' not found after fetch" -ForegroundColor Red
            exit 1
        }
        
        # Pull latest changes
        git pull
    } else {
        Write-Host "Found existing haris folder but it's not a git repository. Removing..."
        Remove-Item -Path haris -Recurse -Force
        Write-Host "Cloning haris repository (branch: $Branch)..."
        git clone -b $Branch --single-branch $Repository
        Set-Location haris
    }
} else {
    Write-Host "Cloning haris repository (branch: $Branch)..."
    git clone -b $Branch --single-branch $Repository
    Set-Location haris
}

# -------------------------------
# Step 3: Create a Python virtual environment
# -------------------------------
$EnvDir = Join-Path $env:USERPROFILE "python-envs"
New-Item -ItemType Directory -Path $EnvDir -Force | Out-Null
Set-Location $EnvDir
python -m venv hut-dds

# Activate virtual environment
$ActivateScript = Join-Path $EnvDir "hut-dds\Scripts\Activate.ps1"
& $ActivateScript

# -------------------------------
# Step 4: Setup the PyDDS environment
# -------------------------------
$HarisPath = Join-Path $env:USERPROFILE "haris"
python (Join-Path $HarisPath "server\scripts\pyDDS\setup_env.py")
python (Join-Path $HarisPath "server\scripts\pyDDS\sample_data\generate_sample_data.py")

# -------------------------------
# Step 5: Setup Haris
# -------------------------------
$PythonPath = (Get-Command python).Source
$ScenarioPath = Join-Path $HarisPath "server\web\scenarios"
Set-Location $ScenarioPath

# Update DDSTest.json with Python path
$ConfigFile = "DDSTest.json"
$Config = Get-Content $ConfigFile | ConvertFrom-Json
$Config.pythonPath = $PythonPath.Replace('\', '\\')  # Escape backslashes for JSON
$Config | ConvertTo-Json -Depth 10 | Set-Content $ConfigFile

# -------------------------------
# Finish up
# -------------------------------
Write-Host ""
Write-Host "Haris setup complete!" -ForegroundColor Green
Write-Host "Branch: $Branch" -ForegroundColor Cyan
Write-Host "Run the following command to start Haris:"
Write-Host "cd $HarisPath\server && java -jar hut.jar 44101 DDSTest.json"
Write-Host ""
Write-Host "If you do not have a DDS instance with pixi support, you can run:"
Write-Host "cd $HarisPath\server && java -jar hut.jar 44101 DDSTest.json dev"
Write-Host ""
Write-Host "Visualize the simulator at: http://127.0.0.1:44101"