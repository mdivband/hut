# FlatBuffers Setup Guide for DDS

This guide will help you set up FlatBuffers serialization for your DDS communication.

## Quick Setup

1. **Install required packages:**
   ```bash
   pip install flatbuffers zenoh
   ```

2. **Run the setup script:**
   ```bash
   python setup_flatbuffers.py
   ```

3. **Test the setup:**
   ```bash
   # Terminal 1 - Start the listener
   python listener_flatbuffers.py --continuous
   
   # Terminal 2 - Start the publisher
   python publisher_flatbuffers.py --agent_id UAV-1 --interval 2
   ```

## Manual Setup (if automatic setup fails)

### Step 1: Download FlatBuffers Compiler

**Windows:**
- Download from: https://github.com/google/flatbuffers/releases/latest
- Extract `flatc.exe` to this directory

**Linux/Mac:**
- Install via package manager or compile from source
- Ensure `flatc` is in your PATH

### Step 2: Generate Python Code

```bash
# Create output directory
mkdir -p flatbuffers/generated

# Generate Python code from schema
flatc --python -o flatbuffers/generated flatbuffers/dds.fbs
```

### Step 3: Update Python Path

The generated code will be in `flatbuffers/generated/DDSSchema/`

## Schema Overview

The `dds.fbs` schema defines:
- **DDSMessage**: Main message container
- **Position**: GPS coordinates (lat, lon, alt)
- **Velocity**: 3D velocity vector
- **Attitude**: Orientation (roll, pitch, yaw)
- **Status**: Enumerated status values

## Usage Examples

### Publisher (FlatBuffers):
```python
python publisher_flatbuffers.py --agent_id UAV-1 --format flatbuffer
```

### Publisher (String fallback):
```python
python publisher_flatbuffers.py --agent_id UAV-1 --format string
```

### Listener (Auto-detect format):
```python
python listener_flatbuffers.py --continuous --format auto
```

## Integration with Java DDSController

The Java `PythonExecutor` class will work with both formats:
- FlatBuffers: Structured binary data
- String: Plain text fallback

The listener automatically detects the format and outputs accordingly.

## Troubleshooting

1. **Import errors**: Run `python setup_flatbuffers.py` first
2. **No flatc found**: Download manually and place in this directory
3. **Permission issues**: Ensure scripts are executable
4. **Connection issues**: Check if Zenoh router is running

## Files Created

- `dds.fbs`: FlatBuffers schema definition
- `listener_flatbuffers.py`: Enhanced listener with FlatBuffers support
- `publisher_flatbuffers.py`: Sample publisher with FlatBuffers
- `setup_flatbuffers.py`: Automated setup script
- `flatbuffers/generated/`: Generated Python classes (after setup)
