#!/usr/bin/env python3
"""
DDS Publisher with FlatBuffers support for Aircraft Messages
This script publishes position, velocity, heading, and mission data using separate topics
"""

import zenoh
import time
import random
import argparse
import csv
import os
import sys
from collections import defaultdict

# Add the script folder and generated folder to path
script_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(script_dir)
sys.path.append(os.path.join(script_dir, 'flatbuffers', 'generated'))

# Import utility functions
from utils import *

# Check if FlatBuffers are available
FLATBUFFERS_AVAILABLE = check_flatbuffers()
if FLATBUFFERS_AVAILABLE:
    import flatbuffers
    from messages import (PositionMessage, VelocityMessage, HeadingMessage, 
                         MissionMessage, WaypointMessage, TakeoffMessage, LandMessage,
                         FireMessage, Element, ElementWrapper, WaypointType)
    print("FlatBuffers support enabled")

# Global dictionary to store publishers
publishers_cache = {}

# Global dictionary to store complete missions for each aircraft
aircraft_missions = {}

# Configuration for mission republishing
MISSION_REPUBLISH_INTERVAL = 30  # Republish missions every 30 steps

def create_position_message(aircraft_type, aircraft_id, lat, lng, alt):
    """Create a PositionMessage FlatBuffer"""
    builder = flatbuffers.Builder(256)
    aircraft_type = get_aircraft_type(aircraft_type)
    PositionMessage.PositionMessageStart(builder)
    PositionMessage.PositionMessageAddTimestamp(builder, int(time.time() * 1000))
    PositionMessage.PositionMessageAddTtype(builder, aircraft_type)
    PositionMessage.PositionMessageAddId(builder, int(aircraft_id) if aircraft_id.isdigit() else 1)
    PositionMessage.PositionMessageAddLatitude(builder, lat)
    PositionMessage.PositionMessageAddLongitude(builder, lng)
    PositionMessage.PositionMessageAddAltitude(builder, alt)
    message_offset = PositionMessage.PositionMessageEnd(builder)
    builder.Finish(message_offset)
    return builder.Output()

def create_velocity_message(aircraft_type, aircraft_id, vel_x, vel_y, vel_z):
    """Create a VelocityMessage FlatBuffer"""
    builder = flatbuffers.Builder(256)
    aircraft_type = get_aircraft_type(aircraft_type)
    VelocityMessage.VelocityMessageStart(builder)
    VelocityMessage.VelocityMessageAddTimestamp(builder, int(time.time() * 1000))
    VelocityMessage.VelocityMessageAddTtype(builder, aircraft_type)
    VelocityMessage.VelocityMessageAddId(builder, int(aircraft_id) if aircraft_id.isdigit() else 1)
    VelocityMessage.VelocityMessageAddX(builder, vel_x)
    VelocityMessage.VelocityMessageAddY(builder, vel_y)
    VelocityMessage.VelocityMessageAddZ(builder, vel_z)
    message_offset = VelocityMessage.VelocityMessageEnd(builder)
    builder.Finish(message_offset)
    return builder.Output()

def create_heading_message(aircraft_type, aircraft_id, heading):
    """Create a HeadingMessage FlatBuffer"""
    builder = flatbuffers.Builder(256)
    aircraft_type = get_aircraft_type(aircraft_type)
    HeadingMessage.HeadingMessageStart(builder)
    HeadingMessage.HeadingMessageAddTimestamp(builder, int(time.time() * 1000))
    HeadingMessage.HeadingMessageAddTtype(builder, aircraft_type)
    HeadingMessage.HeadingMessageAddId(builder, int(aircraft_id) if aircraft_id.isdigit() else 1)
    HeadingMessage.HeadingMessageAddHeading(builder, heading)
    message_offset = HeadingMessage.HeadingMessageEnd(builder)
    builder.Finish(message_offset)
    return builder.Output()

def create_takeoff_element(builder, aircraft_type, aircraft_id, climb_angle, altitude, autocontinue):
    """Create a TakeoffMessage element"""
    aircraft_type_enum = get_aircraft_type(aircraft_type)
    TakeoffMessage.TakeoffMessageStart(builder)
    TakeoffMessage.TakeoffMessageAddTimestamp(builder, int(time.time() * 1000))
    TakeoffMessage.TakeoffMessageAddTtype(builder, aircraft_type_enum)
    TakeoffMessage.TakeoffMessageAddId(builder, int(aircraft_id) if aircraft_id.isdigit() else 1)
    TakeoffMessage.TakeoffMessageAddClimbAngle(builder, climb_angle)
    TakeoffMessage.TakeoffMessageAddAltitude(builder, altitude)
    TakeoffMessage.TakeoffMessageAddAutocontinue(builder, autocontinue)
    return TakeoffMessage.TakeoffMessageEnd(builder)

