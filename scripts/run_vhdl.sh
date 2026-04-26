#!/bin/bash

############################################

# Usage:

# ./run_vhdl.sh <design.vhd> <tb.vhd>

############################################

set -e   # 🔥 Stop immediately if any command fails

DESIGN=$1
TB=$2

if [ -z "$DESIGN" ] || [ -z "$TB" ]; then
echo "Usage: ./run_vhdl.sh <design.vhd> <tb.vhd>"
exit 1
fi

if [ ! -f "$DESIGN" ] || [ ! -f "$TB" ]; then
echo "Design or Testbench file not found"
exit 1
fi

VHDL_DIR="$HOME/vhdl"
LOGFILE="/tmp/vhdl_execution.log"

echo "===== VHDL EXECUTION STARTED =====" > "$LOGFILE"

############################################

# 1️⃣ Copy files

############################################

cp "$DESIGN" "$VHDL_DIR/design.vhd"
cp "$TB" "$VHDL_DIR/tb.vhd"

cd "$VHDL_DIR"

# Remove old artifacts

rm -f demo.vcd

############################################

# 🔧 CLEAN INPUT FILES (CRITICAL FIX)

############################################

# Remove backticks (main cause of your error)

sed -i 's/`//g' design.vhd
sed -i 's/`//g' tb.vhd

# Normalize file encoding (remove hidden/bad characters)

tr -cd '\11\12\15\40-\176' < tb.vhd > tb_clean.vhd && mv tb_clean.vhd tb.vhd
tr -cd '\11\12\15\40-\176' < design.vhd > design_clean.vhd && mv design_clean.vhd design.vhd

############################################

# 🧪 DEBUG: Print file content (optional but useful)

############################################

echo "===== TB FILE CONTENT =====" >> "$LOGFILE"
cat -n tb.vhd >> "$LOGFILE"

############################################

# 2️⃣ Analyze & Elaborate

############################################

echo "Analyzing..." >> "$LOGFILE"

ghdl -a design.vhd >> "$LOGFILE" 2>&1
ghdl -a tb.vhd >> "$LOGFILE" 2>&1
ghdl -e tb >> "$LOGFILE" 2>&1

############################################

# 3️⃣ Run simulation (generate demo.vcd)

############################################

echo "Running simulation..." >> "$LOGFILE"

OUTPUT=$(timeout 5s ghdl -r tb --vcd=demo.vcd 2>&1 | head -n 200)

echo "===== SIMULATION OUTPUT =====" >> "$LOGFILE"
echo "$OUTPUT" >> "$LOGFILE"

############################################

# 4️⃣ Verify VCD

############################################

if [ ! -f "demo.vcd" ]; then
echo "Warning: demo.vcd not generated" >> "$LOGFILE"
else
echo "VCD file demo.vcd generated successfully" >> "$LOGFILE"
fi

echo "===== VHDL EXECUTION FINISHED =====" >> "$LOGFILE"

cat "$LOGFILE"
