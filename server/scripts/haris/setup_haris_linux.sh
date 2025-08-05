#!/bin/bash
 
# Requirements:
# - git
# - pixi
# - Java 17
# - Python 3.7+
# - DDS instance with pixi support setup (or Python 3.7+)
# - flatc (FlatBuffers compiler)
set -e
 
# Step 1: Setup or update haris repo
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
 
# Step 2: Create a Python virtual environment
mkdir -p ~/python-envs
cd ~/python-envs
python3 -m venv hut-dds
source hut-dds/bin/activate
pip install eclipse-zenoh flatbuffers
 
# Step 2.5: Install flatc (FlatBuffers compiler)
echo "Installing flatc (FlatBuffers compiler)..."
 
if command -v flatc >/dev/null 2>&1; then
  echo "flatc already installed."
else
  if command -v apt >/dev/null 2>&1; then
    sudo apt update
    sudo apt install -y flatbuffers-compiler
  else
    echo "Please install 'flatc' manually from https://github.com/google/flatbuffers/releases"
    exit 1
  fi
fi
 
# Compile FlatBuffers
python ~/haris/server/scripts/pyDDS/flatbuffers/setup_flatbuffers.py
# Generate sample data
python ~/haris/server/scripts/pyDDS/sample_data/generate_sample_data.py

# Step 3: Get Python path
PYTHON_PATH=$(which python)
 
# Step 4: Navigate to the scenarios directory
cd ~/haris/server/web/scenarios
 
# Step 5: Replace pythonPath in DDSTest.json
if command -v jq >/dev/null 2>&1; then
  jq --arg path "$PYTHON_PATH" '.pythonPath = $path' DDSTest.json > tmp.json && mv tmp.json DDSTest.json
else
  sed -i "s|\"pythonPath\": \".*\"|\"pythonPath\": \"$PYTHON_PATH\"|" DDSTest.json
fi
 
# Wrapping up
echo
echo "Haris setup complete!"
echo "Run the following command to start Haris:"
echo "cd ~/haris/server && java -jar hut.jar 44101 DDSTest.json"
echo
echo "If you do not have a DDS instance with pixi support, you can run:"
echo "cd ~/haris/server && java -jar hut.jar 44101 DDSTest.json dev"
echo
echo "Visualize the simulator at: http://127.0.0.1:44101"