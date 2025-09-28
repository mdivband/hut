#!/usr/bin/env python3
"""
Setup script for FlatBuffers support in DDS communication
This script will:
1. Install flatbuffers Python package
2. Download flatc compiler if not available
3. Generate Python code from .fbs schemas
"""

import subprocess
import sys
import os
import urllib.request
import zipfile
import platform
import glob

def install_package(package):
    """Install a Python package using pip"""
    try:
        subprocess.check_call(
            [sys.executable, "-m", "pip", "install", package])
        print(f"Successfully installed {package}")
        return True
    except subprocess.CalledProcessError:
        print(f"Failed to install {package}")
        return False

def download_flatc():
    """Download and extract flatc compiler for Windows"""
    system = platform.system().lower()
    if system == "windows":
        url = "https://github.com/google/flatbuffers/releases/download/v23.5.26/Windows.flatc.binary.zip"
        zip_file = "flatc.zip"

        try:
            print("Downloading flatc compiler...")
            urllib.request.urlretrieve(url, zip_file)

            with zipfile.ZipFile(zip_file, 'r') as zip_ref:
                zip_ref.extractall(".")

            os.remove(zip_file)
            print("flatc compiler downloaded and extracted")
            return True
        except Exception as e:
            print(f"Failed to download flatc: {e}")
            return False
    else:
        print("Please install flatc manually for your platform")
        return False

def generate_python_code():
    """Generate Python code from FlatBuffers schemas"""
    # Find all .fbs schema files
    schema_files = glob.glob("*.fbs")

    if not schema_files:
        print("No .fbs schema files found")
        return False

    output_dir = "generated"

    # Delete existing generated directory if it exists
    if os.path.exists(output_dir):
        print(f"Removing existing directory: {output_dir}")
        import shutil
        shutil.rmtree(output_dir)

    # Create output directory
    os.makedirs(output_dir, exist_ok=True)

    # Check if flatc is available
    flatc_cmd = "flatc.exe" if platform.system().lower() == "windows" else "flatc"

    success_count = 0

    for schema_file in schema_files:
        try:
            # Generate Python code for each schema file
            cmd = [flatc_cmd, "--python", "-o", output_dir, schema_file]
            subprocess.check_call(cmd)
            print(f"Generated Python code from {schema_file}")
            success_count += 1
        except subprocess.CalledProcessError:
            print(f"Failed to generate Python code from {schema_file}")
        except FileNotFoundError:
            print("flatc compiler not found")
            return False

    if success_count > 0:
        print(f"Successfully processed {success_count}/{len(schema_files)} schema files")
        return True
    else:
        print("Failed to process any schema files")
        return False

def main():
    print("Setting up FlatBuffers for DDS communication...")
    print("=" * 50)

    # Install flatbuffers Python package
    if not install_package("flatbuffers"):
        return False

    # Add the parent path of the script to sys.path
    os.chdir(os.path.dirname(os.path.abspath(__file__)))

    # Download flatc if not available
    if not os.path.exists("flatc.exe") and platform.system().lower() == "windows":
        if not download_flatc():
            return False

    # Generate Python code from schemas
    if not generate_python_code():
        return False

    print("=" * 50)
    print("FlatBuffers setup completed successfully!")

    return True

if __name__ == "__main__":
    main()
