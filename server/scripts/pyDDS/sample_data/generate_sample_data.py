import csv
import math
import os
import random

def generate_sample_data():
    """
    Generate sample data for UAV simulation with specified agent speeds and timing:
    - Agent 1: 20 m/s (steps 1-80)
    - Agent 2: 18 m/s (steps 1-80) 
    - Agent 3: 25 m/s (steps 1-80)
    - Agent 4: 20 m/s (steps 20-80)
    - Agent 5: 18 m/s (steps 40-80)
    - 4 second intervals between steps
    """
    
    # Setup the configurable parameters
    
    # Time and step configuration
    SECONDS_PER_STEP = 0.5  # Time interval between simulation steps
    TOTAL_STEPS = 300      # Total number of simulation steps
    
    # Noise control
    USE_NOISE = False       # Enable/disable all randomization and noise
    
    # Starting position for new agents (agents 4 and 5)
    START_LAT = 50.92880002299894
    START_LON = -1.4094788872327502
    
    # Position variation for existing agents (agents 1-3)
    EXISTING_AGENT_POS_VARIATION = 0.004  # degrees
    NEW_AGENT_POS_VARIATION = 0.0002      # degrees
    
    # Movement parameters
    MAX_HEADING_CHANGE_PER_STEP = 15      # degrees
    VELOCITY_NOISE = 2.0                  # m/s random variation
    ALTITUDE_CHANGE_RANGE = 5.0           # meters per step
    MIN_ALTITUDE = 100                    # meters
    MAX_ALTITUDE = 250                    # meters
    
    # Orientation parameters
    MAX_ROLL_PITCH = 8.0                  # degrees
    
    # Battery parameters
    MIN_BATTERY_DRAIN = 0.005             # per step
    MAX_BATTERY_DRAIN = 0.015             # per step
    MIN_BATTERY_LEVEL = 0.1               # minimum battery level
    
    # Signal strength parameters
    SIGNAL_VARIATION = 0.05               # variation per step
    MIN_SIGNAL = 0.7                      # minimum signal strength
    MAX_SIGNAL = 0.98                     # maximum signal strength
    INITIAL_SIGNAL_MIN = 0.85             # initial signal range
    INITIAL_SIGNAL_MAX = 0.95
    
    # Coordinate conversion (approximate for UK latitude)
    LAT_METERS_PER_DEGREE = 111000        # meters per degree latitude
    LON_METERS_PER_DEGREE = 80000         # meters per degree longitude
    
    # Agent configurations
    agents = {
        'UAV-001': {'speed': 20, 'start_step': 1, 'mission': 'Alpha', 'start_heading': 270},
        'UAV-002': {'speed': 18, 'start_step': 1, 'mission': 'Beta', 'start_heading': 72},
        'UAV-003': {'speed': 25, 'start_step': 1, 'mission': 'Gamma', 'start_heading': 0},
        'UAV-004': {'speed': 20, 'start_step': 80, 'mission': 'Delta', 'start_heading': 216},
        'UAV-005': {'speed': 18, 'start_step': 160, 'mission': 'Echo', 'start_heading': 330}
    }
        
    # Initialize agent states
    agent_states = {}
    for agent_id, config in agents.items():
        # Apply position offsets for all agents (with optional noise)
        if agent_id in ['UAV-004', 'UAV-005']:
            # Base offset for new agents, plus optional noise
            base_lat_offset = 0.0001 * (int(agent_id[-1]) - 4)  # Small distinct offsets
            base_lon_offset = 0.0001 * (int(agent_id[-1]) - 4)
            noise_lat = random.uniform(-NEW_AGENT_POS_VARIATION, NEW_AGENT_POS_VARIATION) if USE_NOISE else 0
            noise_lon = random.uniform(-NEW_AGENT_POS_VARIATION, NEW_AGENT_POS_VARIATION) if USE_NOISE else 0
            lat_offset = base_lat_offset + noise_lat
            lon_offset = base_lon_offset + noise_lon
        else:
            # Base offset for existing agents, plus optional noise
            base_lat_offset = 0.001 * (int(agent_id[-1]) - 1)  # Distinct offsets for agents 1-3
            base_lon_offset = 0.001 * (int(agent_id[-1]) - 1)
            noise_lat = random.uniform(-EXISTING_AGENT_POS_VARIATION, EXISTING_AGENT_POS_VARIATION) if USE_NOISE else 0
            noise_lon = random.uniform(-EXISTING_AGENT_POS_VARIATION, EXISTING_AGENT_POS_VARIATION) if USE_NOISE else 0
            lat_offset = base_lat_offset + noise_lat
            lon_offset = base_lon_offset + noise_lon

        # Use defined start heading, with optional noise
        start_heading = config['start_heading']
        if USE_NOISE:
            start_heading += random.uniform(-30, 30)  # Add some initial heading variation
        start_heading = start_heading % 360

        agent_states[agent_id] = {
            'lat': START_LAT + lat_offset,
            'lon': START_LON + lon_offset,
            'altitude': random.uniform(
                MIN_ALTITUDE + 20, 
                MAX_ALTITUDE - 50) if USE_NOISE else (MIN_ALTITUDE + MAX_ALTITUDE) / 2,
            'heading': start_heading,
            'vel_x': 0,
            'vel_y': 0,
            'vel_z': 0,
            'roll': 0,
            'pitch': 0,
            'yaw': 0,
            'battery': 1.0,
            'signal': random.uniform(
                INITIAL_SIGNAL_MIN, 
                INITIAL_SIGNAL_MAX) if USE_NOISE else (INITIAL_SIGNAL_MIN + INITIAL_SIGNAL_MAX) / 2
        }
    
    # Generate data
    data = []
    
    # Header
    header = ['step', 'agent_id', 'latitude', 'longitude', 'altitude', 'heading', 
              'vel_x', 'vel_y', 'vel_z', 'roll', 'pitch', 'yaw', 'battery_level', 
              'signal_strength', 'status', 'custom_data']
    data.append(header)
    
    # Generate TOTAL_STEPS steps
    for step in range(1, TOTAL_STEPS + 1):
        for agent_id, config in agents.items():
            # Skip if agent hasn't started yet
            if step < config['start_step']:
                continue
                
            state = agent_states[agent_id]
            
            # Calculate movement based on speed and time interval
            speed_ms = config['speed']  # m/s
            distance_per_step = speed_ms * SECONDS_PER_STEP  # distance in meters

            # Convert distance to approximate lat/lon changes
            lat_per_meter = 1.0 / LAT_METERS_PER_DEGREE
            lon_per_meter = 1.0 / LON_METERS_PER_DEGREE
            
            # Generate somewhat random but realistic movement
            heading_change = random.uniform(
                -MAX_HEADING_CHANGE_PER_STEP, MAX_HEADING_CHANGE_PER_STEP) if USE_NOISE else 0
            state['heading'] = (state['heading'] + heading_change) % 360
            
            # Calculate velocity components
            heading_rad = math.radians(state['heading'])
            velocity_noise_x = random.uniform(-VELOCITY_NOISE, VELOCITY_NOISE) if USE_NOISE else 0
            velocity_noise_y = random.uniform(-VELOCITY_NOISE, VELOCITY_NOISE) if USE_NOISE else 0
            velocity_noise_z = random.uniform(-VELOCITY_NOISE, VELOCITY_NOISE) if USE_NOISE else 0
            
            state['vel_x'] = speed_ms * math.cos(heading_rad) + velocity_noise_x
            state['vel_y'] = speed_ms * math.sin(heading_rad) + velocity_noise_y
            state['vel_z'] = velocity_noise_z
            
            # Update position
            lat_change = distance_per_step * math.cos(heading_rad) * lat_per_meter
            lon_change = distance_per_step * math.sin(heading_rad) * lon_per_meter
            
            state['lat'] += lat_change
            state['lon'] += lon_change
            
            # Update altitude slightly
            altitude_change = random.uniform(-ALTITUDE_CHANGE_RANGE, ALTITUDE_CHANGE_RANGE) if USE_NOISE else 0
            state['altitude'] += altitude_change
            state['altitude'] = max(MIN_ALTITUDE, min(MAX_ALTITUDE, state['altitude']))
            
            # Update orientation
            state['roll'] = random.uniform(-MAX_ROLL_PITCH, MAX_ROLL_PITCH) if USE_NOISE else 0
            state['pitch'] = random.uniform(-MAX_ROLL_PITCH, MAX_ROLL_PITCH) if USE_NOISE else 0
            state['yaw'] = state['heading']
            
            # Update battery (gradual decrease)
            battery_drain = random.uniform(MIN_BATTERY_DRAIN, MAX_BATTERY_DRAIN) if USE_NOISE else (MIN_BATTERY_DRAIN + MAX_BATTERY_DRAIN) / 2
            state['battery'] = max(MIN_BATTERY_LEVEL, state['battery'] - battery_drain)
            
            # Update signal strength
            signal_change = random.uniform(-SIGNAL_VARIATION, SIGNAL_VARIATION) if USE_NOISE else 0
            state['signal'] += signal_change
            state['signal'] = max(MIN_SIGNAL, min(MAX_SIGNAL, state['signal']))
            
            # Create row data
            row = [
                step,
                agent_id,
                round(state['lat'], 13),
                round(state['lon'], 13),
                round(state['altitude'], 1),
                round(state['heading'], 1),
                round(state['vel_x'], 1),
                round(state['vel_y'], 1),
                round(state['vel_z'], 1),
                round(state['roll'], 1),
                round(state['pitch'], 1),
                round(state['yaw'], 1),
                round(state['battery'], 2),
                round(state['signal'], 2),
                'ACTIVE',
                f"Mission {config['mission']} Step {step}"
            ]
            
            data.append(row)
    
    # Print summary using actual constants
    print("\nData generation summary:")
    for agent_id, config in agents.items():
        end_step = TOTAL_STEPS
        print(f"- {agent_id}: {config['speed']} m/s, steps {config['start_step']}-{end_step}")
    print(f"- {SECONDS_PER_STEP} second intervals between steps")
    print(f"- Total steps: {TOTAL_STEPS}")
    print(f"- Noise enabled: {USE_NOISE}")
    print(f"- New agents start near position: {START_LAT}, {START_LON}")
    
    return data

def save_to_csv(data, filename='sample_data.csv'):
    """Save the generated data to a CSV file"""
    # Change directory to the script's location
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)
    with open(filename, 'w', newline='', encoding='utf-8') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerows(data)
    print(f"Sample data saved to {filename}")
    print(f"Generated {len(data)-1} data rows")

if __name__ == "__main__":
    # Generate the data
    sample_data = generate_sample_data()
    
    # Save to CSV file
    save_to_csv(sample_data, 'sample_data.csv')
