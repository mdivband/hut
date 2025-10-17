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
    
    # Mission configuration
    TAKEOFF_ALTITUDE = 100.0     # Takeoff target altitude
    CLIMB_ANGLE = 15.0           # Takeoff climb angle in degrees
    MISSION_ALTITUDE = 150.0     # Default mission altitude
    LANDING_ALTITUDE = 0.0       # Landing altitude
    ABORT_ALTITUDE = 50.0        # Abort altitude for landing
    ACCEPT_RADIUS = 10.0         # Waypoint accept radius
    PASS_RADIUS = 5.0           # Waypoint pass radius
    
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

    # Fire parameters
    FIRE_EVENT_PROBABILITY = 0.05  # 5% chance to generate a fire event per step
    MIN_FIRE_DISTANCE_METERS = 1000   # Minimum distance from an agent to spawn a fire
    FIRE_RADIUS_METERS = 5000      # 5km radius around an agent
    N_FIRE_EVENTS = 5               # Max number of fire events to generate
    
    # Fire status progression
    FIRE_CONTAINED_STEP = 20   # Step when Fire 1 and 2 become contained
    FIRE_EXTINGUISHED_STEP = 50  # Step when Fire 1 and 2 become extinguished

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
    
    # Generate mission data
    mission_data = []
    mission_header = ['aircraft_type', 'agent_id', 'mission_elements']
    mission_data.append(mission_header)

    for agent_id, config in agents.items():
        mission_elements = []
        
        # Add TAKEOFF element (starting position)
        start_state = agent_positions[agent_id][0]  # First position for this agent
        takeoff_element = f"TAKEOFF:{CLIMB_ANGLE},{TAKEOFF_ALTITUDE},true"
        mission_elements.append(takeoff_element)
        
        # Add WAYPOINT elements based on waypoint intervals
        waypoint_steps = list(range(WAYPOINT_INTERVAL, TOTAL_STEPS + 1, WAYPOINT_INTERVAL))
        
        for waypoint_step in waypoint_steps:
            # Check if agent is active at this step and waypoint exists
            if (waypoint_step >= config['start_step'] and 
                waypoint_step - 1 < len(agent_positions[agent_id]) and 
                agent_positions[agent_id][waypoint_step - 1] is not None):
                
                waypoint_state = agent_positions[agent_id][waypoint_step - 1]
                waypoint_element = (f"WAYPOINT:{ACCEPT_RADIUS},{PASS_RADIUS},"
                                  f"{waypoint_state['lat']},{waypoint_state['lon']},"
                                  f"{MISSION_ALTITUDE},true,{waypoint_state['heading']}")
                mission_elements.append(waypoint_element)
        
        # Add LAND element (final position)
        # Use the last valid position for this agent
        last_step = min(TOTAL_STEPS, len(agent_positions[agent_id]))
        if last_step > 0:
            final_state = agent_positions[agent_id][last_step - 1]
            land_element = (f"LAND:{ABORT_ALTITUDE},{final_state['lat']},"
                          f"{final_state['lon']},{LANDING_ALTITUDE},true")
            mission_elements.append(land_element)
        
        # Join all mission elements with semicolon
        mission_string = ";".join(mission_elements)
        
        mission_row = [
            config['type'],  # aircraft_type
            agent_id,        # agent_id
            mission_string   # mission_elements
        ]
        mission_data.append(mission_row)
    
    # Track generated fires and their status progression
    generated_fires = {}  # fire_id -> {'step': spawn_step, 'lat': lat, 'lng': lng}
    
    # Generate regular agent and fire data
    agent_data = []
    fire_data = []
    
    # Header - updated to include aircraft_type for better compatibility and waypoint columns
    agent_header = ['step', 'agent_id', 'aircraft_type', 'latitude', 'longitude', 'altitude', 'heading',
              'vel_x', 'vel_y', 'vel_z', 'roll', 'pitch', 'yaw', 'battery_level', 
              'signal_strength', 'status', 'custom_data',
              'waypoint_latitude', 'waypoint_longitude', 'waypoint_altitude', 'waypoint_heading']
    # Updated fire header to include status and remove image
    fire_header = ['step', 'fire_id', 'latitude', 'longitude', 'status']

    agent_data.append(agent_header)
    fire_data.append(fire_header)

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
                waypoint_alt = round(MISSION_ALTITUDE, 1)  # Use mission altitude instead of computed altitude
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

            agent_data.append(row)

        # Generate new fire events
        if random.random() < FIRE_EVENT_PROBABILITY and fire_id_counter <= N_FIRE_EVENTS:
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
                random_distance = random.uniform(MIN_FIRE_DISTANCE_METERS, FIRE_RADIUS_METERS)

                # convert distance and angle to lat/lon offsets
                lat_offset = (random_distance * math.cos(random_angle)) / LAT_METERS_PER_DEGREE
                lon_offset = (random_distance * math.sin(random_angle)) / LON_METERS_PER_DEGREE

                fire_lat = agent_state['lat'] + lat_offset
                fire_lon = agent_state['lon'] + lon_offset

                # Store fire information for status tracking
                generated_fires[fire_id_counter] = {
                    'step': step,
                    'lat': fire_lat,
                    'lng': fire_lon
                }

                fire_id_counter += 1

        # Add fire status updates for all generated fires
        for fire_id, fire_info in generated_fires.items():
            # Determine fire status based on step and fire ID
            fire_status = 0  # Default: ACTIVE
            
            if fire_id in [1, 2]:  # Fire 1 and 2 have special progression
                if step >= FIRE_EXTINGUISHED_STEP:
                    fire_status = 2  # EXTINGUISHED
                elif step >= FIRE_CONTAINED_STEP:
                    fire_status = 1  # CONTAINED
                # else remains ACTIVE (0)
            # Other fires remain ACTIVE throughout

            fire_row = [
                step,
                fire_id,
                round(fire_info['lat'], 13),
                round(fire_info['lng'], 13),
                fire_status
            ]
            fire_data.append(fire_row)
    
    # Print summary using actual constants
    print("\nData generation summary:")
    for agent_id, config in agents.items():
        end_step = TOTAL_STEPS
        print(f"- Agent {agent_id} ({config['type']}): {config['speed']} m/s, steps {config['start_step']}-{end_step}")
    print(f"- {SECONDS_PER_STEP} second intervals between steps")
    print(f"- Total steps: {TOTAL_STEPS}")
    print(f"- Waypoint interval: {WAYPOINT_INTERVAL} steps")
    print(f"- Mission altitude: {MISSION_ALTITUDE}m, Takeoff altitude: {TAKEOFF_ALTITUDE}m")
    print(f"- Fire status progression: Fire 1&2 contained at step {FIRE_CONTAINED_STEP}, extinguished at step {FIRE_EXTINGUISHED_STEP}")
    print(f"- Generated {len(generated_fires)} fire events")
    print(f"- Noise enabled: {USE_NOISE}")
    print(f"- Compatible with FlatBuffers schemas")
    
    return agent_data, fire_data, mission_data

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
    agents_sample_data, fires_sample_data, missions_sample_data = generate_sample_data()
    
    # Save to CSV files
    save_to_csv(agents_sample_data, 'agents_data.csv')
    save_to_csv(fires_sample_data, 'fires_data.csv')
    save_to_csv(missions_sample_data, 'missions_data.csv')