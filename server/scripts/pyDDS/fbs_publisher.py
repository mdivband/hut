#!/usr/bin/env python3
"""
DDS Publisher with FlatBuffers support for Aircraft Messages
This script publishes position, velocity, heading, and waypoint data using separate topics
"""

import zenoh
import time
import random
import argparse
import csv
import os
import sys

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
    from messages import PositionMessage, VelocityMessage, HeadingMessage, WaypointMessage, FireMessage
    print("FlatBuffers support enabled")

# Global dictionary to store publishers
publishers_cache = {}

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

def create_waypoint_message(aircraft_type, aircraft_id, lat, lng, alt, heading):
    """Create a WaypointMessage FlatBuffer"""
    builder = flatbuffers.Builder(256)
    aircraft_type = get_aircraft_type(aircraft_type)
    WaypointMessage.WaypointMessageStart(builder)
    WaypointMessage.WaypointMessageAddTimestamp(builder, int(time.time() * 1000))
    WaypointMessage.WaypointMessageAddTtype(builder, aircraft_type)
    WaypointMessage.WaypointMessageAddId(builder, int(aircraft_id) if aircraft_id.isdigit() else 1)
    WaypointMessage.WaypointMessageAddLatitude(builder, lat)
    WaypointMessage.WaypointMessageAddLongitude(builder, lng)
    WaypointMessage.WaypointMessageAddAltitude(builder, alt)
    WaypointMessage.WaypointMessageAddHeading(builder, heading)
    message_offset = WaypointMessage.WaypointMessageEnd(builder)
    builder.Finish(message_offset)
    return builder.Output()

def create_fire_message(fire_id, lat, lng):
    """Create a FireMessage FlatBuffer"""
    builder = flatbuffers.Builder(128)
    FireMessage.FireMessageStart(builder)
    FireMessage.FireMessageAddTimestamp(builder, int(time.time() * 1000))
    FireMessage.FireMessageAddId(builder, int(fire_id))
    FireMessage.FireMessageAddLatitude(builder, lat)
    FireMessage.FireMessageAddLongitude(builder, lng)
    message_offset = FireMessage.FireMessageEnd(builder)
    builder.Finish(message_offset)
    return builder.Output()

def load_csv_data(csv_file_path):
    """Load CSV data and organize by steps"""
    csv_data = {}
    try:
        with open(csv_file_path, 'r', newline='') as csvfile:
            reader = csv.DictReader(csvfile)
            for row in reader:
                step = int(row['step'])
                if step not in csv_data:
                    csv_data[step] = []
                csv_data[step].append(row)
        print(f"Loaded CSV data with {len(csv_data)} steps")
        for step, agents in csv_data.items():
            print(f"  Step {step}: {len(agents)} agents")
        return csv_data
    except Exception as e:
        print(f"Error loading CSV file: {e}")
        return None

def publish_aircraft_data(
        session, aircraft_type, aircraft_id, position_data, 
        velocity_data, heading_data, format_type, waypoint_data=None):
    """Publish aircraft data to separate topics, including waypoint if provided"""
    position_topic = f"aircraft/{aircraft_type}/{aircraft_id}/position"
    velocity_topic = f"aircraft/{aircraft_type}/{aircraft_id}/velocity"
    heading_topic = f"aircraft/{aircraft_type}/{aircraft_id}/heading"
    waypoint_topic = f"aircraft/{aircraft_type}/{aircraft_id}/waypoint"

    # Get or create publishers for this aircraft using global cache
    if position_topic not in publishers_cache:
        publishers_cache[position_topic] = session.declare_publisher(position_topic)
    if velocity_topic not in publishers_cache:
        publishers_cache[velocity_topic] = session.declare_publisher(velocity_topic)
    if heading_topic not in publishers_cache:
        publishers_cache[heading_topic] = session.declare_publisher(heading_topic)
    if waypoint_topic not in publishers_cache:
        publishers_cache[waypoint_topic] = session.declare_publisher(waypoint_topic)

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
        if waypoint_data:
            wp_msg = create_waypoint_message(
                aircraft_type, aircraft_id,
                waypoint_data['lat'], waypoint_data['lng'],
                waypoint_data['alt'], waypoint_data['heading'])
            publishers_cache[waypoint_topic].put(wp_msg)
            print(f"Published waypoint FlatBuffer for {aircraft_type}/{aircraft_id}")
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
        if waypoint_data:
            wp_str = f"Waypoint - Lat: {waypoint_data['lat']}, Lng: {waypoint_data['lng']}, Alt: {waypoint_data['alt']}, Heading: {waypoint_data['heading']}"
            publishers_cache[waypoint_topic].put(wp_str)
            print(f"Published waypoint string for {aircraft_type}/{aircraft_id}: {wp_str}")

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

def publish_fire_data(session, fire_id, lat, lng, format_type):
    """Publish fire data to a dedicated topic"""
    fire_topic = "fires/events"

    if fire_topic not in publishers_cache:
        publishers_cache[fire_topic] = session.declare_publisher(fire_topic)

    if format_type == 'flatbuffer':
        fire_msg = create_fire_message(fire_id, lat, lng)
        publishers_cache[fire_topic].put(fire_msg)
        print(f"Published fire event FlatBuffer for fire ID {fire_id}")
    else:
        fire_str = f"Fire Event - ID: {fire_id}, Lat: {lat}, Lng: {lng}"
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

    if args.format == 'flatbuffer' and not FLATBUFFERS_AVAILABLE:
        print("\n\n\nFlatBuffers not available, falling back to string format\n\n\n")
        args.format = 'string'

    # Load CSV data if requested
    csv_data = None
    if args.use_csv:
        csv_file_path = os.path.join(script_dir, 'sample_data', 'sample_data.csv')
        csv_data = load_csv_data(csv_file_path)
        if csv_data is None:
            print("Failed to load CSV data, using random values instead")
            args.use_csv = False
        else:
            print(f"CSV mode enabled - will cycle through {len(csv_data)} steps")

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
                            print(f"\n--- Publishing Step {current_step} ({len(step_data)} agents) ---")
                            for row in step_data:
                                aircraft_type = row.get('aircraft_type', args.aircraft_type)

                                if aircraft_type == 'FIRE':
                                    fire_id = row['agent_id'] # Reusing agent_id column
                                    lat = float(row['latitude'])
                                    lng = float(row['longitude'])
                                    publish_fire_data(session, fire_id, lat, lng, args.format)
                                else:
                                    aircraft_id = row['agent_id']
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
                                    # Prepare waypoint data if available
                                    waypoint_data = None
                                    if (row.get('waypoint_latitude') and row.get('waypoint_longitude')
                                        and row.get('waypoint_altitude') and row.get('waypoint_heading')):
                                        waypoint_data = {
                                            'lat': float(row['waypoint_latitude']),
                                            'lng': float(row['waypoint_longitude']),
                                            'alt': float(row['waypoint_altitude']),
                                            'heading': float(row['waypoint_heading'])
                                        }
                                    publish_aircraft_data(
                                        session, aircraft_type, aircraft_id,
                                        position_data, velocity_data, heading_data,
                                        args.format, waypoint_data
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
                            args.format, None
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