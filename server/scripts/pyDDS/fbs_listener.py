#!/usr/bin/env python3
import zenoh, time, argparse, sys
import json

# Add the script folder and generated folder to path
import os
script_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(script_dir)
sys.path.append(os.path.join(script_dir, 'flatbuffers', 'generated'))

# FlatBuffers imports (will be available after running setup_flatbuffers.py)
try:
    import flatbuffers
    from DDSSchema import DDSMessage, AgentData, Coordinate, Velocity, Attitude, Status
    FLATBUFFERS_AVAILABLE = True
    print("FlatBuffers support enabled", flush=True)
except ImportError as e:
    FLATBUFFERS_AVAILABLE = False
    print(f"FlatBuffers not available - using string format: {e}", flush=True)

def velocity_to_speed(velocity_x, velocity_y, velocity_z):
    """Convert velocity components to speed (magnitude)"""
    return (velocity_x**2 + velocity_y**2 + velocity_z**2)**0.5

def deserialize_flatbuffer(data):
    """Deserialize FlatBuffer data to a readable format"""
    try:
        # Create a DDSMessage from the received data
        msg = DDSMessage.DDSMessage.GetRootAs(data, 0)
        
        # Extract message metadata
        result = {
            "timestamp": msg.Timestamp(),
            "message_id": msg.MessageId().decode('utf-8') if msg.MessageId() else "",
            "source": msg.Source().decode('utf-8') if msg.Source() else "",
            "agents": []
        }
        
        # Extract each agent's data
        for i in range(msg.AgentsLength()):
            agent = msg.Agents(i)
            
            agent_data = {
                "agent_id": agent.AgentId().decode('utf-8') if agent.AgentId() else "",
                "battery_level": agent.BatteryLevel(),
                "signal_strength": agent.SignalStrength(),
                "status": agent.Status(),
                "altitude": agent.Altitude(),
                "heading": agent.Heading(),
                "custom_data": agent.CustomData().decode('utf-8') if agent.CustomData() else ""
            }
            
            # Extract coordinate data
            if agent.Coordinate():
                agent_data["coordinate"] = {
                    "lat": agent.Coordinate().Lat(),
                    "lng": agent.Coordinate().Lng()
                }
            
            # Extract velocity data and calculate speed
            if agent.Velocity():
                velocity_x = agent.Velocity().X()
                velocity_y = agent.Velocity().Y()
                velocity_z = agent.Velocity().Z()
                
                # We do not need agent velocity for now
                # agent_data["velocity"] = {
                #     "x": velocity_x,
                #     "y": velocity_y,
                #     "z": velocity_z
                # }
                
                # Calculate and add speed
                agent_data["speed"] = velocity_to_speed(
                    velocity_x, velocity_y, velocity_z)
            
            # Extract attitude data - we do no need this for now
            # if agent.Attitude():
            #     agent_data["attitude"] = {
            #         "roll": agent.Attitude().Roll(),
            #         "pitch": agent.Attitude().Pitch(),
            #         "yaw": agent.Attitude().Yaw()
            #     }
            
            result["agents"].append(agent_data)
        
        return result
    except Exception as e:
        return {"error": f"Failed to deserialize FlatBuffer: {e}"}

def format_output(data, is_flatbuffer=False):
    """Format the output data for display as JSON"""
    if is_flatbuffer:
        # Pretty print the deserialized FlatBuffer data as JSON
        return json.dumps(data, indent=2)
    else:
        # Wrap string data in JSON format
        string_data = {"type": "string", "data": str(data)}
        return json.dumps(string_data, indent=2)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='DDS Listener for Zenoh with FlatBuffers support')
    parser.add_argument('--wait_time', type=float, default=1.0,
                        help='Maximum wait time in seconds (default: 1.0)')
    parser.add_argument('--continuous', action='store_true',
                        help='Run continuously until killed')
    parser.add_argument('--format', choices=['auto', 'flatbuffer', 'string'], 
                        default='auto', 
                        help='Data format to expect (default: auto)')
    args = parser.parse_args()
    
    data_received = False
    last_check_time = time.time()
    no_data_warning_interval = 5.0  # Print warning every 5 seconds if no data

    def listener(sample):
        global data_received, last_check_time
        data_received = True
        last_check_time = time.time()
        
        try:
            # Try to determine the data format
            is_flatbuffer = False
            
            if args.format == 'flatbuffer' or (
                args.format == 'auto' and FLATBUFFERS_AVAILABLE):
                # Try to deserialize as FlatBuffer first
                try:
                    payload_bytes = sample.payload.to_bytes()
                    if len(payload_bytes) > 4:  # FlatBuffers have a minimum size
                        deserialized_data = deserialize_flatbuffer(payload_bytes)
                        if "error" not in deserialized_data:
                            is_flatbuffer = True
                            formatted_output = format_output(deserialized_data, True)
                            
                            # Print as JSON
                            agent_count = len(deserialized_data.get("agents", []))
                            print(f"FlatBuffer Data received for {agent_count} "
                                  f"agent{'s' if agent_count != 1 else ''}:", 
                                  flush=True)
                            print(formatted_output, flush=True)
                        else:
                            raise Exception("Not a valid FlatBuffer")
                    else:
                        raise Exception("Data too small for FlatBuffer")
                except Exception:
                    # Fall back to string format
                    is_flatbuffer = False
            
            if not is_flatbuffer:
                # Handle as string data and format as JSON
                payload_str = sample.payload.to_string()
                if payload_str:
                    formatted_output = format_output(payload_str, False)
                    print("String Data:", flush=True)
                    print(formatted_output, flush=True)
                else:
                    pretty_time = time.strftime(
                        '%Y-%m-%d %H:%M:%S', time.localtime(time.time()))
                    empty_data = {"type": "empty", "data": None}
                    print(f"{pretty_time}: Empty Data:", flush=True)
                    print(json.dumps(empty_data, indent=2), flush=True)
                    
        except Exception as e:
            print(f"Error processing data from '{sample.key_expr}': {e}", flush=True)
    
    try:
        with zenoh.open(zenoh.Config()) as session:
            if args.continuous:
                format_msg = f" (format: {args.format})" if args.format != 'auto' else ""
                print(f"DDS Listener started in continuous mode{format_msg}...", 
                      flush=True)
            else:
                print(f"DDS Listener started, waiting for {args.wait_time} seconds...", 
                      flush=True)
            
            # Print the start time 
            print(f"Start time: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())}", 
                  flush=True)
            sub = session.declare_subscriber('DDS/test', listener)
            
            if args.continuous:
                # Continuous mode - run until killed
                try:
                    while True:
                        time.sleep(0.5)  # Check every 0.5 seconds
                        current_time = time.time()
                        pretty_time = time.strftime(
                            '%Y-%m-%d %H:%M:%S', time.localtime(current_time))
                        
                        # Check if enough time has passed since last data received
                        time_since_last_data = current_time - last_check_time
                        if time_since_last_data >= no_data_warning_interval:
                            print(
                                f"{pretty_time}: No data received - publisher may not be active",
                                flush=True)
                            last_check_time = current_time
                except KeyboardInterrupt:
                    print("DDS Listener stopped by user", flush=True)
            else:
                # Wait for the specified time (original behavior)
                time.sleep(args.wait_time)
                
                if not data_received:
                    pretty_time = time.strftime(
                        '%Y-%m-%d %H:%M:%S', time.localtime(time.time()))
                    print(f"{pretty_time}: No data received - publisher may not be active", 
                          flush=True)
                
    except Exception as e:
        print(f"Error connecting to DDS: {e}", flush=True)
        print("Publisher is not active or connection failed", flush=True)
