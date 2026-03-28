#!/bin/bash

############################################
# Usage:
# ./run_sverilog.sh <design.sv> <tb.sv>
############################################

DESIGN=$1
TB=$2

if [ -z "$DESIGN" ] || [ -z "$TB" ]; then
    echo "Usage: ./run_sverilog.sh <design.sv> <tb.sv>"
    exit 1
fi

if [ ! -f "$DESIGN" ] || [ ! -f "$TB" ]; then
    echo "Design or Testbench file not found"
    exit 1
fi

SV_DIR="$HOME/sverilog"
LOGFILE="/tmp/sverilog_execution.log"

echo "===== SYSTEMVERILOG EXECUTION STARTED =====" > "$LOGFILE"

############################################
# 1️⃣ Copy files
############################################

cp "$DESIGN" "$SV_DIR/design.sv"
cp "$TB" "$SV_DIR/tb.sv"

cd "$SV_DIR"

# Clean previous build
rm -rf obj_dir
rm -f demo.vcd sim_main.cpp

############################################
# 2️⃣ Generate simple C++ runner
############################################

cat <<EOF > sim_main.cpp
#include "Vdesign.h"
#include "verilated.h"
#include "verilated_vcd_c.h"

int main(int argc, char** argv) {

    Verilated::commandArgs(argc, argv);

    Vdesign* top = new Vdesign;

    VerilatedVcdC* tfp = new VerilatedVcdC;
    top->trace(tfp, 99);
    tfp->open("demo.vcd");

    for (int i = 0; i < 100; i++) {
        top->eval();
        tfp->dump(i);
    }

    tfp->close();
    delete top;
    return 0;
}
EOF

############################################
# 3️⃣ Compile with Verilator
############################################

echo "Compiling with Verilator..." >> "$LOGFILE"

verilator --cc design.sv tb.sv --top-module tb --trace --exe sim_main.cpp

if [ $? -ne 0 ]; then
    echo "Compilation failed" >> "$LOGFILE"
    cat "$LOGFILE"
    exit 1
fi

############################################
# 4️⃣ Build
############################################

make -C obj_dir -f Vdesign.mk >> "$LOGFILE" 2>&1

############################################
# 5️⃣ Run simulation
############################################

echo "Running simulation..." >> "$LOGFILE"

OUTPUT=$(timeout 5s ./obj_dir/Vdesign 2>&1 | head -n 200)

echo "===== SIMULATION OUTPUT =====" >> "$LOGFILE"
echo "$OUTPUT" >> "$LOGFILE"

############################################
# 6️⃣ Check VCD
############################################

if [ ! -f "demo.vcd" ]; then
    echo "Warning: demo.vcd not generated" >> "$LOGFILE"
else
    echo "VCD file demo.vcd generated successfully" >> "$LOGFILE"
fi

echo "===== SYSTEMVERILOG EXECUTION FINISHED =====" >> "$LOGFILE"

cat "$LOGFILE"
