#!/bin/bash

VCD_FILE=$1
OUTPUT_PNG=$2

echo "===== WAVEFORM SCRIPT START ====="
echo "Input VCD: $VCD_FILE"
echo "Output PNG: $OUTPUT_PNG"

# Check VCD exists
if [ ! -f "$VCD_FILE" ]; then
    echo "ERROR: VCD file not found"
    exit 1
fi

# Run Python renderer
python3 /home/ubuntu/bitlab-backend-qnx-vm/scripts/vcd_to_png.py "$VCD_FILE" "$OUTPUT_PNG"

if [ $? -ne 0 ]; then
    echo "ERROR: Python waveform generation failed"
    exit 1
fi

echo "Waveform generated successfully"
echo "===== WAVEFORM SCRIPT END ====="
echo "WAVEFORM COMPLETED"
exit 0
