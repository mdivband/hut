# Requirements:
# - git
# - Java 17
# - Python 3.7+
# - DDS instance with pixi support setup (or Python 3.7+)
# - flatc v25.2+ (FlatBuffers compiler) - to be installed via PyDDS setup_env.py

param(
    [Parameter(Position=0)]
    [Alias("b")]
    [string]$Branch = "xprize_mcs",
    [Parameter(Position=1)]
    [Alias("local", "path", "folder")]
    [string]$LocalFolderName = "haris",
    [Parameter()]
    [string]$Repository = "https://github.com/SooratiLab/haris.git",
    [Parameter()]
    [Alias("no-pull", "n")]
    [switch]$NoPull
)

$ErrorActionPreference = "Stop"

# Initialize error tracking
$HasErrors = $false

# Always start in user directory
Set-Location $env:USERPROFILE

Write-Host "Setting up Haris from branch: $Branch"
Write-Host "Repository: $Repository"
Write-Host "Local folder: $LocalFolderName"
if ($NoPull) {
    Write-Host "No-pull mode: Will not pull from remote if repo exists locally" -ForegroundColor Yellow
}
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
    try {
        switch ($PkgManager) {
            "chocolatey" { choco install git -y }
            "winget" { winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements }
            "scoop" { scoop install git }
        }
        Refresh-Environment
        Write-Host "Git installed successfully" -ForegroundColor Green
    } catch {
        Write-Host "ERROR: Failed to install Git: $_" -ForegroundColor Red
        $HasErrors = $true
    }
}

# Verify branch exists before proceeding (skip if no-pull mode)
if (-not $NoPull) {
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
}

Write-Host "Java is required for HARIS to run. Please ensure Java 17+ is installed."

# Python - Check multiple possible commands
Write-Host "Checking for Python..."

# First, check if we're in a virtual environment and try to deactivate it
if ($env:VIRTUAL_ENV) {    
    # Try to deactivate
    Write-Host "Deactivating existing Python virtual env..."
    if (Get-Command deactivate -ErrorAction SilentlyContinue) {
        try {
            deactivate
        } catch {
            Write-Host "ERROR: Cannot deactivate virtual environment." -ForegroundColor Red
            Write-Host "Please run 'deactivate' manually and try again." -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "ERROR: Cannot deactivate virtual environment." -ForegroundColor Red
        Write-Host "Please run 'deactivate' manually and try again." -ForegroundColor Red
        exit 1
    }
    
    # Clear environment variables and refresh PATH
    $env:VIRTUAL_ENV = $null
    $env:VIRTUAL_ENV_PROMPT = $null
    Refresh-Environment
}

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
    try {
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
                $PythonInstalled = $true
                Write-Host "Python installed successfully" -ForegroundColor Green
                break
            }
        }
        
        if (-not $PythonInstalled) {
            Write-Host "ERROR: Python installation appears to have failed" -ForegroundColor Red
            $HasErrors = $true
        }
    } catch {
        Write-Host "ERROR: Failed to install Python: $_" -ForegroundColor Red
        $HasErrors = $true
    }
}

# Use the found Python command or default to 'python'
if (-not $PythonCommand) {
    $PythonCommand = "python"
    Write-Host "WARNING: Using default Python command 'python' - this may not work" -ForegroundColor Yellow
}

# FlatBuffers compiler (flatc) - Will be installed via PyDDS setup_env.py

if (Test-Path $LocalFolderName) {
    if (Test-Path "$LocalFolderName\.git") {
        Set-Location $LocalFolderName

        if (-not $NoPull) {
            Write-Host "Found existing $LocalFolderName git repository."
            Write-Host "Updating to branch '$Branch'..."
            
            try {
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
                    $HasErrors = $true
                }
                
                # Pull latest changes
                git pull
                Write-Host "Repository updated successfully" -ForegroundColor Green
            } catch {
                Write-Host "ERROR: Failed to update repository: $_" -ForegroundColor Red
                $HasErrors = $true
            }
        }
        # No output when $NoPull is true and git repo exists
    } else {
        if ($NoPull) {
            Write-Host "ERROR: Found existing '$LocalFolderName' folder but it's not a git repository." -ForegroundColor Red
            Write-Host "Cannot proceed with -no-pull flag. Please remove the folder or clone manually." -ForegroundColor Red
            exit 1
        } else {
            Write-Host "Found existing $LocalFolderName folder but it's not a git repository. Removing..."
            try {
                Remove-Item -Path $LocalFolderName -Recurse -Force
                Write-Host "Cloning repository to '$LocalFolderName' (branch: $Branch)..."
                git clone -b $Branch --single-branch $Repository $LocalFolderName
                Set-Location $LocalFolderName
                Write-Host "Repository cloned successfully" -ForegroundColor Green
            } catch {
                Write-Host "ERROR: Failed to clone repository: $_" -ForegroundColor Red
                $HasErrors = $true
            }
        }
    }
} else {
    if ($NoPull) {
        Write-Host "ERROR: Local folder '$LocalFolderName' does not exist and -no-pull flag is set." -ForegroundColor Red
        Write-Host "Cannot proceed without cloning the repository. Please run without -no-pull flag first." -ForegroundColor Red
        exit 1
    } else {
        Write-Host "Cloning repository to '$LocalFolderName' (branch: $Branch)..."
        try {
            git clone -b $Branch --single-branch $Repository $LocalFolderName
            Set-Location $LocalFolderName
            Write-Host "Repository cloned successfully" -ForegroundColor Green
        } catch {
            Write-Host "ERROR: Failed to clone repository: $_" -ForegroundColor Red
            $HasErrors = $true
        }
    }
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
    try {
        Remove-Item -Path "hut-dds" -Recurse -Force
    } catch {
        Write-Host "ERROR: Failed to remove existing environment: $_" -ForegroundColor Red
        $HasErrors = $true
    }
}

