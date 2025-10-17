#!/usr/bin/env python3
import zenoh, time, argparse, sys
import json
from collections import defaultdict, deque

# Add the script folder and generated folder to path
import os
script_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(script_dir)
sys.path.append(os.path.join(script_dir, 'flatbuffers', 'generated'))

from utils import *

# Setup logging
logger = setup_logging('fbs_listener.log')

# FlatBuffers imports (will be available after running setup_flatbuffers.py)
FLATBUFFERS_AVAILABLE = check_flatbuffers(type_based=True)
if FLATBUFFERS_AVAILABLE:
    from messages import (PositionMessage, VelocityMessage, HeadingMessage, WaypointMessage, 
                         FireMessage, MissionMessage, TakeoffMessage, LandMessage, 
                         Element, ElementWrapper)
    print("FlatBuffers support enabled", flush=True)
    logger.info("FlatBuffers support enabled")
else:
    logger.warning("FlatBuffers not available - using string format")

# Global storage for aircraft data
aircraft_data = defaultdict(lambda: {
    'position': None,
    'velocity': None,
    'heading': None,
    'waypoint': None,
    'mission': None,
    'last_update': None
})
fire_events = deque(maxlen=200)

# Buffer for recent updates
message_buffer = deque(maxlen=1000)
combine_timeout = 1.0

def velocity_to_speed(velocity_x, velocity_y, velocity_z):
    """Convert velocity components to speed (magnitude)"""
    return (velocity_x**2 + velocity_y**2 + velocity_z**2)**0.5

def parse_topic_key(key_expr):
    """Parse topic key to extract aircraft type and ID"""
    # Expected format: aircraft/[TYPE]/[ID]/[message_type]
    parts = str(key_expr).split('/')
    if len(parts) >= 4 and parts[0] == 'aircraft':
        aircraft_type = get_aircraft_name(parts[1])
        aircraft_id = parts[2]
        message_type = parts[3]
        return aircraft_type, aircraft_id, message_type
    return None, None, None

def deserialize_position_message(data):
    """Deserialize PositionMessage FlatBuffer"""
    try:
        msg = PositionMessage.PositionMessage.GetRootAs(data, 0)
        return {
            "timestamp": msg.Timestamp(),
            "type": msg.Ttype(),
            "id": msg.Id(),
            "latitude": msg.Latitude(),
            "longitude": msg.Longitude(),
            "altitude": msg.Altitude()
        }
    except Exception as e:
        logger.error(f"Failed to deserialize PositionMessage: {e}")
        return {"error": f"Failed to deserialize PositionMessage: {e}"}

def deserialize_velocity_message(data):
    """Deserialize VelocityMessage FlatBuffer"""
    try:
        msg = VelocityMessage.VelocityMessage.GetRootAs(data, 0)
        x = msg.X()
        y = msg.Y()
        z = msg.Z()
        speed = velocity_to_speed(x, y, z)
        return {
            "timestamp": msg.Timestamp(),
            "type": msg.Ttype(),
            "id": msg.Id(),
            "x": x,
            "y": y,
            "z": z,
            "speed": speed
        }
    except Exception as e:
        logger.error(f"Failed to deserialize VelocityMessage: {e}")
        return {"error": f"Failed to deserialize VelocityMessage: {e}"}

def deserialize_heading_message(data):
    """Deserialize HeadingMessage FlatBuffer"""
    try:
        msg = HeadingMessage.HeadingMessage.GetRootAs(data, 0)
        return {
            "timestamp": msg.Timestamp(),
            "type": msg.Ttype(),
            "id": msg.Id(),
            "heading": msg.Heading()
        }
    except Exception as e:
        logger.error(f"Failed to deserialize HeadingMessage: {e}")
        return {"error": f"Failed to deserialize HeadingMessage: {e}"}

