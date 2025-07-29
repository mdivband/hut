#!/usr/bin/env python3
"""
DDS Publisher with FlatBuffers support and CSV data reading
This script publishes data using FlatBuffers serialization from CSV files
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

# FlatBuffers imports (will be available after running setup_flatbuffers.py)
try:
    import flatbuffers
    from DDSSchema import DDSMessage, AgentData, Coordinate, Velocity, Attitude, Status
    FLATBUFFERS_AVAILABLE = True
    print("FlatBuffers support enabled")
except ImportError as e:
    FLATBUFFERS_AVAILABLE = False
    print(f"FlatBuffers not available - using string format: {e}")

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

def create_flatbuffer_message_from_csv_step(step_data, message_counter):
    """Create a FlatBuffer message from CSV step data (multiple agents)"""
    builder = flatbuffers.Builder(2048)
    
    # Create strings for message metadata
    message_id_offset = builder.CreateString(f"msg_{message_counter}")
    source_offset = builder.CreateString("csv_publisher")
    
    # Create agent data for each agent in this step
    agent_offsets = []
    
    for row in step_data:
        # Create strings for this agent
        agent_id_offset = builder.CreateString(row['agent_id'])
        custom_data_offset = builder.CreateString(row['custom_data'])
        
        # Create Coordinate
        Coordinate.CoordinateStart(builder)
        Coordinate.CoordinateAddLat(builder, float(row['latitude']))
        Coordinate.CoordinateAddLng(builder, float(row['longitude']))
        coordinate_offset = Coordinate.CoordinateEnd(builder)
        
        # Create Velocity
        Velocity.VelocityStart(builder)
        Velocity.VelocityAddX(builder, float(row['vel_x']))
        Velocity.VelocityAddY(builder, float(row['vel_y']))
        Velocity.VelocityAddZ(builder, float(row['vel_z']))
        velocity_offset = Velocity.VelocityEnd(builder)
        
        # Create Attitude
        Attitude.AttitudeStart(builder)
        Attitude.AttitudeAddRoll(builder, float(row['roll']))
        Attitude.AttitudeAddPitch(builder, float(row['pitch']))
        Attitude.AttitudeAddYaw(builder, float(row['yaw']))
        attitude_offset = Attitude.AttitudeEnd(builder)
        
        # Map status string to enum
        status_map = {
            'ACTIVE': Status.Status.ACTIVE,
            'INACTIVE': Status.Status.INACTIVE,
            'ERROR': Status.Status.ERROR,
            'UNKNOWN': Status.Status.UNKNOWN
        }
        status_value = status_map.get(row['status'], Status.Status.UNKNOWN)
        
        # Create AgentData
        AgentData.AgentDataStart(builder)
        AgentData.AgentDataAddAgentId(builder, agent_id_offset)
        AgentData.AgentDataAddCoordinate(builder, coordinate_offset)
        AgentData.AgentDataAddAltitude(builder, float(row['altitude']))
        AgentData.AgentDataAddHeading(builder, float(row['heading']))
        AgentData.AgentDataAddVelocity(builder, velocity_offset)
        AgentData.AgentDataAddAttitude(builder, attitude_offset)
        AgentData.AgentDataAddStatus(builder, status_value)
        AgentData.AgentDataAddBatteryLevel(builder, float(row['battery_level']))
        AgentData.AgentDataAddSignalStrength(builder, float(row['signal_strength']))
        AgentData.AgentDataAddCustomData(builder, custom_data_offset)
        agent_offset = AgentData.AgentDataEnd(builder)
        
        agent_offsets.append(agent_offset)
    
    # Create vector of agents
    DDSMessage.DDSMessageStartAgentsVector(builder, len(agent_offsets))
    for agent_offset in reversed(agent_offsets):  # FlatBuffers vectors are built in reverse
        builder.PrependUOffsetTRelative(agent_offset)
    agents_vector = builder.EndVector(len(agent_offsets))
    
    # Create main DDSMessage
    DDSMessage.DDSMessageStart(builder)
    DDSMessage.DDSMessageAddTimestamp(builder, int(time.time() * 1000))
    DDSMessage.DDSMessageAddMessageId(builder, message_id_offset)
    DDSMessage.DDSMessageAddSource(builder, source_offset)
    DDSMessage.DDSMessageAddAgents(builder, agents_vector)
    message_offset = DDSMessage.DDSMessageEnd(builder)
    
    # Finish the buffer
    builder.Finish(message_offset)
    
    return builder.Output()

def create_flatbuffer_message(agent_id, counter):
    """Create a FlatBuffer message with sample data for a single agent"""
    builder = flatbuffers.Builder(1024)
    
    # Create strings for message metadata
    message_id_offset = builder.CreateString(f"msg_{counter}")
    source_offset = builder.CreateString("test_publisher")
    
    # Create strings for agent
    agent_id_offset = builder.CreateString(agent_id)
    custom_data_offset = builder.CreateString(f"Sample data {counter}")
    
    # Create Coordinate
    Coordinate.CoordinateStart(builder)
    Coordinate.CoordinateAddLat(builder, random.uniform(37.7, 37.8))
    Coordinate.CoordinateAddLng(builder, random.uniform(-122.5, -122.4))
    coordinate_offset = Coordinate.CoordinateEnd(builder)
    
    # Create Velocity
    Velocity.VelocityStart(builder)
    Velocity.VelocityAddX(builder, random.uniform(-5, 5))
    Velocity.VelocityAddY(builder, random.uniform(-5, 5))
    Velocity.VelocityAddZ(builder, random.uniform(-1, 1))
    velocity_offset = Velocity.VelocityEnd(builder)
    
    # Create Attitude
    Attitude.AttitudeStart(builder)
    Attitude.AttitudeAddRoll(builder, random.uniform(-180, 180))
    Attitude.AttitudeAddPitch(builder, random.uniform(-90, 90))
    Attitude.AttitudeAddYaw(builder, random.uniform(-180, 180))
    attitude_offset = Attitude.AttitudeEnd(builder)
    
    # Create AgentData
    AgentData.AgentDataStart(builder)
    AgentData.AgentDataAddAgentId(builder, agent_id_offset)
    AgentData.AgentDataAddCoordinate(builder, coordinate_offset)
    AgentData.AgentDataAddAltitude(builder, random.uniform(100, 200))
    AgentData.AgentDataAddVelocity(builder, velocity_offset)
    AgentData.AgentDataAddAttitude(builder, attitude_offset)
    AgentData.AgentDataAddStatus(builder, Status.Status.ACTIVE)
    AgentData.AgentDataAddBatteryLevel(builder, random.uniform(0.2, 1.0))
    AgentData.AgentDataAddSignalStrength(builder, random.uniform(0.5, 1.0))
    AgentData.AgentDataAddCustomData(builder, custom_data_offset)
    agent_offset = AgentData.AgentDataEnd(builder)
    
    # Create vector of agents (single agent in this case)
    DDSMessage.DDSMessageStartAgentsVector(builder, 1)
    builder.PrependUOffsetTRelative(agent_offset)
    agents_vector = builder.EndVector(1)
    
    # Create main DDSMessage
    DDSMessage.DDSMessageStart(builder)
    DDSMessage.DDSMessageAddTimestamp(builder, int(time.time() * 1000))
    DDSMessage.DDSMessageAddMessageId(builder, message_id_offset)
    DDSMessage.DDSMessageAddSource(builder, source_offset)
    DDSMessage.DDSMessageAddAgents(builder, agents_vector)
    message_offset = DDSMessage.DDSMessageEnd(builder)
    
    # Finish the buffer
    builder.Finish(message_offset)
    
    return builder.Output()

def create_string_message(agent_id, counter):
    """Create a simple string message for fallback"""
    return f"Agent {agent_id} - Message {counter} - Time: {time.time()}"

def main():
    parser = argparse.ArgumentParser(
        description='DDS Publisher with FlatBuffers support')
    parser.add_argument('--agent_id', default='UAV-1', 
                        help='Agent ID (default: UAV-1)')
    parser.add_argument('--interval', type=float, default=1.0, 
                        help='Publish interval in seconds (default: 1.0)')
    parser.add_argument('--format', choices=['flatbuffer', 'string'], 
                        default='flatbuffer',
                        help='Data format to publish (default: flatbuffer)')
    parser.add_argument('--count', type=int, default=0, 
                        help='Number of messages to send (0 = infinite)')
    parser.add_argument('--use_csv', action='store_true', 
                        help='Use CSV data from sample_data folder')
    parser.add_argument('--step_interval', type=float, default=2.0, 
                        help='Interval between steps when using CSV data (default: 2.0)')
    args = parser.parse_args()
    
    print(f"Starting DDS Publisher for agent '{args.agent_id}'")
    print(f"Format: {args.format}, Interval: {args.interval}s")
    
    if args.format == 'flatbuffer' and not FLATBUFFERS_AVAILABLE:
        print("FlatBuffers not available, falling back to string format")
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
            pub = session.declare_publisher('DDS/test')
            
            counter = 1
            current_step = 1
            
            while True:
                try:
                    if args.use_csv and csv_data:
                        # CSV mode - publish all agents for current step
                        if current_step in csv_data:
                            step_data = csv_data[current_step]
                            print(f"\n--- Publishing Step {current_step} "
                                  f"({len(step_data)} agents) ---")
                            
                            if args.format == 'flatbuffer':
                                # Create and send FlatBuffer message from CSV step (all agents)
                                message_data = create_flatbuffer_message_from_csv_step(
                                    step_data, counter)
                                pub.put(message_data)
                                print(f"Published FlatBuffer message for {len(step_data)} agents "
                                      f"(Step {current_step})")
                            else:
                                # Create and send string messages from CSV (one per agent)
                                for row in step_data:
                                    message_data = f"Agent {row['agent_id']} - Step {current_step} "
                                    message_data += f"Lat: {row['latitude']}, Lon: {row['longitude']}, "
                                    message_data += f"Alt: {row['altitude']}"
                                    message_data += f", Heading: {row['heading']}"
                                    pub.put(message_data)
                                    print(f"Published string message: {message_data}")
                            
                            counter += 1
                            
                            # Move to next step
                            current_step += 1
                            if current_step > max(csv_data.keys()):
                                current_step = 1  # Loop back to first step
                                print("Reached end of CSV data, looping back to step 1")
                            
                            time.sleep(args.step_interval)
                        else:
                            print(f"No data for step {current_step}")
                            current_step += 1
                    else:
                        # Random mode - single agent
                        if args.format == 'flatbuffer':
                            # Create and send FlatBuffer message
                            message_data = create_flatbuffer_message(
                                args.agent_id, counter)
                            pub.put(message_data)
                            print(f"Published FlatBuffer message {counter} for "
                                  f"{args.agent_id}")
                        else:
                            # Create and send string message
                            message_data = create_string_message(args.agent_id, counter)
                            pub.put(message_data)
                            print(f"Published string message {counter}: {message_data}")
                        
                        counter += 1
                        time.sleep(args.interval)
                    
                    # Check if we've sent enough messages
                    if args.count > 0 and counter > args.count:
                        break
                        
                except KeyboardInterrupt:
                    print("\nPublisher stopped by user")
                    break
                    
    except Exception as e:
        print(f"Error in publisher: {e}")

if __name__ == "__main__":
    main()
