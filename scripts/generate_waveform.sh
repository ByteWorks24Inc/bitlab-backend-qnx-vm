#!/bin/bash

# args: <vcd_path> <output_png>

VCD_PATH=$1
OUTPUT_PNG=$2

if [ ! -f "$VCD_PATH" ]; then
  echo "❌ VCD file not found"
  exit 1
fi

echo "📊 Generating waveform..."

python3 /home/ubuntu/scripts/vcd_to_png.py "$VCD_PATH" "$OUTPUT_PNG"

if [ $? -ne 0 ]; then
  echo "❌ Failed"
  exit 1
fi

echo "✅ Done"
