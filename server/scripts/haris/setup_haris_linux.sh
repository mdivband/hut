#!/bin/bash

# Requirements:
# - git
# - pixi
# - Java 17
# - Python 3.7+
# - DDS instance with pixi support setup (or Python 3.7+)
# - flatc v25.2+ (FlatBuffers compiler)

set -e

# Default parameters
BRANCH="xprize_mcs"
LOCAL_FOLDER_NAME="haris"
REPOSITORY="https://github.com/SooratiLab/haris.git"
NO_PULL=false
HAS_ERRORS=false

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo "Options:"
    echo "  -b, --branch BRANCH         Branch to clone/switch to (default: xprize_mcs)"
    echo "  -l, --local FOLDER          Local folder name (default: haris)"
    echo "  -r, --repository URL        Repository URL (default: https://github.com/SooratiLab/haris.git)"
    echo "  -n, --no-pull              Don't pull from remote if repo exists locally"
    echo "  -h, --help                 Show this help message"
    exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -b|--branch)
            BRANCH="$2"
            shift 2
            ;;
        -l|--local|--path|--folder)
            LOCAL_FOLDER_NAME="$2"
            shift 2
            ;;
        -r|--repository)
            REPOSITORY="$2"
            shift 2
            ;;
        -n|--no-pull)
            NO_PULL=true
            shift
            ;;
        -h|--help)
            show_usage
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            ;;
    esac
done

# Function to check if remote branch exists
check_remote_branch() {
    local repo="$1"
    local branch="$2"
    
    if git ls-remote --heads "$repo" | grep -q "refs/heads/$branch$"; then
        return 0
    else
        return 1
    fi
}

echo "Setting up Haris from branch: $BRANCH"
echo "Repository: $REPOSITORY"
echo "Local folder: $LOCAL_FOLDER_NAME"
if [ "$NO_PULL" = true ]; then
    echo "No-pull mode: Will not pull from remote if repo exists locally"
fi
echo

# Start in home directory
cd ~

# -------------------------------
# Step 0: Detect package manager
# -------------------------------
if command -v apt >/dev/null 2>&1; then
  PKG_MANAGER="apt"
  UPDATE_CMD="sudo apt update"
  INSTALL_CMD="sudo apt install -y"
elif command -v dnf >/dev/null 2>&1; then
  PKG_MANAGER="dnf"
  UPDATE_CMD="sudo dnf makecache"
  INSTALL_CMD="sudo dnf install -y"
elif command -v yum >/dev/null 2>&1; then
  PKG_MANAGER="yum"
  UPDATE_CMD="sudo yum makecache"
  INSTALL_CMD="sudo yum install -y"
elif command -v pacman >/dev/null 2>&1; then
  PKG_MANAGER="pacman"
  UPDATE_CMD="sudo pacman -Sy"
  INSTALL_CMD="sudo pacman -S --noconfirm"
else
  echo "Unsupported package manager. Please install dependencies manually."
  exit 1
fi

echo "Using package manager: $PKG_MANAGER"

# -------------------------------
# Step 1: Install dependencies
# -------------------------------

# Git
echo "Checking for Git..."
if command -v git >/dev/null 2>&1; then
  echo "Found Git: $(git --version)"
else
  echo "Git not found. Installing..."
  $UPDATE_CMD
  if $INSTALL_CMD git; then
    echo "Git installed successfully"
  else
    echo "ERROR: Failed to install Git"
    HAS_ERRORS=true
  fi
fi

# Verify branch exists before proceeding (skip if no-pull mode)
if [ "$NO_PULL" != true ]; then
    echo "Checking if branch '$BRANCH' exists in repository..."
    if check_remote_branch "$REPOSITORY" "$BRANCH"; then
        echo "Branch '$BRANCH' found in repository."
    else
        echo "ERROR: Branch '$BRANCH' does not exist in repository '$REPOSITORY'"
        echo "Available branches:"
        git ls-remote --heads "$REPOSITORY" | sed 's/.*refs\/heads\//  - /' | sort
        exit 1
    fi
