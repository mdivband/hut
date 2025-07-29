#!/usr/bin/env python3
import zenoh, time, argparse, sys

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='DDS Listener for Zenoh')
    parser.add_argument('--wait_time', type=float, default=1.0,
                        help='Maximum wait time in seconds (default: 1.0)')
    parser.add_argument('--continuous', action='store_true',
                        help='Run continuously until killed')
    args = parser.parse_args()
    
    data_received = False
    last_check_time = time.time()
    no_data_warning_interval = 5.0  # Print warning every 5 seconds if no data

    def listener(sample):
        global data_received, last_check_time
        data_received = True
        last_check_time = time.time()
        payload_str = sample.payload.to_string()
        if payload_str:
            print(f"Data received: '{payload_str}' from '{sample.key_expr}'", flush=True)
        else:
            print(f"Empty data received from '{sample.key_expr}'", flush=True)
    
    try:
        with zenoh.open(zenoh.Config()) as session:
            if args.continuous:
                print("DDS Listener started in continuous mode...", flush=True)
            else:
                print(f"DDS Listener started, waiting for {args.wait_time} seconds...", flush=True)
            
            sub = session.declare_subscriber('DDS/test', listener)
            
            if args.continuous:
                # Continuous mode - run until killed
                try:
                    while True:
                        time.sleep(0.5)  # Check every second
                        current_time = time.time()
                        
                        # Periodically report if no data received
                        if not data_received and (current_time - last_check_time) >= no_data_warning_interval:
                            print("No data received - publisher may not be active", flush=True)
                            last_check_time = current_time
                except KeyboardInterrupt:
                    print("DDS Listener stopped by user", flush=True)
            else:
                # Wait for the specified time (original behavior)
                time.sleep(args.wait_time)
                
                if not data_received:
                    print("No data received - publisher may not be active", flush=True)
                
    except Exception as e:
        print(f"Error connecting to DDS: {e}", flush=True)
        print("Publisher is not active or connection failed", flush=True)