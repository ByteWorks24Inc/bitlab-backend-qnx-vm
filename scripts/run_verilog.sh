#!/bin/bash

############################################
# Usage:
# ./run_verilog.sh <design.v> <tb.v>
############################################

DESIGN=$1
TB=$2

if [ -z "$DESIGN" ] || [ -z "$TB" ]; then
    echo "Usage: ./run_verilog.sh <design.v> <tb.v>"
    exit 1
fi

if [ ! -f "$DESIGN" ] || [ ! -f "$TB" ]; then
    echo "Design or Testbench file not found"
    exit 1
fi

VERILOG_DIR="$HOME/verilog"
LOGFILE="/tmp/verilog_execution.log"

echo "===== VERILOG EXECUTION STARTED =====" > "$LOGFILE"

############################################
# 1️⃣ Copy files
############################################

cp "$DESIGN" "$VERILOG_DIR/design.v"
cp "$TB" "$VERILOG_DIR/tb.v"

cd "$VERILOG_DIR"

# Remove old artifacts
rm -f output.vvp demo.vcd

############################################
# 2️⃣ Compile
############################################

echo "Compiling..." >> "$LOGFILE"

iverilog -o output.vvp design.v tb.v >> "$LOGFILE" 2>&1

if [ $? -ne 0 ]; then
    echo "Compilation failed" >> "$LOGFILE"
    cat "$LOGFILE"
    exit 1
fi

############################################
# 3️⃣ Run simulation (force VCD file)
############################################

echo "Running simulation..." >> "$LOGFILE"

# NOTE:
# Testbench MUST include:
# $dumpfile("demo.vcd");
# $dumpvars(0, <tb_module_name>);

OUTPUT=$(timeout 5s vvp output.vvp 2>&1 | head -n 200)

echo "===== SIMULATION OUTPUT =====" >> "$LOGFILE"
echo "$OUTPUT" >> "$LOGFILE"

############################################
# 4️⃣ Ensure VCD exists
############################################

if [ ! -f "demo.vcd" ]; then
    echo "Warning: demo.vcd not generated (check testbench dumpfile)" >> "$LOGFILE"
else
    echo "VCD file demo.vcd generated successfully" >> "$LOGFILE"
fi

echo "===== VERILOG EXECUTION FINISHED =====" >> "$LOGFILE"

cat "$LOGFILE"