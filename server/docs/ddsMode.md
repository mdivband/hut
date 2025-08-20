## Using the Simulator in DDS Mode

### Overview
In DDS Mode, the Simulator retrieves UAV swarm state information from a DDS server.

### Architecture
This setup uses the `zenoh.io` pub/sub framework with `flatbuffers` for data serialization. A sample `listener` and `publisher` are provided in **Python** to simplify testing and development.

---

### Automated Setup
## Linux
A setup script is not provided for automated setup on linux.
This script will install all required dependencies and provide the running `jar` command.

**To use this script, run the following**
`wget https://raw.githubusercontent.com/SooratiLab/haris/xprize_mcs/scripts/setup_haris.sh -O setup_haris.sh`
`chmod +x setup_haris.sh`
`./setup_haris.sh`

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

### 5. Setup FlatBuffers

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