def create_waypoint_element(builder, aircraft_type, aircraft_id, accept_radius, pass_radius, lat, lng, alt, autocontinue, heading):
    """Create a WaypointMessage element"""
    aircraft_type_enum = get_aircraft_type(aircraft_type)
    WaypointMessage.WaypointMessageStart(builder)
    WaypointMessage.WaypointMessageAddTimestamp(builder, int(time.time() * 1000))
    WaypointMessage.WaypointMessageAddTtype(builder, aircraft_type_enum)
    WaypointMessage.WaypointMessageAddId(builder, int(aircraft_id) if aircraft_id.isdigit() else 1)
    WaypointMessage.WaypointMessageAddAcceptRadius(builder, accept_radius)
    WaypointMessage.WaypointMessageAddPassRadius(builder, pass_radius)
    WaypointMessage.WaypointMessageAddLatitude(builder, lat)
    WaypointMessage.WaypointMessageAddLongitude(builder, lng)
    WaypointMessage.WaypointMessageAddAltitude(builder, alt)
    WaypointMessage.WaypointMessageAddAutocontinue(builder, autocontinue)
    WaypointMessage.WaypointMessageAddHeading(builder, heading)
    return WaypointMessage.WaypointMessageEnd(builder)

def create_land_element(builder, aircraft_type, aircraft_id, abort_altitude, lat, lng, alt, autocontinue):
    """Create a LandMessage element"""
    aircraft_type_enum = get_aircraft_type(aircraft_type)
    LandMessage.LandMessageStart(builder)
    LandMessage.LandMessageAddTimestamp(builder, int(time.time() * 1000))
    LandMessage.LandMessageAddTtype(builder, aircraft_type_enum)
    LandMessage.LandMessageAddId(builder, int(aircraft_id) if aircraft_id.isdigit() else 1)
    LandMessage.LandMessageAddAbortAltitude(builder, abort_altitude)
    LandMessage.LandMessageAddLatitude(builder, lat)
    LandMessage.LandMessageAddLongitude(builder, lng)
    LandMessage.LandMessageAddAltitude(builder, alt)
    LandMessage.LandMessageAddAutocontinue(builder, autocontinue)
    return LandMessage.LandMessageEnd(builder)

def create_mission_message(aircraft_type, aircraft_id, mission_elements):
    """
    Create a MissionMessage FlatBuffer containing the complete mission
    mission_elements: list of tuples (element_type, element_data)
    element_type: "TAKEOFF", "WAYPOINT", or "LAND"
    element_data: tuple with element-specific parameters
    """
    builder = flatbuffers.Builder(2048)  # Increased buffer size for complete missions
    aircraft_type_enum = get_aircraft_type(aircraft_type)
    
    # Create mission elements
    element_offsets = []
    for element_type, element_data in mission_elements:
        if element_type == "TAKEOFF":
            climb_angle, altitude, autocontinue = element_data
            takeoff_offset = create_takeoff_element(builder, aircraft_type, aircraft_id, climb_angle, altitude, autocontinue)
            
            ElementWrapper.ElementWrapperStart(builder)
            ElementWrapper.ElementWrapperAddEType(builder, Element.Element.TakeoffMessage)
            ElementWrapper.ElementWrapperAddE(builder, takeoff_offset)
            wrapper_offset = ElementWrapper.ElementWrapperEnd(builder)
            element_offsets.append(wrapper_offset)
            
        elif element_type == "WAYPOINT":
            accept_radius, pass_radius, lat, lng, alt, autocontinue, heading = element_data
            waypoint_offset = create_waypoint_element(builder, aircraft_type, aircraft_id, accept_radius, pass_radius, lat, lng, alt, autocontinue, heading)
            
            ElementWrapper.ElementWrapperStart(builder)
            ElementWrapper.ElementWrapperAddEType(builder, Element.Element.WaypointMessage)
            ElementWrapper.ElementWrapperAddE(builder, waypoint_offset)
            wrapper_offset = ElementWrapper.ElementWrapperEnd(builder)
            element_offsets.append(wrapper_offset)
            
        elif element_type == "LAND":
            abort_altitude, lat, lng, alt, autocontinue = element_data
            land_offset = create_land_element(builder, aircraft_type, aircraft_id, abort_altitude, lat, lng, alt, autocontinue)
            
            ElementWrapper.ElementWrapperStart(builder)
            ElementWrapper.ElementWrapperAddEType(builder, Element.Element.LandMessage)
            ElementWrapper.ElementWrapperAddE(builder, land_offset)
            wrapper_offset = ElementWrapper.ElementWrapperEnd(builder)
            element_offsets.append(wrapper_offset)
    
    # Create mission vector - fixed deprecation warning
    MissionMessage.MissionMessageStartMissionVector(builder, len(element_offsets))
    for element_offset in reversed(element_offsets):
        builder.PrependUOffsetTRelative(element_offset)
    mission_vector = builder.EndVector()  # Fixed: removed deprecated numElems parameter
    
    # Create mission message
    MissionMessage.MissionMessageStart(builder)
    MissionMessage.MissionMessageAddTimestamp(builder, int(time.time() * 1000))
    MissionMessage.MissionMessageAddTtype(builder, aircraft_type_enum)
    MissionMessage.MissionMessageAddId(builder, int(aircraft_id) if aircraft_id.isdigit() else 1)
    MissionMessage.MissionMessageAddMission(builder, mission_vector)
    message_offset = MissionMessage.MissionMessageEnd(builder)
    builder.Finish(message_offset)
    return builder.Output()