def deserialize_waypoint_message(data):
    """Deserialize WaypointMessage FlatBuffer"""
    try:
        msg = WaypointMessage.WaypointMessage.GetRootAs(data, 0)
        return {
            "timestamp": msg.Timestamp(),
            "type": msg.Ttype(),
            "id": msg.Id(),
            "latitude": msg.Latitude(),
            "longitude": msg.Longitude(),
            "altitude": msg.Altitude(),
            "heading": msg.Heading()
        }
    except Exception as e:
        logger.error(f"Failed to deserialize WaypointMessage: {e}")
        return {"error": f"Failed to deserialize WaypointMessage: {e}"}

def deserialize_mission_element(element_wrapper):
    """Deserialize a single mission element from ElementWrapper"""
    try:
        element_type = element_wrapper.EType()
        element_table = element_wrapper.E()
        
        if element_type == Element.Element.TakeoffMessage:
            takeoff_msg = TakeoffMessage.TakeoffMessage()
            takeoff_msg.Init(element_table.Bytes, element_table.Pos)
            return {
                "type": "TAKEOFF",
                "timestamp": takeoff_msg.Timestamp(),
                "aircraft_type": takeoff_msg.Ttype(),
                "id": takeoff_msg.Id(),
                "climb_angle": takeoff_msg.ClimbAngle(),
                "altitude": takeoff_msg.Altitude(),
                "autocontinue": takeoff_msg.Autocontinue()
            }
        elif element_type == Element.Element.WaypointMessage:
            waypoint_msg = WaypointMessage.WaypointMessage()
            waypoint_msg.Init(element_table.Bytes, element_table.Pos)
            return {
                "type": "WAYPOINT",
                "timestamp": waypoint_msg.Timestamp(),
                "aircraft_type": waypoint_msg.Ttype(),
                "id": waypoint_msg.Id(),
                "accept_radius": waypoint_msg.AcceptRadius(),
                "pass_radius": waypoint_msg.PassRadius(),
                "latitude": waypoint_msg.Latitude(),
                "longitude": waypoint_msg.Longitude(),
                "altitude": waypoint_msg.Altitude(),
                "autocontinue": waypoint_msg.Autocontinue(),
                "heading": waypoint_msg.Heading()
            }
        elif element_type == Element.Element.LandMessage:
            land_msg = LandMessage.LandMessage()
            land_msg.Init(element_table.Bytes, element_table.Pos)
            return {
                "type": "LAND",
                "timestamp": land_msg.Timestamp(),
                "aircraft_type": land_msg.Ttype(),
                "id": land_msg.Id(),
                "abort_altitude": land_msg.AbortAltitude(),
                "latitude": land_msg.Latitude(),
                "longitude": land_msg.Longitude(),
                "altitude": land_msg.Altitude(),
                "autocontinue": land_msg.Autocontinue()
            }
        else:
            return {"error": f"Unknown element type: {element_type}"}
    except Exception as e:
        logger.error(f"Failed to deserialize mission element: {e}")
        return {"error": f"Failed to deserialize mission element: {e}"}

def extract_waypoints_from_mission(mission_elements):
    """Extract waypoints from mission elements in simplified format"""
    waypoints = []
    
    for element in mission_elements:
        element_type = element.get("type")

        # We do not add takeoff elements as waypoints  
        if element_type == "WAYPOINT":
            # Convert waypoint to old format
            waypoint = {
                "timestamp": element.get("timestamp"),
                "type": element.get("aircraft_type"),
                "id": element.get("id"),
                "latitude": element.get("latitude"),
                "longitude": element.get("longitude"),
                "altitude": element.get("altitude"),
                "heading": element.get("heading")
            }
            waypoints.append(waypoint)
            
        elif element_type == "LAND":
            # Check if land position is different from last waypoint
            land_lat = element.get("latitude")
            land_lng = element.get("longitude")
            
            # If we have waypoints and the land position is different, add it
            if waypoints:
                last_waypoint = waypoints[-1]
                # Check if land position is significantly different (allow small rounding differences)
                if (abs(land_lat - last_waypoint["latitude"]) > 0.000001 or 
                    abs(land_lng - last_waypoint["longitude"]) > 0.000001):
                    land_waypoint = {
                        "timestamp": element.get("timestamp"),
                        "type": element.get("aircraft_type"),
                        "id": element.get("id"),
                        "latitude": land_lat,
                        "longitude": land_lng,
                        "altitude": element.get("altitude", 0.0),
                        "heading": 0.0  # Land heading not specified
                    }
                    waypoints.append(land_waypoint)
            else:
                # No waypoints yet, add land as waypoint
                land_waypoint = {
                    "timestamp": element.get("timestamp"),
                    "type": element.get("aircraft_type"),
                    "id": element.get("id"),
                    "latitude": land_lat,
                    "longitude": land_lng,
                    "altitude": element.get("altitude", 0.0),
                    "heading": 0.0
                }
                waypoints.append(land_waypoint)
    
    return waypoints