fi

# Java
echo "Checking for Java..."
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
  echo "Found Java version $JAVA_VERSION"
else
  echo "Java not found. Installing OpenJDK 17..."
  $UPDATE_CMD
  case "$PKG_MANAGER" in
    apt)     
      if $INSTALL_CMD openjdk-17-jdk; then
        echo "Java installed successfully"
      else
        echo "ERROR: Failed to install Java"
        HAS_ERRORS=true
      fi
      ;;
    dnf|yum) 
      if $INSTALL_CMD java-17-openjdk-devel; then
        echo "Java installed successfully"
      else
        echo "ERROR: Failed to install Java"
        HAS_ERRORS=true
      fi
      ;;
    pacman)  
      if $INSTALL_CMD jdk17-openjdk; then
        echo "Java installed successfully"
      else
        echo "ERROR: Failed to install Java"
        HAS_ERRORS=true
      fi
      ;;
  esac
fi

# Python - Check and deactivate existing virtual environment first
echo "Checking for Python..."

# First, check if we're in a virtual environment and try to deactivate it
if [ -n "$VIRTUAL_ENV" ]; then
    echo "Deactivating existing Python virtual environment..."
    if command -v deactivate >/dev/null 2>&1; then
        if deactivate; then
            echo "Virtual environment deactivated successfully"
        else
            echo "ERROR: Cannot deactivate virtual environment."
            echo "Please run 'deactivate' manually and try again."
            exit 1
        fi
    else
        echo "ERROR: Cannot deactivate virtual environment."
        echo "Please run 'deactivate' manually and try again."
        exit 1
    fi
    
    # Clear environment variables
    unset VIRTUAL_ENV
    unset VIRTUAL_ENV_PROMPT
fi

if command -v python3 >/dev/null 2>&1; then
  PY_VERSION=$(python3 -V 2>&1 | awk '{print $2}')
  PY_MAJOR=$(echo "$PY_VERSION" | cut -d. -f1)
  PY_MINOR=$(echo "$PY_VERSION" | cut -d. -f2)
  PY_MAJOR_MINOR="${PY_MAJOR}.${PY_MINOR}"
  if [ "$PY_MAJOR" -eq 3 ] && [ "$PY_MINOR" -ge 7 ]; then
    echo "Found Python $PY_VERSION"
  else
    echo "Python version < 3.7 detected. Installing newer Python..."
    $UPDATE_CMD
    case "$PKG_MANAGER" in
      apt)     
        if $INSTALL_CMD python3; then
          echo "Python installed successfully"
        else
          echo "ERROR: Failed to install Python"
          HAS_ERRORS=true
        fi
        ;;
      dnf|yum) 
        if $INSTALL_CMD python39; then
          echo "Python installed successfully"
        else
          echo "ERROR: Failed to install Python"
          HAS_ERRORS=true
        fi
        ;;
      pacman)  
        if $INSTALL_CMD python; then
          echo "Python installed successfully"
        else
          echo "ERROR: Failed to install Python"
          HAS_ERRORS=true
        fi
        ;;
    esac
    # Re-check version after installation
    PY_VERSION=$(python3 -V 2>&1 | awk '{print $2}')
    PY_MAJOR=$(echo "$PY_VERSION" | cut -d. -f1)
    PY_MINOR=$(echo "$PY_VERSION" | cut -d. -f2)
    PY_MAJOR_MINOR="${PY_MAJOR}.${PY_MINOR}"
  fi