def create_fire_message(fire_id, lat, lng, status):
    """Create a FireMessage FlatBuffer"""
    builder = flatbuffers.Builder(1024)

    FireMessage.FireMessageStart(builder)
    FireMessage.FireMessageAddTimestamp(builder, int(time.time() * 1000))
    FireMessage.FireMessageAddId(builder, int(fire_id))
    FireMessage.FireMessageAddLatitude(builder, lat)
    FireMessage.FireMessageAddLongitude(builder, lng)
    FireMessage.FireMessageAddStatus(builder, status)
    message_offset = FireMessage.FireMessageEnd(builder)
    builder.Finish(message_offset)
    return builder.Output()

def get_active_aircraft_from_csv(csv_data):
    """Extract aircraft start steps from CSV data to determine when they become active"""
    aircraft_info = {}
    
    for step_data in csv_data.values():
        for row in step_data:
            if row.get('type') not in ['FIRE', 'MISSION']:
                aircraft_key = f"{row['aircraft_type']}/{row['agent_id']}"
                step = int(row.get('step', 0))
                
                if aircraft_key not in aircraft_info:
                    aircraft_info[aircraft_key] = {
                        'start_step': step,
                        'aircraft_type': row['aircraft_type'],
                        'agent_id': row['agent_id']
                    }
                else:
                    # Update start step to minimum seen
                    aircraft_info[aircraft_key]['start_step'] = min(
                        aircraft_info[aircraft_key]['start_step'], step)
    
    return aircraft_info

def load_csv_data(agent_csv_path, fire_csv_path, mission_csv_path):
    """Load and merge agent, fire, and mission data, building complete missions per aircraft."""
    merged_data = defaultdict(list)

    try:
        # Load agent data
        with open(agent_csv_path, 'r', newline='') as csvfile:
            reader = csv.DictReader(csvfile)
            for row in reader:
                step = int(row['step'])
                row['type'] = row['aircraft_type']
                merged_data[step].append(row)
        print(f"Loaded {sum(len(v) for v in merged_data.values())} agent data rows.")

        # Load fire data
        with open(fire_csv_path, 'r', newline='') as csvfile:
            reader = csv.DictReader(csvfile)
            for row in reader:
                step = int(row['step'])
                row['type'] = 'FIRE'
                merged_data[step].append(row)
        print(f"Loaded and merged fire data.")

        # Load and process mission data to build complete missions
        if os.path.exists(mission_csv_path):
            with open(mission_csv_path, 'r', newline='') as csvfile:
                reader = csv.DictReader(csvfile)
                for row in reader:
                    aircraft_key = f"{row['aircraft_type']}/{row['agent_id']}"
                    mission_elements = parse_mission_elements(row['mission_elements'])
                    
                    # Store complete mission for this aircraft
                    aircraft_missions[aircraft_key] = {
                        'aircraft_type': row['aircraft_type'],
                        'agent_id': row['agent_id'],
                        'mission_elements': mission_elements
                    }
                    
            print(f"Loaded complete missions for {len(aircraft_missions)} aircraft.")
        else:
            print(f"Mission CSV file not found: {mission_csv_path}")

        return merged_data
    except Exception as e:
        print(f"Error loading or merging CSV files: {e}")
        return None

