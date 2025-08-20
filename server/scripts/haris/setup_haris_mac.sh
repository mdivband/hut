#!/bin/bash

# Requirements:
# - git
# - pixi
# - Java 17
# - Python 3.7+
# - DDS instance with pixi support setup (or Python 3.7+)
# - flatc v25.2+ (FlatBuffers compiler)

set -e

# -------------------------------
# Step 0: Detect package manager
# -------------------------------
if command -v brew >/dev/null 2>&1; then
  PKG_MANAGER="brew"
  UPDATE_CMD="brew update"
  INSTALL_CMD="brew install"
elif command -v port >/dev/null 2>&1; then
  PKG_MANAGER="port"
  UPDATE_CMD="sudo port selfupdate"
  INSTALL_CMD="sudo port install"
else
  echo "Neither Homebrew nor MacPorts found. Please install Homebrew first:"
  echo "  /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
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
  $INSTALL_CMD git
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
    brew) $INSTALL_CMD openjdk@17 ;;
    port) $INSTALL_CMD openjdk17 ;;
  esac
fi

# Python
echo "Checking for Python..."
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
      brew) $INSTALL_CMD python@3.12 ;;
      port) $INSTALL_CMD python312 ;;
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
    brew) $INSTALL_CMD python@3.12 ;;
    port) $INSTALL_CMD python312 ;;
  esac
  # Get version after installation
  PY_VERSION=$(python3 -V 2>&1 | awk '{print $2}')
  PY_MAJOR=$(echo "$PY_VERSION" | cut -d. -f1)
  PY_MINOR=$(echo "$PY_VERSION" | cut -d. -f2)
  PY_MAJOR_MINOR="${PY_MAJOR}.${PY_MINOR}"
fi

# Python venv (built into Python 3.3+ on Mac)
echo "Checking for Python venv..."
if python3 -m venv --help >/dev/null 2>&1; then
  echo "Python venv module available."
else
  echo "Python venv not available. This is unusual for Mac Python installations."
  echo "Please check your Python installation."
fi

# Python pip (usually comes with Python on Mac)
echo "Checking for pip..."
if python3 -m pip --version >/dev/null 2>&1; then
  echo "Found pip: $(python3 -m pip --version)"
else
  echo "pip not found. Installing..."
  # On Mac, try ensurepip first
  if python3 -m ensurepip --upgrade >/dev/null 2>&1; then
    echo "pip installed via ensurepip"
  else
    echo "ensurepip failed. Installing via package manager..."
    $UPDATE_CMD
    case "$PKG_MANAGER" in
      brew) echo "pip should come with Python from Homebrew" ;;
      port) $INSTALL_CMD py312-pip ;;
    esac
  fi
fi

# FlatBuffers compiler (flatc)
echo "Checking for flatc..."
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
  FLATC_VERSION="25.2.10"
  FLATC_URL="https://github.com/google/flatbuffers/releases/download/v${FLATC_VERSION}/Mac.flatc.binary.zip"
  TEMP_DIR=$(mktemp -d)

  echo "Downloading flatc v$FLATC_VERSION from $FLATC_URL..."
  curl -L "$FLATC_URL" -o "$TEMP_DIR/flatc.zip"

  cd "$TEMP_DIR"
  unzip -q flatc.zip

  # Find flatc binary after unzip
  if [ -f flatc ]; then
    TARGET_DIR="/usr/local/bin"
    [ -d "/opt/homebrew/bin" ] && TARGET_DIR="/opt/homebrew/bin"

    echo "Installing flatc to $TARGET_DIR..."
    sudo mv flatc "$TARGET_DIR/"
    sudo chmod +x "$TARGET_DIR/flatc"
  else
    echo "flatc binary not found in archive!"
    exit 1
  fi

  rm -rf "$TEMP_DIR"

  if command -v flatc >/dev/null 2>&1; then
    echo "flatc $(flatc --version) installed successfully"
  else
    echo "Failed to install flatc"
    exit 1
  fi
fi

# -------------------------------
# Step 2: Setup or update haris repo
# -------------------------------
cd ~

if [[ -d "haris" ]]; then
  if [[ -d "haris/.git" ]]; then
    echo "Found existing haris git repository. Fetching updates..."
    cd haris
    git fetch
    git switch xprize_mcs
    git pull
  else
    echo "Found existing haris folder but it's not a git repository. Removing..."
    rm -rf haris
    git clone -b xprize_mcs --single-branch https://github.com/SooratiLab/haris.git
    cd haris
  fi
else
  echo "Cloning haris repository..."
  git clone -b xprize_mcs --single-branch https://github.com/SooratiLab/haris.git
  cd haris
fi

# -------------------------------
# Step 3: Create a Python virtual environment
# -------------------------------
mkdir -p ~/python-envs
cd ~/python-envs
python3 -m venv hut-dds
source hut-dds/bin/activate
pip install --upgrade pip
pip install eclipse-zenoh flatbuffers

# -------------------------------
# Step 4: Compile FlatBuffers
# -------------------------------
python ~/haris/server/scripts/pyDDS/flatbuffers/setup_flatbuffers.py
python ~/haris/server/scripts/pyDDS/sample_data/generate_sample_data.py

# -------------------------------
# Step 5: Setup Haris
# -------------------------------
PYTHON_PATH=$(which python)
cd ~/haris/server/web/scenarios

if command -v jq >/dev/null 2>&1; then
  jq --arg path "$PYTHON_PATH" '.pythonPath = $path' DDSTest.json > tmp.json && mv tmp.json DDSTest.json
else
  # Different syntax as Mac uses BSD sed
  sed -i '' "s|\"pythonPath\": \".*\"|\"pythonPath\": \"$PYTHON_PATH\"|" DDSTest.json
fi

# -------------------------------
# Finish up
# -------------------------------
echo
echo "Haris setup complete!"
echo "Run the following command to start Haris:"
echo "cd ~/haris/server && java -jar hut.jar 44101 DDSTest.json"
echo
echo "If you do not have a DDS instance with pixi support, you can run:"
echo "cd ~/haris/server && java -jar hut.jar 44101 DDSTest.json dev"
echo
echo "Visualize the simulator at: http://127.0.0.1:44101"