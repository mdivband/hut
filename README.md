# HARIS with DDS Support Documentation

### Description
This repository contains a version of HARIS that supports updating the state of the Simulator using infrormation from a DDS (Data Distribution Service) framework.

For more information about **HARIS**, go to the `Master` branch.

### Overview
In DDS Mode, the Simulator retrieves UAV swarm state information from a DDS server.

### Architecture
This setup uses the `zenoh.io` pub/sub framework with `flatbuffers` for data serialization. A sample `listener` and `publisher` are provided in **Python** to simplify testing and development.

---

### Automated Setup
Setup scripts are now available for **Windows**, **macOS** and **Linux** systems.

These scripts will install all required dependencies using the available package manager and provide the running `jar` command.

## Windows
**Prerequisites:** Open PowerShell as Administrator

**Run the following in PowerShell:**
```powershell
$Branch = "xprize_mcs"  # Change this to your desired branch
$ScriptUrl = "https://raw.githubusercontent.com/SooratiLab/haris/$Branch/server/scripts/haris/setup_haris_win.ps1"
Invoke-WebRequest -Uri $ScriptUrl -OutFile setup_haris_win.ps1
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
.\setup_haris_win.ps1 $Branch
```

**Alternative one-liner:**
```powershell
# For xprize_mcs branch
iex "& { $(irm https://raw.githubusercontent.com/SooratiLab/haris/xprize_mcs/server/scripts/haris/setup_haris_win.ps1) } xprize_mcs"
```

**Usage with different branches:**
```powershell
.\setup_haris_win.ps1 main
.\setup_haris_win.ps1 xprize_mcs_dev
.\setup_haris_win.ps1 feature-branch
```

## macOS
**Prerequisites:** Install `Homebrew` first (if not already installed):
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

**After installing Homebrew, run the following:**
```bash
curl -fsSL https://raw.githubusercontent.com/SooratiLab/haris/xprize_mcs_dev/server/scripts/haris/setup_haris_mac.sh -o setup_haris.sh
chmod +x setup_haris.sh
./setup_haris.sh
```

## Linux
**Run the following in terminal**
```bash
wget https://raw.githubusercontent.com/SooratiLab/haris/xprize_mcs_dev/server/scripts/haris/setup_haris_linux.sh -O setup_haris.sh
chmod +x setup_haris.sh
./setup_haris.sh
```

### Manual Setup (for Development)
IntelliJ is the preferred IDE if you want to setup the Simulator manually for development.

The following steps have been provided to acheive this:

## Simulator Setup (Java / IntelliJ)

1. **Clone the repository.**
2. **Open the project in IntelliJ.**

3. **Configure the project:**
   - Set **Project SDK** to **Java 17 (LTS)**.
   - Set **Compile Output** to: `repo/out`.
   - Mark the following as source folders in **Modules**:
     - `server/src`
     - `server/Testing`
   - Under **Run/Debug Configurations**:
     - Set Java version to 17.
     - Leave CLI arguments empty for now.
     - Set **Working Directory** to: `repo_path/server` if not set *(critical step)*.

4. **If you see "package not found" errors:**
   - Add the following as dependencies under **Modules** by clicking on **+** and selecting **1.**:
     - `libs/`
     - `libs/selenium**/`

5. **To run a test scenario:**
   - Start the simulation.
   - Connect to the running instance via: `127.0.0.1:<port>`

---

## Python Environment Setup (for DDS)

### 1. Install Python (system-wide)

Use Python 3.7+ for compatibility.

### 2. Create a virtual environment

#### Windows:
```powershell
mkdir ~/python-envs
cd ~/python-envs
py -m venv hut-dds
hut-dds\Scripts\Activate.ps1
```

If activation fails:
```powershell
Get-ExecutionPolicy
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```
Then re-run the activation script.

#### Linux:
```bash
mkdir ~/python-envs
cd ~/python-envs
python3 -m venv hut-dds
source hut-dds/bin/activate
```

### 3. Install dependencies
```bash/ powershell
pip install eclipse-zenoh
```

### 4. Configure the simulator to use your Python environment

Edit `repo_path/server/web/scenarios/DDSTest.json` and update the `pythonPath` field:

- **Windows:**
  ```powershell
  python -c "import sys; print(sys.executable)"
  ```
  Replace all backslashes (`\`) with double backslashes (`\\`) in the path.

- **Linux:**
  ```bash
  which python
  ```

### 5. Setup FlatBuffers v25.2+

Inside the activated `hut-dds` environment and from the `repo_path` folder:
```bash/ powershell
python server/scripts/pyDDS/flatbuffers/setup_flatbuffers.py
```

### 6. Generate Sample Data

Run the following to produce example data:
```bash/ powershell
python server/scripts/pyDDS/sample_data/generate_sample_data.py
```

---

## Testing DDS Mode

1. In IntelliJ, set the following CLI arguments:
   ```
   44101 DDSTest.json dev
   ```

2. Run the application.

3. Open a browser and go to:
   ```
   http://127.0.0.1:44101/sandbox.html
   ```

---

## Common Errors & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| Listener fails to start | Missing Python dependencies | Ensure `zenoh` and `flatbuffers` are installed. |
| Python script not found | Incorrect IntelliJ project path or missing files | Set the correct working directory in IntelliJ. Confirm `pyDDS` file structure is intact. |
| `pythonPath` not working | Incorrect or missing path in `DDSTest.json` | Make sure it's set to the full path of the virtual environment's Python executable. |
| PowerShell execution policy error | Script execution blocked by Windows | Run `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser` in PowerShell as Administrator |
| Branch not found error | Specified branch doesn't exist | Check available branches or use a valid branch name like `main`, `xprize_mcs`, or `xprize_mcs_dev` |