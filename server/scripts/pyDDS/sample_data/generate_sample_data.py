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
    - Compatible with FlatBuffers schemas
    """
    
    # Setup the configurable parameters
    
    # Time and step configuration
    SECONDS_PER_STEP = 0.5  # Match the combine_interval from listener (default 0.5)
    TOTAL_STEPS = 300       # Reduced to match typical use case
    # Waypoint configuration
    WAYPOINT_INTERVAL = 50  # Provide new waypoint every N steps
    
    # Noise control
    USE_NOISE = False        # Enable for more realistic data
    
    # Starting position for new agents (agents 4 and 5)
    START_LAT = 30.65582
    START_LON = -96.42533

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
    
    # Battery parameters (kept for compatibility)
    MIN_BATTERY_DRAIN = 0.005             # per step
    MAX_BATTERY_DRAIN = 0.015             # per step
    MIN_BATTERY_LEVEL = 0.1               # minimum battery level
    
    # Signal strength parameters (kept for compatibility)
    SIGNAL_VARIATION = 0.05               # variation per step
    MIN_SIGNAL = 0.7                      # minimum signal strength
    MAX_SIGNAL = 0.98                     # maximum signal strength
    INITIAL_SIGNAL_MIN = 0.85             # initial signal range
    INITIAL_SIGNAL_MAX = 0.95
    
    # Coordinate conversion (approximate for UK latitude)
    LAT_METERS_PER_DEGREE = 111000        # meters per degree latitude
    LON_METERS_PER_DEGREE = 80000         # meters per degree longitude

    FIRE_EVENT_PROBABILITY = 0.05  # 5% chance to generate a fire event per step
    FIRE_RADIUS_METERS = 1000      # 1km radius around an agent

    # Agent configurations - using numeric IDs for FlatBuffers compatibility
    agents = {
        '1': {'speed': 20, 'start_step': 1, 'mission': 'Alpha', 'start_heading': 270, 'type': 'STA'},
        '2': {'speed': 18, 'start_step': 1, 'mission': 'Beta', 'start_heading': 72, 'type': 'STA'},
        '3': {'speed': 25, 'start_step': 1, 'mission': 'Gamma', 'start_heading': 0, 'type': 'STA'},
        '4': {'speed': 20, 'start_step': 50, 'mission': 'Delta', 'start_heading': 216, 'type': 'FSA'},
        '5': {'speed': 18, 'start_step': 80, 'mission': 'Echo', 'start_heading': 330, 'type': 'FSA'}
    }

    # Initialize agent states
    agent_states = {}
    for agent_id, config in agents.items():
        # Apply position offsets for all agents (with optional noise)
        if agent_id in ['4', '5']:
            # Base offset for new agents, plus optional noise
            base_lat_offset = 0.0001 * (int(agent_id) - 4)  # Small distinct offsets
            base_lon_offset = 0.0001 * (int(agent_id) - 4)
            noise_lat = random.uniform(-NEW_AGENT_POS_VARIATION, NEW_AGENT_POS_VARIATION) if USE_NOISE else 0
            noise_lon = random.uniform(-NEW_AGENT_POS_VARIATION, NEW_AGENT_POS_VARIATION) if USE_NOISE else 0
            lat_offset = base_lat_offset + noise_lat
            lon_offset = base_lon_offset + noise_lon
        else:
            # Base offset for existing agents, plus optional noise
            base_lat_offset = 0.001 * (int(agent_id) - 1)  # Distinct offsets for agents 1-3
            base_lon_offset = 0.001 * (int(agent_id) - 1)
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
                INITIAL_SIGNAL_MAX) if USE_NOISE else (INITIAL_SIGNAL_MIN + INITIAL_SIGNAL_MAX) / 2,
            'type': config['type']
        }
    
    # Precompute all agent positions for lookahead
    agent_positions = {agent_id: [] for agent_id in agents}
    agent_states_copy = {k: v.copy() for k, v in agent_states.items()}

    for step in range(1, TOTAL_STEPS + 11):  # +10 for lookahead
        for agent_id, config in agents.items():
            if step < config['start_step']:
                agent_positions[agent_id].append(None)
                continue

            state = agent_states_copy[agent_id]

            speed_ms = config['speed']
            distance_per_step = speed_ms * SECONDS_PER_STEP

            lat_per_meter = 1.0 / LAT_METERS_PER_DEGREE
            lon_per_meter = 1.0 / LON_METERS_PER_DEGREE

            heading_change = random.uniform(
                -MAX_HEADING_CHANGE_PER_STEP, MAX_HEADING_CHANGE_PER_STEP) if USE_NOISE else 0
            state['heading'] = (state['heading'] + heading_change) % 360

            heading_rad = math.radians(state['heading'])
            velocity_noise_x = random.uniform(-VELOCITY_NOISE, VELOCITY_NOISE) if USE_NOISE else 0
            velocity_noise_y = random.uniform(-VELOCITY_NOISE, VELOCITY_NOISE) if USE_NOISE else 0
            velocity_noise_z = random.uniform(-VELOCITY_NOISE, VELOCITY_NOISE) if USE_NOISE else 0

            state['vel_x'] = speed_ms * math.cos(heading_rad) + velocity_noise_x
            state['vel_y'] = speed_ms * math.sin(heading_rad) + velocity_noise_y
            state['vel_z'] = velocity_noise_z

            lat_change = distance_per_step * math.cos(heading_rad) * lat_per_meter
            lon_change = distance_per_step * math.sin(heading_rad) * lon_per_meter

            state['lat'] += lat_change
            state['lon'] += lon_change

            altitude_change = random.uniform(-ALTITUDE_CHANGE_RANGE, ALTITUDE_CHANGE_RANGE) if USE_NOISE else 0
            state['altitude'] += altitude_change
            state['altitude'] = max(MIN_ALTITUDE, min(MAX_ALTITUDE, state['altitude']))

            state['roll'] = random.uniform(-MAX_ROLL_PITCH, MAX_ROLL_PITCH) if USE_NOISE else 0
            state['pitch'] = random.uniform(-MAX_ROLL_PITCH, MAX_ROLL_PITCH) if USE_NOISE else 0
            state['yaw'] = state['heading']

            battery_drain = random.uniform(MIN_BATTERY_DRAIN, MAX_BATTERY_DRAIN) if USE_NOISE else (MIN_BATTERY_DRAIN + MAX_BATTERY_DRAIN) / 2
            state['battery'] = max(MIN_BATTERY_LEVEL, state['battery'] - battery_drain)

            signal_change = random.uniform(-SIGNAL_VARIATION, SIGNAL_VARIATION) if USE_NOISE else 0
            state['signal'] += signal_change
            state['signal'] = max(MIN_SIGNAL, min(MAX_SIGNAL, state['signal']))

            # Store a copy of the state for this step
            agent_positions[agent_id].append({
                'lat': state['lat'],
                'lon': state['lon'],
                'altitude': state['altitude'],
                'heading': state['heading'],
                'vel_x': state['vel_x'],
                'vel_y': state['vel_y'],
                'vel_z': state['vel_z'],
                'roll': state['roll'],
                'pitch': state['pitch'],
                'yaw': state['yaw'],
                'battery': state['battery'],
                'signal': state['signal'],
                'type': state['type']
            })
    
    # Generate data
    data = []
    
    # Header - updated to include aircraft_type for better compatibility and waypoint columns
    header = ['step', 'agent_id', 'aircraft_type', 'latitude', 'longitude', 'altitude', 'heading', 
              'vel_x', 'vel_y', 'vel_z', 'roll', 'pitch', 'yaw', 'battery_level', 
              'signal_strength', 'status', 'custom_data',
              'waypoint_latitude', 'waypoint_longitude', 'waypoint_altitude', 'waypoint_heading']
    data.append(header)

    fire_id_counter = 1

    # Generate TOTAL_STEPS steps
    for step in range(1, TOTAL_STEPS + 1):
        for agent_id, config in agents.items():
            # Skip if agent hasn't started yet
            if step < config['start_step']:
                continue

            state = agent_positions[agent_id][step - 1]

            # Default waypoint columns
            waypoint_lat = ''
            waypoint_lon = ''
            waypoint_alt = ''
            waypoint_heading = ''

            # Calculate which waypoint "block" this step belongs to
            # Steps 1-N use waypoint at step N, steps N+1-N*2 use waypoint at step N*2, etc.
            waypoint_block = ((step - 1) // WAYPOINT_INTERVAL) + 1
            waypoint_step = waypoint_block * WAYPOINT_INTERVAL
            
            # Fill waypoint columns if the waypoint step exists and agent is active
            if (waypoint_step <= TOTAL_STEPS and 
                waypoint_step >= config['start_step'] and
                waypoint_step - 1 < len(agent_positions[agent_id]) and 
                agent_positions[agent_id][waypoint_step - 1] is not None):
                
                future_state = agent_positions[agent_id][waypoint_step - 1]
                waypoint_lat = round(future_state['lat'], 13)
                waypoint_lon = round(future_state['lon'], 13)
                waypoint_alt = round(future_state['altitude'], 1)
                waypoint_heading = round(future_state['heading'], 1)

            row = [
                step,
                agent_id,
                state['type'],  # aircraft_type column
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
                f"Mission {config['mission']} Step {step}",
                waypoint_lat,
                waypoint_lon,
                waypoint_alt,
                waypoint_heading
            ]

            data.append(row)

        if random.random() < FIRE_EVENT_PROBABILITY:
            # gets all agents active in the current step
            active_agents = [
                agent_id for agent_id, config in agents.items() if step >= config['start_step']
            ]

            if active_agents:
                # picks a random agent to spawn the fire near
                random_agent_id = random.choice(active_agents)
                agent_state = agent_positions[random_agent_id][step - 1]

                # generate a random offset from the agent's position
                random_angle = random.uniform(0, 2 * math.pi)
                random_distance = random.uniform(0, FIRE_RADIUS_METERS)

                # convert distance and angle to lat/lon offsets
                lat_offset = (random_distance * math.cos(random_angle)) / LAT_METERS_PER_DEGREE
                lon_offset = (random_distance * math.sin(random_angle)) / LON_METERS_PER_DEGREE

                fire_lat = agent_state['lat'] + lat_offset
                fire_lon = agent_state['lon'] + lon_offset

                fire_row = [
                    step,
                    fire_id_counter,    # fire counter for the ID
                    'FIRE',             # name to identify this row
                    round(fire_lat, 13),
                    round(fire_lon, 13),
                    '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '' # unused columns
                ]
                data.append(fire_row)
                fire_id_counter += 1
    
    # Print summary using actual constants
    print("\nData generation summary:")
    for agent_id, config in agents.items():
        end_step = TOTAL_STEPS
        print(f"- Agent {agent_id} ({config['type']}): {config['speed']} m/s, steps {config['start_step']}-{end_step}")
    print(f"- {SECONDS_PER_STEP} second intervals between steps")
    print(f"- Total steps: {TOTAL_STEPS}")
    print(f"- Noise enabled: {USE_NOISE}")
    print(f"- Compatible with FlatBuffers schemas")
    
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