def parse_mission_elements(mission_string):
    """
    Parse mission elements from CSV string format
    Expected format: "TAKEOFF:climb_angle,altitude,autocontinue;WAYPOINT:accept_radius,pass_radius,lat,lng,alt,autocontinue,heading;LAND:abort_altitude,lat,lng,alt,autocontinue"
    """
    elements = []
    if not mission_string or mission_string.strip() == '':
        return elements
    
    element_strings = mission_string.split(';')
    for element_str in element_strings:
        if ':' not in element_str:
            continue
        
        element_type, params_str = element_str.split(':', 1)
        params = params_str.split(',')
        
        if element_type == "TAKEOFF":
            climb_angle = float(params[0])
            altitude = float(params[1])
            autocontinue = params[2].lower() == 'true'
            elements.append((element_type, (climb_angle, altitude, autocontinue)))
            
        elif element_type == "WAYPOINT":
            accept_radius = float(params[0])
            pass_radius = float(params[1])
            lat = float(params[2])
            lng = float(params[3])
            alt = float(params[4])
            autocontinue = params[5].lower() == 'true'
            heading = float(params[6])
            elements.append((element_type, (accept_radius, pass_radius, lat, lng, alt, autocontinue, heading)))
            
        elif element_type == "LAND":
            abort_altitude = float(params[0])
            lat = float(params[1])
            lng = float(params[2])
            alt = float(params[3])
            autocontinue = params[4].lower() == 'true'
            elements.append((element_type, (abort_altitude, lat, lng, alt, autocontinue)))
    
    return elements

def publish_aircraft_data(
        session, aircraft_type, aircraft_id, position_data, 
        velocity_data, heading_data, format_type):
    """Publish aircraft data to separate topics"""
    position_topic = f"aircraft/{aircraft_type}/{aircraft_id}/position"
    velocity_topic = f"aircraft/{aircraft_type}/{aircraft_id}/velocity"
    heading_topic = f"aircraft/{aircraft_type}/{aircraft_id}/heading"

    # Get or create publishers for this aircraft using global cache
    if position_topic not in publishers_cache:
        publishers_cache[position_topic] = session.declare_publisher(position_topic)
    if velocity_topic not in publishers_cache:
        publishers_cache[velocity_topic] = session.declare_publisher(velocity_topic)
    if heading_topic not in publishers_cache:
        publishers_cache[heading_topic] = session.declare_publisher(heading_topic)

    if format_type == 'flatbuffer':
        if position_data:
            pos_msg = create_position_message(
                aircraft_type, aircraft_id,
                position_data['lat'], position_data['lng'], position_data['alt'])
            publishers_cache[position_topic].put(pos_msg)
            print(f"Published position FlatBuffer for {aircraft_type}/{aircraft_id}")
        if velocity_data:
            vel_msg = create_velocity_message(
                aircraft_type, aircraft_id,
                velocity_data['x'], velocity_data['y'], velocity_data['z'])
            publishers_cache[velocity_topic].put(vel_msg)
            print(f"Published velocity FlatBuffer for {aircraft_type}/{aircraft_id}")
        if heading_data:
            head_msg = create_heading_message(
                aircraft_type, aircraft_id, heading_data['heading'])
            publishers_cache[heading_topic].put(head_msg)
            print(f"Published heading FlatBuffer for {aircraft_type}/{aircraft_id}")
    else:
        if position_data:
            pos_str = f"Position - Lat: {position_data['lat']}, Lng: {position_data['lng']}, Alt: {position_data['alt']}"
            publishers_cache[position_topic].put(pos_str)
            print(f"Published position string for {aircraft_type}/{aircraft_id}: {pos_str}")
        if velocity_data:
            vel_str = f"Velocity - X: {velocity_data['x']}, Y: {velocity_data['y']}, Z: {velocity_data['z']}"
            publishers_cache[velocity_topic].put(vel_str)
            print(f"Published velocity string for {aircraft_type}/{aircraft_id}: {vel_str}")
        if heading_data:
            head_str = f"Heading: {heading_data['heading']}"
            publishers_cache[heading_topic].put(head_str)
            print(f"Published heading string for {aircraft_type}/{aircraft_id}: {head_str}")