else
  echo "Python not found. Installing Python..."
  $UPDATE_CMD
  case "$PKG_MANAGER" in
    apt)     
      if $INSTALL_CMD python3; then
        echo "Python installed successfully"
      else
        echo "ERROR: Failed to install Python"
        HAS_ERRORS=true
      fi
      ;;
    dnf|yum) 
      if $INSTALL_CMD python39; then
        echo "Python installed successfully"
      else
        echo "ERROR: Failed to install Python"
        HAS_ERRORS=true
      fi
      ;;
    pacman)  
      if $INSTALL_CMD python; then
        echo "Python installed successfully"
      else
        echo "ERROR: Failed to install Python"
        HAS_ERRORS=true
      fi
      ;;
  esac
  # Get version after installation
  PY_VERSION=$(python3 -V 2>&1 | awk '{print $2}')
  PY_MAJOR=$(echo "$PY_VERSION" | cut -d. -f1)
  PY_MINOR=$(echo "$PY_VERSION" | cut -d. -f2)
  PY_MAJOR_MINOR="${PY_MAJOR}.${PY_MINOR}"
fi

# Python venv - always install for current Python version
echo "Installing Python venv for version ${PY_MAJOR_MINOR}..."
$UPDATE_CMD
case "$PKG_MANAGER" in
  apt)     
    if ! ($INSTALL_CMD "python${PY_MAJOR_MINOR}-venv" || $INSTALL_CMD python3-venv); then
      echo "ERROR: Failed to install Python venv"
      HAS_ERRORS=true
    fi
    ;;
  dnf|yum) 
    if ! ($INSTALL_CMD "python${PY_MAJOR_MINOR}-venv" || $INSTALL_CMD python3-venv); then
      echo "ERROR: Failed to install Python venv"
      HAS_ERRORS=true
    fi
    ;;
  pacman)  
    if ! $INSTALL_CMD python-virtualenv; then
      echo "ERROR: Failed to install Python virtualenv"
      HAS_ERRORS=true
    fi
    ;;
esac

