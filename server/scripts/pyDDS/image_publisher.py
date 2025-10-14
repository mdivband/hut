import csv
import socket
import json
import time
import os

def read_fire_data(fire_csv_path):
    """reads csv data from fires_data.csv"""
    with open(fire_csv_path, 'r', newline='') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            yield row

def send_data(host, port, data):
    """connects to tcp server and sends data"""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((host, port))
            # Encode the JSON string to bytes and send
            s.sendall(json.dumps(data).encode('utf-8'))
        return True
    except ConnectionRefusedError:
        print(f"Connection to {host}:{port} refused. Is the listener running?")
        return False
    except Exception as e:
        print(f"An error occurred while sending data: {e}")
        return False

def main():
    HOST = '127.0.0.1'
    PORT = 65432        # a commonly used tcp port

    script_dir = os.path.dirname(os.path.abspath(__file__))
    fire_csv = os.path.join(script_dir, 'sample_data', 'fires_data.csv')

    print("Image Publisher started. Reading from:", fire_csv)

    fire_data = sorted(list(read_fire_data(fire_csv)), key=lambda x: int(x['step']))

    current_step = 1
    data_index = 0

    while data_index < len(fire_data):
        row = fire_data[data_index]
        step_of_row = int(row['step'])

        if step_of_row == current_step:
            # prep the data packet
            image_packet = {
                'step': step_of_row,
                'fire_id': int(row['fire_id']),
                'image': row['image']
            }

            print(f"Step {current_step}: Sending image for fire ID {image_packet['fire_id']}...")
            if not send_data(HOST, PORT, image_packet):
                # retry logic for connection, especially usefl when manually starting up listener
                time.sleep(2)
                continue

            data_index += 1
        else:
            print(f"Step {current_step}: No image event. Waiting...")
            time.sleep(0.5)
            current_step += 1

    print("Image Publisher finished sending all data.")

if __name__ == "__main__":
    main()