def publish_complete_mission(session, aircraft_type, aircraft_id, mission_elements, format_type):
    """Publish complete mission data for an aircraft"""
    mission_topic = f"aircraft/{aircraft_type}/{aircraft_id}/set_mission"
    
    if mission_topic not in publishers_cache:
        publishers_cache[mission_topic] = session.declare_publisher(mission_topic)
    
    if format_type == 'flatbuffer':
        mission_msg = create_mission_message(aircraft_type, aircraft_id, mission_elements)
        publishers_cache[mission_topic].put(mission_msg)
        
        # Count elements by type for better logging
        takeoff_count = sum(1 for elem in mission_elements if elem[0] == "TAKEOFF")
        waypoint_count = sum(1 for elem in mission_elements if elem[0] == "WAYPOINT")
        land_count = sum(1 for elem in mission_elements if elem[0] == "LAND")
        
        print(f"Published COMPLETE mission FlatBuffer for {aircraft_type}/{aircraft_id}: "
              f"{takeoff_count} takeoff, {waypoint_count} waypoints, {land_count} land")
    else:
        mission_str = f"Complete Mission - Total elements: {len(mission_elements)}"
        for i, (elem_type, elem_data) in enumerate(mission_elements):
            mission_str += f"\n  {i+1}. {elem_type}: {elem_data}"
        publishers_cache[mission_topic].put(mission_str)
        print(f"Published complete mission string for {aircraft_type}/{aircraft_id}")

def publish_missions_for_active_aircraft(session, current_step, aircraft_info, format_type):
    """Publish missions for aircraft that are active at the current step"""
    missions_published = 0
    
    for aircraft_key, mission_data in aircraft_missions.items():
        # Check if this aircraft is active at the current step
        if aircraft_key in aircraft_info:
            start_step = aircraft_info[aircraft_key]['start_step']
            if current_step >= start_step:
                aircraft_type = mission_data['aircraft_type']
                aircraft_id = mission_data['agent_id']
                mission_elements = mission_data['mission_elements']
                
                if mission_elements:
                    publish_complete_mission(session, aircraft_type, aircraft_id, mission_elements, format_type)
                    missions_published += 1
    
    return missions_published

def generate_random_aircraft_data(aircraft_id):
    """Generate random aircraft data for testing"""
    position_data = {
        'lat': random.uniform(37.7, 37.8),
        'lng': random.uniform(-122.5, -122.4),
        'alt': random.uniform(100, 200)
    }
    velocity_data = {
        'x': random.uniform(-10, 10),
        'y': random.uniform(-10, 10),
        'z': random.uniform(-2, 2)
    }
    heading_data = {
        'heading': random.uniform(0, 360)
    }
    return position_data, velocity_data, heading_data

def publish_fire_data(session, fire_id, lat, lng, status, format_type):
    """Publish fire data to a dedicated topic."""
    fire_topic = "fires/events"

    if fire_topic not in publishers_cache:
        publishers_cache[fire_topic] = session.declare_publisher(fire_topic)

    if format_type == 'flatbuffer':
        fire_msg = create_fire_message(fire_id, lat, lng, status)
        publishers_cache[fire_topic].put(fire_msg)
        print(f"Published fire event FlatBuffer for fire ID {fire_id} (with status)")
    else:
        fire_str = f"Fire Event - ID: {fire_id}, Lat: {lat}, Lng: {lng}, Status: {status}"
        publishers_cache[fire_topic].put(fire_str)
        print(f"Published fire event string: {fire_str}")

