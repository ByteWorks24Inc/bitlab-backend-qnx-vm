import sys
from vcdvcd import VCDVCD
import matplotlib.pyplot as plt

vcd_file = sys.argv[1]
output = sys.argv[2]

print("Reading VCD:", vcd_file)

vcd = VCDVCD(vcd_file)

signals = list(vcd.signals.keys())

plt.figure(figsize=(10, 5))

offset = 0

for sig in signals[:3]:  # limit to first 3 signals
    tv = vcd[sig].tv

    times = []
    values = []

    for t, v in tv:
        times.append(t)

        # Convert binary string → integer
        if 'x' in v or 'z' in v:
            values.append(0)
        else:
            values.append(int(v, 2))

    # Offset signals vertically so they don't overlap
    shifted = [val + offset for val in values]

    plt.step(times, shifted, where='post', label=sig)

    offset += max(values) + 2  # spacing between signals

plt.xlabel("Time")
plt.ylabel("Signals")
plt.title("Actual Waveform")
plt.legend()
plt.grid(True)

plt.savefig(output)

print("PNG generated at:", output)