# FlatBuffers compiler (flatc)
echo "Checking for flatc..."
INSTALL_FLATC=false
if command -v flatc >/dev/null 2>&1; then
  FLATC_VERSION=$(flatc --version 2>&1 | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+' | head -1)
  FLATC_MAJOR=$(echo "$FLATC_VERSION" | cut -d. -f1)
  FLATC_MINOR=$(echo "$FLATC_VERSION" | cut -d. -f2)
  
  if [ "$FLATC_MAJOR" -gt 25 ] || ([ "$FLATC_MAJOR" -eq 25 ] && [ "$FLATC_MINOR" -ge 2 ]); then
    echo "Found flatc v$FLATC_VERSION (meets requirement v25.2+)"
  else
    echo "Found flatc v$FLATC_VERSION but need v25.2+. Installing newer version..."
    INSTALL_FLATC=true
  fi
else
  echo "flatc not found. Installing v25.2.10..."
  INSTALL_FLATC=true
fi

if [ "$INSTALL_FLATC" = true ]; then
  # Download and install flatc v25.2.10
  FLATC_URL="https://github.com/google/flatbuffers/releases/download/v25.2.10/Linux.flatc.binary.g++-13.zip"
  TEMP_DIR=$(mktemp -d)
  
  echo "Downloading flatc v25.2.10 from $FLATC_URL..."
  if command -v curl >/dev/null 2>&1; then
    if curl -L "$FLATC_URL" -o "$TEMP_DIR/flatc.zip"; then
      echo "flatc downloaded successfully"
    else
      echo "ERROR: Failed to download flatc"
      HAS_ERRORS=true
    fi
  elif command -v wget >/dev/null 2>&1; then
    if wget "$FLATC_URL" -O "$TEMP_DIR/flatc.zip"; then
      echo "flatc downloaded successfully"
    else
      echo "ERROR: Failed to download flatc"
      HAS_ERRORS=true
    fi
  else
    echo "Neither curl nor wget found. Installing curl..."
    $UPDATE_CMD
    if $INSTALL_CMD curl; then
      if curl -L "$FLATC_URL" -o "$TEMP_DIR/flatc.zip"; then
        echo "flatc downloaded successfully"
      else
        echo "ERROR: Failed to download flatc"
        HAS_ERRORS=true
      fi
    else
      echo "ERROR: Failed to install curl"
      HAS_ERRORS=true
    fi
  fi
  
  # Extract and install if download was successful
  if [ "$HAS_ERRORS" != true ]; then
    cd "$TEMP_DIR"
    if unzip flatc.zip && sudo mv flatc /usr/local/bin/ && sudo chmod +x /usr/local/bin/flatc; then
      # Verify installation
      if command -v flatc >/dev/null 2>&1; then
        NEW_VERSION=$(flatc --version 2>&1 | grep -o '[0-9]\+\.[0-9]\+\.[0-9]\+' | head -1)
        echo "flatc v$NEW_VERSION installed successfully"
      else
        echo "ERROR: Failed to verify flatc installation"
        HAS_ERRORS=true
      fi
    else
      echo "ERROR: Failed to extract and install flatc"
      HAS_ERRORS=true
    fi
    
    # Cleanup
    rm -rf "$TEMP_DIR"
  fi
fi

# -------------------------------
# Step 2: Setup or update repository
# -------------------------------
cd ~

if [[ -d "$LOCAL_FOLDER_NAME" ]]; then
  if [[ -d "$LOCAL_FOLDER_NAME/.git" ]]; then
    if [ "$NO_PULL" != true ]; then
      echo "Found existing $LOCAL_FOLDER_NAME git repository."
      echo "Updating to branch '$BRANCH'..."
      cd "$LOCAL_FOLDER_NAME"
      
      if git fetch; then
        # Check if branch exists locally or remotely
        if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
          # Local branch exists
          if git switch "$BRANCH" && git pull; then
            echo "Repository updated successfully"
          else
            echo "ERROR: Failed to switch to branch or pull updates"
            HAS_ERRORS=true
          fi
        elif git show-ref --verify --quiet "refs/remotes/origin/$BRANCH"; then
          # Remote branch exists, create local tracking branch
          if git switch -c "$BRANCH" "origin/$BRANCH"; then
            echo "Repository updated successfully"
          else
            echo "ERROR: Failed to create local tracking branch"
            HAS_ERRORS=true
          fi
        else
          echo "ERROR: Branch '$BRANCH' not found after fetch"
          HAS_ERRORS=true
        fi
      else
        echo "ERROR: Failed to fetch from repository"
        HAS_ERRORS=true
      fi
    else
      cd "$LOCAL_FOLDER_NAME"
    fi
  else
    if [ "$NO_PULL" = true ]; then
      echo "ERROR: Found existing '$LOCAL_FOLDER_NAME' folder but it's not a git repository."
      echo "Cannot proceed with --no-pull flag. Please remove the folder or clone manually."
      exit 1
    else
      echo "Found existing $LOCAL_FOLDER_NAME folder but it's not a git repository. Removing..."
      if rm -rf "$LOCAL_FOLDER_NAME" && git clone -b "$BRANCH" --single-branch "$REPOSITORY" "$LOCAL_FOLDER_NAME"; then
        cd "$LOCAL_FOLDER_NAME"
        echo "Repository cloned successfully"
      else
        echo "ERROR: Failed to remove old folder or clone repository"
        HAS_ERRORS=true
      fi
    fi
  fi
else
  if [ "$NO_PULL" = true ]; then
    echo "ERROR: Local folder '$LOCAL_FOLDER_NAME' does not exist and --no-pull flag is set."
    echo "Cannot proceed without cloning the repository. Please run without --no-pull flag first."
    exit 1
  else
    echo "Cloning repository to '$LOCAL_FOLDER_NAME' (branch: $BRANCH)..."
    if git clone -b "$BRANCH" --single-branch "$REPOSITORY" "$LOCAL_FOLDER_NAME"; then
      cd "$LOCAL_FOLDER_NAME"
      echo "Repository cloned successfully"
    else
      echo "ERROR: Failed to clone repository"
      HAS_ERRORS=true
    fi
  fi
fi

# -------------------------------
# Step 3: Create a Python virtual environment
# -------------------------------
mkdir -p ~/python-envs
cd ~/python-envs

# Remove existing environment if it exists
if [[ -d "hut-dds" ]]; then
  echo "Removing existing virtual environment..."
  if rm -rf hut-dds; then
    echo "Existing environment removed"
  else
    echo "ERROR: Failed to remove existing environment"
    HAS_ERRORS=true
  fi
fi

if python3 -m venv hut-dds; then
  echo "Virtual environment created successfully"
else
  echo "ERROR: Failed to create virtual environment"
  HAS_ERRORS=true
fi

if source hut-dds/bin/activate; then
  echo "Virtual environment activated"
else
  echo "ERROR: Failed to activate virtual environment"
  HAS_ERRORS=true
fi

# -------------------------------
# Step 4: Setup the PyDDS environment
# -------------------------------
echo "Setting up PyDDS environment..."

VENV_PYTHON="$HOME/python-envs/hut-dds/bin/python"
SETUP_ENV_SCRIPT="$HOME/$LOCAL_FOLDER_NAME/server/scripts/pyDDS/setup_env.py"
SAMPLE_DATA_SCRIPT="$HOME/$LOCAL_FOLDER_NAME/server/scripts/pyDDS/sample_data/generate_sample_data.py"

if [[ -f "$VENV_PYTHON" ]]; then
  echo "Using virtual environment Python: $VENV_PYTHON"
  
  if "$VENV_PYTHON" "$SETUP_ENV_SCRIPT"; then
    echo "PyDDS setup completed successfully"
  else
    echo "ERROR: PyDDS setup failed"
    HAS_ERRORS=true
  fi
  
  if "$VENV_PYTHON" "$SAMPLE_DATA_SCRIPT"; then
    echo "Sample data generation completed successfully"
  else
    echo "ERROR: Sample data generation failed"
    HAS_ERRORS=true
  fi
else
  echo "ERROR: Virtual environment Python not found at: $VENV_PYTHON"
  echo "Falling back to system Python"
  if python3 "$SETUP_ENV_SCRIPT" && python3 "$SAMPLE_DATA_SCRIPT"; then
    echo "PyDDS setup completed with system Python"
  else
    echo "ERROR: Failed to run PyDDS setup with system Python"
    HAS_ERRORS=true
  fi
fi

# -------------------------------
# Step 5: Setup Haris
# -------------------------------
cd "$HOME/$LOCAL_FOLDER_NAME/server/web/scenarios"

if command -v jq >/dev/null 2>&1; then
  if jq --arg path "$VENV_PYTHON" '.pythonPath = $path' DDSTest.json > tmp.json && mv tmp.json DDSTest.json; then
    echo "Updated DDSTest.json with virtual environment Python path: $VENV_PYTHON"
  else
    echo "ERROR: Failed to update DDSTest.json with jq"
    HAS_ERRORS=true
  fi
else
  if sed -i "s|\"pythonPath\": \".*\"|\"pythonPath\": \"$VENV_PYTHON\"|" DDSTest.json; then
    echo "Updated DDSTest.json with virtual environment Python path: $VENV_PYTHON"
  else
    echo "ERROR: Failed to update DDSTest.json with sed"
    HAS_ERRORS=true
  fi
fi

# -------------------------------
# Finish up
# -------------------------------
echo
if [ "$HAS_ERRORS" = true ]; then
  echo "Haris setup completed with ERRORS!"
  echo "Please review the error messages above and fix any issues."
else
  echo "Haris setup complete!"
fi

# Always end in the server directory
SERVER_PATH="$HOME/$LOCAL_FOLDER_NAME/server"
cd "$SERVER_PATH"

echo "Run the following command to start Haris:"
echo "cd $HOME/$LOCAL_FOLDER_NAME/server && java -jar hut.jar 44101 DDSTest.json"
echo
echo "If you do not have a DDS instance with pixi support, you can run:"
echo "cd $HOME/$LOCAL_FOLDER_NAME/server && java -jar hut.jar 44101 DDSTest.json dev"
echo
echo "Visualize the simulator at: http://127.0.0.1:44101"