def deserialize_mission_message(data, full_mission=True):
    """Deserialize MissionMessage FlatBuffer"""
    try:
        msg = MissionMessage.MissionMessage.GetRootAs(data, 0)
        
        mission_elements = []
        for i in range(msg.MissionLength()):
            element_wrapper = msg.Mission(i)
            element_data = deserialize_mission_element(element_wrapper)
            if "error" not in element_data:
                mission_elements.append(element_data)
            else:
                logger.error(f"Error in mission element {i}: {element_data['error']}")
        
        if full_mission:
            # Return full mission data
            takeoff_count = sum(1 for elem in mission_elements 
                                if elem.get("type") == "TAKEOFF")
            waypoint_count = sum(1 for elem in mission_elements 
                                if elem.get("type") == "WAYPOINT")
            land_count = sum(1 for elem in mission_elements 
                             if elem.get("type") == "LAND")
            return {
                "timestamp": msg.Timestamp(),
                "aircraft_type": msg.Ttype(),
                "id": msg.Id(),
                "mission_elements": mission_elements,
                "element_count": {
                    "takeoff": takeoff_count,
                    "waypoints": waypoint_count,
                    "land": land_count,
                    "total": len(mission_elements)
                }
            }
        else:
            # Extract waypoints only - simplified style
            waypoints = extract_waypoints_from_mission(mission_elements)
            return {
                "timestamp": msg.Timestamp(),
                "aircraft_type": msg.Ttype(),
                "id": msg.Id(),
                "waypoints": waypoints,
            }
            
    except Exception as e:
        logger.error(f"Failed to deserialize MissionMessage: {e}")
        return {"error": f"Failed to deserialize MissionMessage: {e}"}

def deserialize_fire_message(data):
    """Deserialize FireMessage FlatBuffer"""
    try:
        msg = FireMessage.FireMessage.GetRootAs(data, 0)
        return {
            "timestamp": msg.Timestamp(),
            "id": msg.Id(),
            "latitude": msg.Latitude(),
            "longitude": msg.Longitude(),
            "status": msg.Status()  # Add this line
        }
    except Exception as e:
        logger.error(f"Failed to deserialize FireMessage: {e}")
        return {"error": f"Failed to deserialize FireMessage: {e}"}

