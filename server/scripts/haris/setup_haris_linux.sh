#!/bin/bash

# Requirements:
# - git
# - pixi
# - Java 17
# - Python 3.7+
# - DDS instance with pixi support setup (or Python 3.7+)
# - flatc (FlatBuffers compiler)

set -e

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
    apt)     $INSTALL_CMD openjdk-17-jdk ;;
    dnf|yum) $INSTALL_CMD java-17-openjdk-devel ;;
    pacman)  $INSTALL_CMD jdk17-openjdk ;;
  esac
fi

# Python
echo "Checking for Python..."
if command -v python3 >/dev/null 2>&1; then
  PY_VERSION=$(python3 -V 2>&1 | awk '{print $2}')
  PY_MAJOR=$(echo "$PY_VERSION" | cut -d. -f1)
  PY_MINOR=$(echo "$PY_VERSION" | cut -d. -f2)
  if [ "$PY_MAJOR" -eq 3 ] && [ "$PY_MINOR" -ge 7 ]; then
    echo "Found Python $PY_VERSION"
  else
    echo "Python version < 3.7 detected. Installing newer Python..."
    $UPDATE_CMD
    case "$PKG_MANAGER" in
      apt)     $INSTALL_CMD python3 ;;
      dnf|yum) $INSTALL_CMD python39 ;;
      pacman)  $INSTALL_CMD python ;;
    esac
  fi
else
  echo "Python not found. Installing Python..."
  $UPDATE_CMD
  case "$PKG_MANAGER" in
    apt)     $INSTALL_CMD python3 ;;
    dnf|yum) $INSTALL_CMD python39 ;;
    pacman)  $INSTALL_CMD python ;;
  esac
fi

# Python venv
echo "Checking for Python venv..."
if python3 -m venv --help >/dev/null 2>&1; then
  echo "Python venv module available."
else
  echo "Python venv not found. Installing..."
  $UPDATE_CMD
  case "$PKG_MANAGER" in
    apt)     $INSTALL_CMD python3-venv ;;
    dnf|yum) $INSTALL_CMD python3-venv || $INSTALL_CMD python39-virtualenv ;;
    pacman)  $INSTALL_CMD python-virtualenv ;;
  esac
fi

# Check ensurepip and upgrade if needed
if python3 -m ensurepip --help >/dev/null 2>&1; then
  echo "ensurepip module available."
else
  echo "ensurepip not found. Installing..."
  $UPDATE_CMD
  case "$PKG_MANAGER" in
    apt)     $INSTALL_CMD python3-ensurepip ;;
    dnf|yum) $INSTALL_CMD python3-ensurepip || $INSTALL_CMD python39-pip ;;
    pacman)  $INSTALL_CMD python-pip ;;
  esac
fi

# Python pip
echo "Checking for pip..."
if command -v pip3 >/dev/null 2>&1; then
  echo "Found pip: $(pip3 --version)"
else
  echo "pip not found. Installing..."
  $UPDATE_CMD
  case "$PKG_MANAGER" in
    apt)     $INSTALL_CMD python3-pip ;;
    dnf|yum) $INSTALL_CMD python3-pip ;;
    pacman)  $INSTALL_CMD python-pip ;;
  esac
fi

# FlatBuffers compiler (flatc)
echo "Checking for flatc..."
if command -v flatc >/dev/null 2>&1; then
  echo "flatc already installed."
else
  echo "Installing flatc..."
  if [ "$PKG_MANAGER" = "apt" ]; then
    $UPDATE_CMD
    $INSTALL_CMD flatbuffers-compiler
  else
    echo "Please install 'flatc' manually from https://github.com/google/flatbuffers/releases"
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
  sed -i "s|\"pythonPath\": \".*\"|\"pythonPath\": \"$PYTHON_PATH\"|" DDSTest.json
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
