# Requirements:
# - git
# - Java 17
# - Python 3.7+
# - DDS instance with pixi support setup (or Python 3.7+)
# - flatc v25.2+ (FlatBuffers compiler) - to be installed via PyDDS setup_env.py

param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$Branch = "xprize_mcs",
    [Parameter(Position=1)]
    [string]$LocalFolderName = "haris",
    [Parameter()]
    [string]$Repository = "https://github.com/SooratiLab/haris.git"
)

$ErrorActionPreference = "Stop"

# Always start in user directory
Set-Location $env:USERPROFILE

Write-Host "Setting up Haris from branch: $Branch"
Write-Host "Repository: $Repository"
Write-Host "Local folder: $LocalFolderName"
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

# Function to refresh environment variables
function Refresh-Environment {
    $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
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
    Refresh-Environment
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
        "winget" { winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements }
        "scoop" { scoop install git }
    }
    Refresh-Environment
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

Write-Host "Java is required for HARIS to run. Please ensure Java 17+ is installed."
# TODO: Fix Java detection
# Write-Host "Checking for Java..."
# $JavaInstalled = $false
# $JavaCommands = @("java", "java.exe")

# foreach ($JavaCmd in $JavaCommands) {
#     if (Get-Command $JavaCmd -ErrorAction SilentlyContinue) {
#         try {
#             $JavaVersion = & $JavaCmd --version 2>&1
#             # Write-Host "Java output: $JavaVersion" -ForegroundColor Gray

#             if ($JavaVersion -match "openjdk") {
#                 $matches.1
#                 Write-Host "Matches array:" $matches.0 -ForegroundColor Gray
#                 exit 1
#             }

#         } catch {
#             Write-Host "Error checking Java version: $_" -ForegroundColor Yellow
#         }
#     }
# }

# if (-not $JavaInstalled) {
#     Write-Host "Java 17+ not found. Installing OpenJDK 17..."
#     switch ($PkgManager) {
#         "chocolatey" { 
#             choco install openjdk17 -y 
#             Refresh-Environment
#         }
#         "winget" { 
#             Write-Host "Note: winget may require user interaction for Java installation."
#             winget install --id Microsoft.OpenJDK.17 -e --source winget --accept-package-agreements --accept-source-agreements --silent
#             Refresh-Environment
#         }
#         "scoop" { 
#             scoop bucket add java
#             scoop install openjdk17
#             Refresh-Environment
#         }
#     }
# }

# Python - Check multiple possible commands
Write-Host "Checking for Python..."
$PythonInstalled = $false
$PythonCommands = @("python", "python3", "py")

foreach ($PyCmd in $PythonCommands) {
    if (Get-Command $PyCmd -ErrorAction SilentlyContinue) {
        try {
            $PythonVersion = & $PyCmd --version 2>&1
            if ($PythonVersion -match 'Python (\d+)\.(\d+)') {
                $PyMajor = [int]$matches[1]
                $PyMinor = [int]$matches[2]
                if ($PyMajor -eq 3 -and $PyMinor -ge 7) {
                    Write-Host "Found Python $($matches[0]) using command '$PyCmd'"
                    $PythonInstalled = $true
                    $PythonCommand = $PyCmd
                    break
                }
            }
        } catch {
            # Continue checking other commands
        }
    }
}

if (-not $PythonInstalled) {
    Write-Host "Python 3.7+ not found. Installing Python..."
    switch ($PkgManager) {
        "chocolatey" { 
            choco install python3 -y 
            Refresh-Environment
        }
        "winget" { 
            Write-Host "Note: winget may require user interaction for Python installation."
            winget install --id Python.Python.3.12 -e --source winget --accept-package-agreements --accept-source-agreements --silent
            Refresh-Environment
        }
        "scoop" { 
            scoop install python
            Refresh-Environment
        }
    }
    
    # Re-check for Python after installation
    foreach ($PyCmd in $PythonCommands) {
        if (Get-Command $PyCmd -ErrorAction SilentlyContinue) {
            $PythonCommand = $PyCmd
            break
        }
    }
}

# Use the found Python command or default to 'python'
if (-not $PythonCommand) {
    $PythonCommand = "python"
}

