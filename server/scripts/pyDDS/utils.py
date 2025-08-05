import os
import sys
import platform

# Add the script directory to sys.path
script_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(script_dir)
# Add the flatbuffers generated directory to sys.path
sys.path.append(os.path.join(script_dir, 'flatbuffers', 'generated'))

def setup_logging(file_name='fbs_listener.log'):
    """Setup logging configuration"""
    import logging
    import shutil

    # Configure the logger file
    pwd = os.getcwd()
    os.chdir(script_dir)
    if not os.path.exists('logs'):
        os.makedirs('logs')
    file_path = os.path.join('logs', file_name)

    # Check if file exists and rotate logs
    # Older should be 1, oldest should be 2, etc.
    if os.path.exists(file_path):
        # Get the base name without extension
        base_name = os.path.splitext(file_name)[0]
        extension = os.path.splitext(file_name)[1]
        
        # Find the highest numbered backup
        max_backup = 0
        for i in range(1, 10):  # Check up to 9 backups
            backup_name = f"{base_name}.{i}{extension}"
            backup_path = os.path.join('logs', backup_name)
            if os.path.exists(backup_path):
                max_backup = i
            else:
                break
        
        # Rotate existing backups (move .2 to .3, .1 to .2, etc.)
        for i in range(max_backup, 0, -1):
            old_backup = os.path.join('logs', f"{base_name}.{i}{extension}")
            new_backup = os.path.join('logs', f"{base_name}.{i+1}{extension}")
            if os.path.exists(old_backup):
                shutil.move(old_backup, new_backup)
        
        # Move current file to .1
        backup_path = os.path.join('logs', f"{base_name}.1{extension}")
        shutil.move(file_path, backup_path)
    
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(levelname)s - %(message)s',
        handlers=[
            logging.FileHandler(file_path),  # Add file handler
            # logging.StreamHandler(sys.stdout)  # Keep console output
        ]
    )
    return logging.getLogger(__name__)

def check_flatbuffers(type_based=True):
    """
    Check if FlatBuffers is available and import necessary modules

    If type_based is True, it will import Type from messages.
    If type_based is False, it will import DDSSchema.
    
    """
    try:
        import flatbuffers
        if type_based:
            from messages import PositionMessage, VelocityMessage, HeadingMessage
            from messages import Type  # Import Type from the generated messages module
        else:
            from DDSSchema import DDSMessage, AgentData, Coordinate, Velocity, Attitude, Status
        return True
    except ImportError as e:
        print(f"FlatBuffers not available - using string format: {e}", flush=True)
        return False

def get_aircraft_type(aircraft_type):
    """Get the aircraft type as an integer"""
    from messages import Type  # Import Type from the generated messages module

    # String to Type mapping
    aircraft_type_mapping = {
        'STA': Type.Type.STA,
        'FSA': Type.Type.FSA,
        'MCS': Type.Type.MCS,
        'FOB': Type.Type.FOB,
        'FIRE': Type.Type.FIRE,
        'UNDEFINED': Type.Type.UNDEFINED
    }

    return aircraft_type_mapping.get(
        aircraft_type.upper(), Type.Type.STA)

def get_aircraft_name(aircraft_type):
    """Get the aircraft name based on the type should be an integer"""
    from messages import Type  # Import Type from the generated messages module

    aircraft_names = {
        Type.Type.STA: 'STA',
        Type.Type.FSA: 'FSA',
        Type.Type.MCS: 'MCS',
        Type.Type.FOB: 'FOB',
        Type.Type.FIRE: 'FIRE',
        Type.Type.UNDEFINED: 'UNDEFINED'
    }

    # Check if aircraft_type is a string and is in the aricraft_names
    if isinstance(aircraft_type, str) and aircraft_type.upper() in aircraft_names.values():
        return aircraft_type
    
    elif isinstance(aircraft_type, Type.Type):
        return aircraft_names.get(aircraft_type, 'Unknown')
    
    return 'Unknown'