def combine_all_data(data_timeout, full_mission=True):
    """Combine collected aircraft data into the desired JSON format"""
    current_time = int(time.time() * 1000)  # Current timestamp in milliseconds

    # Extract message metadata
    result = {
        "timestamp": current_time,
        "message_id": f"combined_message_{current_time}",
        "source": "aircraft_listener",
        "agents": [],
        "fires": []
    }

    current_time_sec = time.time()

    for aircraft_key, data in aircraft_data.items():
        aircraft_type, aircraft_id = aircraft_key.split('_', 1)
        aircraft_id_str = f"{aircraft_type}-{aircraft_id}"

        # Only include aircraft with recent data (within user-specified timeout)
        if (data['last_update'] and
            current_time_sec - data['last_update'] < data_timeout):

            agent_data = {
                "agent_id": aircraft_id_str,
                "battery_level": 0,  # Default value
                "signal_strength": 0,  # Default value
                "status": 0,  # Default value
                "altitude": 50,  # Default altitude
                "heading": 0,  # Default heading
                "custom_data": ""  # Default empty string
            }

            # Extract coordinate data from position
            if data['position']:
                agent_data["coordinate"] = {
                    "lat": data['position']['latitude'],
                    "lng": data['position']['longitude']
                }
                agent_data["altitude"] = data['position']['altitude']
            else:
                agent_data["coordinate"] = {
                    "lat": 0.0,
                    "lng": 0.0
                }

            # Extract velocity data and calculate speed
            if data['velocity']:
                velocity_x = data['velocity']['x']
                velocity_y = data['velocity']['y']
                velocity_z = data['velocity']['z']
                agent_data["speed"] = velocity_to_speed(
                    velocity_x, velocity_y, velocity_z)
            else:
                agent_data["speed"] = 0.0

            # Extract heading data
            if data['heading']:
                agent_data["heading"] = data['heading']['heading']

            # Extract waypoint data if available (old style single waypoint)
            if data.get('waypoint'):
                agent_data["waypoint"] = {
                    "lat": data['waypoint']['latitude'],
                    "lng": data['waypoint']['longitude'],
                    "altitude": data['waypoint']['altitude'],
                    "heading": data['waypoint']['heading']
                }

            # Extract mission data if available
            if data.get('mission'):
                if full_mission:
                    # Include full mission data and metadata
                    agent_data["mission"] = data['mission']
                else:
                    # Include waypoints array (simplified style)
                    if "waypoints" in data['mission']:
                        agent_data["waypoints"] = data['mission']['waypoints']

            result["agents"].append(agent_data)

    unique_fires = {}
    for event in list(fire_events):
        if current_time_sec - event['timestamp'] < data_timeout:
            fire_id = event['data']['id']
            # Only keep the most recent event for each fire_id
            if fire_id not in unique_fires or event['timestamp'] > unique_fires[fire_id]['timestamp']:
                unique_fires[fire_id] = event
        else:
            # Remove old events
            if fire_events and fire_events[0] == event:
                fire_events.popleft()

    # Add unique fires to result
    for fire_event in unique_fires.values():
        result["fires"].append(fire_event['data'])

    return result

def update_aircraft_data(aircraft_type, aircraft_id, message_type, message_data):
    """Update the global aircraft data store"""
    aircraft_key = f"{aircraft_type}_{aircraft_id}"
    current_time = time.time()

    if message_type == "position" and "error" not in message_data:
        aircraft_data[aircraft_key]['position'] = message_data
        aircraft_data[aircraft_key]['last_update'] = current_time
    elif message_type == "velocity" and "error" not in message_data:
        aircraft_data[aircraft_key]['velocity'] = message_data
        aircraft_data[aircraft_key]['last_update'] = current_time
    elif message_type == "heading" and "error" not in message_data:
        aircraft_data[aircraft_key]['heading'] = message_data
        aircraft_data[aircraft_key]['last_update'] = current_time
    elif message_type == "waypoint" and "error" not in message_data:
        aircraft_data[aircraft_key]['waypoint'] = message_data
        aircraft_data[aircraft_key]['last_update'] = current_time
    elif message_type == "set_mission" and "error" not in message_data:
        aircraft_data[aircraft_key]['mission'] = message_data
        aircraft_data[aircraft_key]['last_update'] = current_time