try {
    & $PythonCommand -m venv hut-dds
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Virtual environment created successfully" -ForegroundColor Green
    } else {
        Write-Host "ERROR: Virtual environment creation failed with exit code: $LASTEXITCODE" -ForegroundColor Red
        $HasErrors = $true
    }
} catch {
    Write-Host "ERROR: Failed to create virtual environment: $_" -ForegroundColor Red
    $HasErrors = $true
}

# Activate virtual environment
$ActivateScript = Join-Path $EnvDir "hut-dds\Scripts\Activate.ps1"
if (Test-Path $ActivateScript) {
    try {
        & $ActivateScript
    } catch {
        Write-Host "ERROR: Failed to activate virtual environment: $_" -ForegroundColor Red
        $HasErrors = $true
    }
} else {
    Write-Host "ERROR: Could not find activation script at $ActivateScript" -ForegroundColor Red
    $HasErrors = $true
}

# -------------------------------
# Step 4: Setup the PyDDS environment
# -------------------------------
$LocalRepoPath = Join-Path $env:USERPROFILE $LocalFolderName
Write-Host "Setting up PyDDS environment..."

$SetupEnvScript = Join-Path $LocalRepoPath "server\scripts\pyDDS\setup_env.py"
$SampleDataScript = Join-Path $LocalRepoPath "server\scripts\pyDDS\sample_data\generate_sample_data.py"

# Use the virtual environment Python
$VenvPythonPath = Join-Path $EnvDir "hut-dds\Scripts\python.exe"

if (Test-Path $VenvPythonPath) {
    Write-Host "Using virtual environment Python: $VenvPythonPath" -ForegroundColor Cyan
    
    try {
        & $VenvPythonPath $SetupEnvScript
        if ($LASTEXITCODE -eq 0) {
        } else {
            Write-Host "ERROR: PyDDS setup failed with exit code: $LASTEXITCODE" -ForegroundColor Red
            $HasErrors = $true
        }
    } catch {
        Write-Host "ERROR: Failed to run PyDDS setup: $_" -ForegroundColor Red
        $HasErrors = $true
    }
    
    try {
        & $VenvPythonPath $SampleDataScript
        if ($LASTEXITCODE -eq 0) {
        } else {
            Write-Host "ERROR: Sample data generation failed with exit code: $LASTEXITCODE" -ForegroundColor Red
            $HasErrors = $true
        }
    } catch {
        Write-Host "ERROR: Failed to generate sample data: $_" -ForegroundColor Red
        $HasErrors = $true
    }
} else {
    Write-Host "ERROR: Virtual environment Python not found at: $VenvPythonPath" -ForegroundColor Red
    Write-Host "Falling back to system Python: $PythonCommand" -ForegroundColor Yellow
    try {
        & $PythonCommand $SetupEnvScript
        & $PythonCommand $SampleDataScript
    } catch {
        Write-Host "ERROR: Failed to run PyDDS setup with system Python: $_" -ForegroundColor Red
        $HasErrors = $true
    }
}

# -------------------------------
# Step 5: Setup Haris
# -------------------------------
$ScenarioPath = Join-Path $LocalRepoPath "server\web\scenarios"
Set-Location $ScenarioPath

# Update DDSTest.json with Python path
$ConfigFile = "DDSTest.json"
if (Test-Path $ConfigFile) {
    try {
        $Config = Get-Content $ConfigFile | ConvertFrom-Json
        $Config.pythonPath = $VenvPythonPath.Replace('\', '/')
        $Config | ConvertTo-Json -Depth 10 | Set-Content $ConfigFile
        Write-Host "Updated $ConfigFile with virtual environment Python path: $VenvPythonPath" -ForegroundColor Green
    } catch {
        Write-Host "ERROR: Failed to update ${ConfigFile}: $_" -ForegroundColor Red
        $HasErrors = $true
    }
} else {
    Write-Host "WARNING: ${ConfigFile} not found" -ForegroundColor Yellow
}

# -------------------------------
# Finish up
# -------------------------------
Write-Host ""
if ($HasErrors) {
    Write-Host "Haris setup completed with ERRORS!" -ForegroundColor Red
    Write-Host "Please review the error messages above and fix any issues." -ForegroundColor Yellow
} else {
    Write-Host "Haris setup complete!" -ForegroundColor Green
}

# Always end in the server directory
$ServerPath = Join-Path $LocalRepoPath "server"
Set-Location $ServerPath

Write-Host "Run the following command to start Haris:"
Write-Host "cd $LocalRepoPath\server && java -jar hut.jar 44101 DDSTest.json"
Write-Host ""
Write-Host "If you do not have a DDS instance with pixi support, you can run:"
Write-Host "cd $LocalRepoPath\server && java -jar hut.jar 44101 DDSTest.json dev"
Write-Host ""
Write-Host "Visualize the simulator at: http://127.0.0.1:44101"