def main():
    parser = argparse.ArgumentParser(
        description='DDS Publisher for Aircraft Messages with FlatBuffers support')
    parser.add_argument('--aircraft_type', default='STA', 
                        help='Aircraft type (default: STA)')
    parser.add_argument('--aircraft_id', default='1', 
                        help='Aircraft ID (default: 1)')
    parser.add_argument('--interval', type=float, default=1.0, 
                        help='Publish interval in seconds (default: 1.0)')
    parser.add_argument('--format', choices=['flatbuffer', 'string'], 
                        default='flatbuffer',
                        help='Data format to publish (default: flatbuffer)')
    parser.add_argument('--count', type=int, default=0, 
                        help='Number of message cycles to send (0 = infinite)')
    parser.add_argument('--use_csv', action='store_true', 
                        help='Use CSV data from sample_data folder')
    parser.add_argument('--step_interval', type=float, default=0.5, 
                        help='Interval between steps when using CSV data (default: 0.5)')
    args = parser.parse_args()

    print(f"Starting Aircraft DDS Publisher")
    print(f"Aircraft: {args.aircraft_type}/{args.aircraft_id}")
    print(f"Format: {args.format}, Interval: {args.interval}s")
    print(f"Mission republish interval: {MISSION_REPUBLISH_INTERVAL} steps")

    if args.format == 'flatbuffer' and not FLATBUFFERS_AVAILABLE:
        print("\n\n\nFlatBuffers not available, falling back to string format\n\n\n")
        args.format = 'string'

    # Load CSV data if requested
    csv_data = None
    aircraft_info = {}
    if args.use_csv:
        agent_csv = os.path.join(script_dir, 'sample_data', 'agents_data.csv')
        fire_csv = os.path.join(script_dir, 'sample_data', 'fires_data.csv')
        mission_csv = os.path.join(script_dir, 'sample_data', 'missions_data.csv')
        csv_data = load_csv_data(agent_csv, fire_csv, mission_csv)
        if csv_data is None:
            print("Failed to load CSV data, using random values instead")
            args.use_csv = False
        else:
            print(f"CSV mode enabled - will cycle through {len(csv_data)} steps")
            print(f"Complete missions loaded for: {list(aircraft_missions.keys())}")
            
            # Extract aircraft information to determine when they become active
            aircraft_info = get_active_aircraft_from_csv(csv_data)
            print(f"Aircraft activity info: {aircraft_info}")

    try:
        with zenoh.open(zenoh.Config()) as session:
            counter = 1
            current_step = 1

            while True:
                try:
                    if args.use_csv and csv_data:
                        # CSV mode - publish data for all agents in current step
                        if current_step in csv_data:
                            step_data = csv_data[current_step]
                            
                            # Check if we need to publish missions
                            should_publish_missions = (current_step == 1 or 
                                                     current_step % MISSION_REPUBLISH_INTERVAL == 0)
                            
                            mission_count = 0
                            if should_publish_missions:
                                mission_count = publish_missions_for_active_aircraft(
                                    session, current_step, aircraft_info, args.format)
                            
                            print(f"\n--- Publishing Step {current_step} ({len(step_data)} telemetry items" +
                                  (f", {mission_count} missions)" if should_publish_missions else ")") + " ---")
                            
                            for row in step_data:
                                row_type = row.get('type')

                                if row_type == 'FIRE':
                                    fire_id = row['fire_id']
                                    lat = float(row['latitude'])
                                    lng = float(row['longitude'])
                                    status = int(row['status'])
                                    publish_fire_data(session, fire_id, lat, lng, status, args.format)

                                elif row_type not in ['MISSION']:  # Skip MISSION type as we handle it separately
                                    # Regular aircraft telemetry data
                                    aircraft_id = row['agent_id']
                                    aircraft_type = row['aircraft_type']
                                    position_data = {
                                        'lat': float(row['latitude']),
                                        'lng': float(row['longitude']),
                                        'alt': float(row['altitude'])
                                    }
                                    velocity_data = {
                                        'x': float(row['vel_x']),
                                        'y': float(row['vel_y']),
                                        'z': float(row['vel_z'])
                                    }
                                    heading_data = {
                                        'heading': float(row['heading'])
                                    }
                                    publish_aircraft_data(
                                        session, aircraft_type, aircraft_id,
                                        position_data, velocity_data, heading_data,
                                        args.format
                                    )
                            current_step += 1
                            if current_step > max(csv_data.keys()):
                                current_step = 1  # Loop back to first step
                                print("Reached end of CSV data, looping back to step 1")
                            time.sleep(args.step_interval)
                        else:
                            print(f"No data for step {current_step}")
                            current_step += 1
                    else:
                        # Random mode - single aircraft
                        position_data, velocity_data, heading_data = generate_random_aircraft_data(
                            args.aircraft_id)
                        publish_aircraft_data(
                            session, args.aircraft_type, args.aircraft_id,
                            position_data, velocity_data, heading_data,
                            args.format
                        )
                        print()
                        time.sleep(args.interval)
                    counter += 1
                    if args.count > 0 and counter > args.count:
                        break
                except KeyboardInterrupt:
                    print("\nPublisher stopped by user")
                    break
    except Exception as e:
        print(f"Error in publisher: {e}")

if __name__ == "__main__":
    main()