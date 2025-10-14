import csv
import math
import os
import random
from PIL import Image
import base64
import io

def generate_gradient_image_base64(width=50, height=50):
    """ Generates a 500x500 image with a random gradient and returns it as a Base64 string"""
    # Two random colors
    color1 = [random.randint(0, 255), random.randint(0, 255), random.randint(0, 255)]
    color2 = [random.randint(0, 255), random.randint(0, 255), random.randint(0, 255)]

    # Create a new blank image
    img = Image.new('RGB', (width, height))

    for x in range(width):
        # Calculate the ratio for linear interpolation
        ratio = x / (width - 1)

        r = int((1 - ratio) * color1[0] + ratio * color2[0])
        g = int((1 - ratio) * color1[1] + ratio * color2[1])
        b = int((1 - ratio) * color1[2] + ratio * color2[2])

        for y in range(height):
            img.putpixel((x, y), (r, g, b))

    # Save the image to a memory buffer
    buffered = io.BytesIO()
    img.save(buffered, format="PNG")

    # Get the byte value of the image and encode it in Base64
    img_bytes = buffered.getvalue()
    return base64.b64encode(img_bytes).decode('utf-8')


# a hue shifting algorithm
def shift_hue_of_base64_image(base64_string, shift_amount=32):
    """
    Decodes a Base64 image, shifts its hue using Pillow, and re-encodes it.
    """
    try:
        img_bytes = base64.b64decode(base64_string)
        img = Image.open(io.BytesIO(img_bytes))
        img_hsv = img.convert('HSV') # RGB to HSV to easily manipulate hue
        h, s, v = img_hsv.split()
        h_shifted = h.point(lambda i: (i + shift_amount) % 256)
        img_hsv_shifted = Image.merge('HSV', (h_shifted, s, v)) # merge channels
        img_rgb_shifted = img_hsv_shifted.convert('RGB') # convert back to RGB
        buffered = io.BytesIO()
        img_rgb_shifted.save(buffered, format="PNG")
        new_img_bytes = buffered.getvalue()
        return base64.b64encode(new_img_bytes).decode('utf-8')

    except Exception as e:
        print(f"Error shifting hue: {e}. Returning original image.")
        return base64_string




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
    MIN_FIRE_DISTANCE_METERS = 1000   # Minimum distance from an agent to spawn a fire
    FIRE_RADIUS_METERS = 5000      # 5km radius around an agent
    N_FIRE_EVENTS = 5               # Max number of fire events to generate
    FIRE_UPDATE_INTERVAL_MIN = 5     # Min steps between updates for a fire
    FIRE_UPDATE_INTERVAL_MAX = 20     # Max steps between updates for a fire
    FIRE_UPDATE_POS_VARIATION = 0.001     # How far (in degrees) a fire can "move" during an update

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
    agent_data = []
    fire_data = []

    # Header - updated to include aircraft_type for better compatibility and waypoint columns
    agent_header = ['step', 'agent_id', 'aircraft_type', 'latitude', 'longitude', 'altitude', 'heading',
                    'vel_x', 'vel_y', 'vel_z', 'roll', 'pitch', 'yaw', 'battery_level',
                    'signal_strength', 'status', 'custom_data',
                    'waypoint_latitude', 'waypoint_longitude', 'waypoint_altitude', 'waypoint_heading']
    fire_header = ['step', 'fire_id', 'latitude', 'longitude', 'image']

    agent_data.append(agent_header)
    fire_data.append(fire_header)

    fire_id_counter = 1
    created_fires = [] # keep track of created fires

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

            agent_data.append(row)

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

                ## for now we don't need the fires themself to move
                fire_lat = agent_state['lat'] #+ lat_offset
                fire_lon = agent_state['lon'] #+ lon_offset

                image_base64 = generate_gradient_image_base64()

                fire_row = [
                    step,
                    fire_id_counter,    # fire counter for the ID
                    round(fire_lat, 13),
                    round(fire_lon, 13),
                    image_base64
                ]
                fire_data.append(fire_row)

                created_fires.append({
                    'id': fire_id_counter,
                    'step': step,
                    'lat': fire_lat,
                    'lon': fire_lon,
                    'img': image_base64
                })

                fire_id_counter += 1


    # every N steps, previously generated fires have their image/other data updated, like a real feed
    print("\nGenerating continuous fire update events...")
    for fire in created_fires:
        update_interval = random.randint(FIRE_UPDATE_INTERVAL_MIN, FIRE_UPDATE_INTERVAL_MAX)

        last_event_step = fire['step']
        last_known_lat = fire['lat']
        last_known_lon = fire['lon']
        last_known_image = fire['img']

        update_count = 0

        # loops till TOTAL_STEPS roughly
        while True:
            next_update_step = last_event_step + update_interval

            if next_update_step > TOTAL_STEPS:
                break

            # for now we're skipping actual movement of the fire
            update_lat = last_known_lat #+ random.uniform(-FIRE_UPDATE_POS_VARIATION, FIRE_UPDATE_POS_VARIATION)
            update_lon = last_known_lon #+ random.uniform(-FIRE_UPDATE_POS_VARIATION, FIRE_UPDATE_POS_VARIATION)
            updated_image_base64 = shift_hue_of_base64_image(last_known_image, shift_amount=random.randint(16, 32))

            fire_data.append([next_update_step, fire['id'], round(update_lat, 13), round(update_lon, 13), updated_image_base64])
            update_count += 1

            # keep a track of the last update
            last_event_step = next_update_step
            last_known_lat = update_lat
            last_known_lon = update_lon
            last_known_image = updated_image_base64

        if update_count > 0:
            print(f"- Scheduling {update_count} update(s) for fire ID {fire['id']} at a {update_interval}-step interval.")


    fire_header_row = fire_data[0]
    fire_data_rows = fire_data[1:]
    fire_data_rows.sort(key=lambda x: x[0]) # sort by the step/timestamp for correctness
    fires_sample_data = [fire_header_row] + fire_data_rows

    # Print summary using actual constants
    print("\nData generation summary:")
    for agent_id, config in agents.items():
        end_step = TOTAL_STEPS
        print(f"- Agent {agent_id} ({config['type']}): {config['speed']} m/s, steps {config['start_step']}-{end_step}")
    print(f"- {SECONDS_PER_STEP} second intervals between steps")
    print(f"- Total steps: {TOTAL_STEPS}")
    print(f"- Noise enabled: {USE_NOISE}")
    print(f"- Compatible with FlatBuffers schemas")

    return agent_data, fires_sample_data

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
    agents_sample_data, fires_sample_data = generate_sample_data()

    # Save to CSV file
    save_to_csv(agents_sample_data, 'agents_data.csv')
    save_to_csv(fires_sample_data, 'fires_data.csv')