def format_output(data, is_flatbuffer=False):
    """Format the output data for display as JSON"""
    if is_flatbuffer:
        return json.dumps(data, indent=2)
    else:
        string_data = {"type": "string", "data": str(data)}
        return json.dumps(string_data, indent=2)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='DDS Listener for Aircraft Messages with FlatBuffers support')
    parser.add_argument('--wait_time', type=float, default=2.0,
                        help='Maximum wait time in seconds (default: 2.0)')
    parser.add_argument('--continuous', action='store_true',
                        help='Run continuously until killed')
    parser.add_argument('--format', choices=['auto', 'flatbuffer', 'string'],
                        default='auto',
                        help='Data format to expect (default: auto)')
    parser.add_argument('--combine_interval', type=float, default=0.5,
                        help='Interval to output combined data (default: 0.5)')
    parser.add_argument('--log_messages', action='store_true',
                        default=False, help='Log received messages to logger')
    parser.add_argument('--data_timeout', type=float, default=5.0,
                        help='Maximum age of data to include in combined output in seconds (default: 5.0)')
    parser.add_argument('--full_mission', action='store_true',
                        default=False, help='Include full mission data instead of extracting waypoints (default: False)')
    args = parser.parse_args()

    data_received = False
    is_flatbuffer = False
    last_check_time = time.time()
    last_combine_time = time.time()
    no_data_warning_interval = 5.0

    def listener(sample):
        global data_received, last_check_time, is_flatbuffer
        data_received = True
        last_check_time = time.time()

        try:
            # Handle fire events separately
            if str(sample.key_expr) == 'fires/events':
                payload_bytes = sample.payload.to_bytes()
                message_data = deserialize_fire_message(payload_bytes)
                if "error" not in message_data:
                    logger.info(f"Received fire event for fire ID {message_data['id']}")
                    # Append a dictionary containing the data and a timestamp for expiry checks
                    fire_events.append({'data': message_data, 'timestamp': time.time()})
                else:
                    logger.error(f"Invalid fire event data: {message_data['error']}")
                return # We've handled the fire event, so we can exit now.

            # Parse the topic key for aircraft messages
            aircraft_type, aircraft_id, message_type = parse_topic_key(sample.key_expr)

            if not aircraft_type or not aircraft_id or not message_type:
                logger.warning(f"Invalid topic format: {sample.key_expr}")
                return

            # Try to determine the data format
            is_flatbuffer = False
            message_data = None

            if args.format == 'flatbuffer' or (
                args.format == 'auto' and FLATBUFFERS_AVAILABLE):

                try:
                    payload_bytes = sample.payload.to_bytes()
                    if len(payload_bytes) > 4:
                        # Deserialize based on message type
                        if message_type == "position":
                            message_data = deserialize_position_message(payload_bytes)
                        elif message_type == "velocity":
                            message_data = deserialize_velocity_message(payload_bytes)
                        elif message_type == "heading":
                            message_data = deserialize_heading_message(payload_bytes)
                        elif message_type == "waypoint":
                            message_data = deserialize_waypoint_message(payload_bytes)
                        elif message_type == "set_mission":
                            message_data = deserialize_mission_message(payload_bytes, args.full_mission)
                        else:
                            logger.error(f"Unknown message type: {message_type}")
                            raise Exception(f"Unknown message type: {message_type}")

                        if "error" not in message_data:
                            is_flatbuffer = True
                            
                            # Special logging for mission messages
                            if message_type == "set_mission":
                                element_count = message_data.get('element_count', {})
                                if args.full_mission:
                                    logger.info(f"Received mission for {aircraft_type}/{aircraft_id}: "
                                              f"{element_count.get('takeoff', 0)} takeoff, "
                                              f"{element_count.get('waypoints', 0)} waypoints, "
                                              f"{element_count.get('land', 0)} land elements")
                                else:
                                    waypoint_count = len(message_data.get('waypoints', []))
                                    logger.info(f"Received mission for {aircraft_type}/{aircraft_id}: "
                                              f"extracted {waypoint_count} waypoints")
                            else:
                                logger.info(f"Received {message_type} for {aircraft_type}/{aircraft_id}")
                            
                            # Log messages as well if enabled
                            if args.log_messages:
                                logger.info(f"{str.capitalize(message_type)} Message: "
                                            f"{json.dumps(message_data, indent=2)}")

                            # Update aircraft data store
                            update_aircraft_data(aircraft_type, aircraft_id, message_type, message_data)
                        else:
                            logger.error(f"Invalid FlatBuffer data for {message_type}: {message_data['error']}")
                            raise Exception("Invalid FlatBuffer data")
                    else:
                        logger.error("Data too small for FlatBuffer")
                        raise Exception("Data too small for FlatBuffer")
                except Exception as e:
                    is_flatbuffer = False
                    logger.error(f"FlatBuffer parsing failed: {e}")

            if not is_flatbuffer:
                # Handle as string data
                payload_str = sample.payload.to_string()
                if payload_str:
                    formatted_output = format_output(payload_str, False)
                    logger.info(f"String Data from {sample.key_expr}:")
                    logger.info(formatted_output)
                else:
                    logger.warning(f"Empty data from {sample.key_expr}")

        except Exception as e:
            logger.error(f"Error processing data from '{sample.key_expr}': {e}")

    try:
        with zenoh.open(zenoh.Config()) as session:
            if args.continuous:
                format_msg = f" (format: {args.format})" if args.format != 'auto' else ""
                mission_msg = f" (full_mission: {args.full_mission})"
                msg = f"Aircraft DDS Listener started in continuous mode{format_msg}{mission_msg}"
                print(msg, flush=True)
                logger.info(msg)
            else:
                msg = f"Aircraft DDS Listener started, waiting for {args.wait_time} seconds..."
                print(msg, flush=True)
                logger.info(msg)
            # Print the start time
            msg = f"Start time: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())}"
            print(msg, flush=True)
            logger.info(msg)

            # Subscribe to all aircraft topics including missions
            position_sub = session.declare_subscriber('aircraft/*/*/position', listener)
            velocity_sub = session.declare_subscriber('aircraft/*/*/velocity', listener)
            heading_sub = session.declare_subscriber('aircraft/*/*/heading', listener)
            waypoint_sub = session.declare_subscriber('aircraft/*/*/waypoint', listener)
            mission_sub = session.declare_subscriber('aircraft/*/*/set_mission', listener)
            fire_sub = session.declare_subscriber('fires/events', listener)

            if args.continuous:
                try:
                    while True:
                        time.sleep(0.1)  # Check every 0.1 seconds
                        current_time = time.time()

                        # Output combined data at intervals
                        if current_time - last_combine_time >= args.combine_interval:
                            combined_data = combine_all_data(args.data_timeout, args.full_mission)
                            if combined_data["agents"] or combined_data["fires"]:
                                agent_count = len(combined_data['agents'])
                                fire_count = len(combined_data['fires'])
                                
                                # Count agents with missions/waypoints
                                if args.full_mission:
                                    agents_with_missions = sum(1 for agent in combined_data['agents'] 
                                                             if agent.get('mission'))
                                    mission_info = f" ({agents_with_missions} with missions)" if agents_with_missions > 0 else ""
                                else:
                                    agents_with_waypoints = sum(1 for agent in combined_data['agents'] 
                                                              if agent.get('waypoints'))
                                    mission_info = f" ({agents_with_waypoints} with waypoints)" if agents_with_waypoints > 0 else ""
                                
                                parts = []
                                if agent_count > 0:
                                    parts.append(f"{agent_count} agent{'s' if agent_count != 1 else ''}{mission_info}")
                                if fire_count > 0:
                                    parts.append(f"{fire_count} fire event{'s' if fire_count != 1 else ''}")

                                msg = f"FlatBuffer Data received for { ' and '.join(parts) }"
                                print(f"{msg}:", flush=True)
                                print(format_output(combined_data, True), flush=True)
                                logger.info(msg)
                            else:
                                msg = f"No data received to combine - publishers may not be active"
                                logger.warning(msg)
                                pretty_time = time.strftime(
                                    '%Y-%m-%d %H:%M:%S', time.localtime(current_time))
                                print(f"{pretty_time}: {msg}", flush=True)

                            # Update last combine time
                            last_combine_time = current_time

                except KeyboardInterrupt:
                    print("Aircraft DDS Listener stopped by user", flush=True)
            else:
                time.sleep(args.wait_time)

                # Output combined data
                combined_data = combine_all_data(args.data_timeout, args.full_mission)
                if combined_data["agents"]:
                    msg = f"FlatBuffer Data received and combined for {len(combined_data['agents'])} "
                    msg += f"agent{'s' if len(combined_data['agents']) != 1 else ''}"
                    print(f"{msg}:", flush=True)
                    print(format_output(combined_data, is_flatbuffer), flush=True)
                    logger.info(msg)
                else:
                    msg = "No data received to combine - publishers may not be active"
                    pretty_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(time.time()))
                    print(f"{pretty_time}: {msg}", flush=True)
                    logger.warning(msg)

    except Exception as e:
        print(f"Error connecting to DDS: {e}", flush=True)
        print("Publishers are not active or connection failed", flush=True)
        logger.error(f"Error connecting to DDS: {e}")