# Python pip
Write-Host "Checking for pip..."
try {
    $PipVersion = & $PythonCommand -m pip --version
    Write-Host "Found pip: $PipVersion"
} catch {
    Write-Host "pip not found. Installing..."
    & $PythonCommand -m ensurepip --upgrade
}

# FlatBuffers compiler (flatc) - Will be installed via PyDDS setup_env.py

# -------------------------------
# Step 2: Setup or update repository
# -------------------------------
Set-Location $env:USERPROFILE

if (Test-Path $LocalFolderName) {
    if (Test-Path "$LocalFolderName\.git") {
        Write-Host "Found existing $LocalFolderName git repository. Updating to branch '$Branch'..."
        Set-Location $LocalFolderName
        
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
        Write-Host "Found existing $LocalFolderName folder but it's not a git repository. Removing..."
        Remove-Item -Path $LocalFolderName -Recurse -Force
        Write-Host "Cloning repository to '$LocalFolderName' (branch: $Branch)..."
        git clone -b $Branch --single-branch $Repository $LocalFolderName
        Set-Location $LocalFolderName
    }
} else {
    Write-Host "Cloning repository to '$LocalFolderName' (branch: $Branch)..."
    git clone -b $Branch --single-branch $Repository $LocalFolderName
    Set-Location $LocalFolderName
}

# -------------------------------
# Step 3: Create a Python virtual environment
# -------------------------------
$EnvDir = Join-Path $env:USERPROFILE "python-envs"
New-Item -ItemType Directory -Path $EnvDir -Force | Out-Null
Set-Location $EnvDir

# Remove existing environment if it exists
if (Test-Path "hut-dds") {
    Write-Host "Removing existing virtual environment..."
    Remove-Item -Path "hut-dds" -Recurse -Force
}

Write-Host "Creating new virtual environment..."
& $PythonCommand -m venv hut-dds

# Activate virtual environment
$ActivateScript = Join-Path $EnvDir "hut-dds\Scripts\Activate.ps1"
if (Test-Path $ActivateScript) {
    & $ActivateScript
    Write-Host "Virtual environment activated"
} else {
    Write-Host "Warning: Could not find activation script at $ActivateScript"
}

# -------------------------------
# Step 4: Setup the PyDDS environment
# -------------------------------
$LocalRepoPath = Join-Path $env:USERPROFILE $LocalFolderName
Write-Host "Setting up PyDDS environment..."
& $PythonCommand (Join-Path $LocalRepoPath "server\scripts\pyDDS\setup_env.py")
& $PythonCommand (Join-Path $LocalRepoPath "server\scripts\pyDDS\sample_data\generate_sample_data.py")

# -------------------------------
# Step 5: Setup Haris
# -------------------------------
$VenvPythonPath = Join-Path $EnvDir "hut-dds\Scripts\python.exe"
$ScenarioPath = Join-Path $LocalRepoPath "server\web\scenarios"
Set-Location $ScenarioPath

# Update DDSTest.json with Python path
$ConfigFile = "DDSTest.json"
if (Test-Path $ConfigFile) {
    $Config = Get-Content $ConfigFile | ConvertFrom-Json
    $Config.pythonPath = $VenvPythonPath.Replace('\', '/')
    $Config | ConvertTo-Json -Depth 10 | Set-Content $ConfigFile
    Write-Host "Updated $ConfigFile with virtual environment Python path: $VenvPythonPath"
} else {
    Write-Host "Warning: $ConfigFile not found"
}

# -------------------------------
# Finish up
# -------------------------------
Write-Host ""
Write-Host "Haris setup complete!" -ForegroundColor Green
Write-Host "Branch: $Branch" -ForegroundColor Cyan
Write-Host "Local folder: $LocalFolderName" -ForegroundColor Cyan
Write-Host "Python Command: $PythonCommand" -ForegroundColor Cyan
Write-Host ""
Write-Host "Run the following command to start Haris:"
Write-Host "cd $LocalRepoPath\server && java -jar hut.jar 44101 DDSTest.json"
Write-Host ""
Write-Host "If you do not have a DDS instance with pixi support, you can run:"
Write-Host "cd $LocalRepoPath\server && java -jar hut.jar 44101 DDSTest.json dev"
Write-Host ""
Write-Host "Visualize the simulator at: http://127.0.0.1:44101"