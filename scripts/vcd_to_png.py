import sys
from vcdvcd import VCDVCD
import matplotlib.pyplot as plt

vcd_file = sys.argv[1]
output = sys.argv[2]

print("Reading VCD:", vcd_file)

try:
    vcd = VCDVCD(vcd_file)
except Exception as e:
    print("ERROR parsing VCD:", e)
    exit(1)

signals = list(vcd.signals.keys())

if len(signals) == 0:
    print("No signals found in VCD")
    exit(1)

plt.figure(figsize=(10, 5))

offset = 0

for sig in signals[:3]:
    try:
        tv = vcd[sig].tv

        times = []
        values = []

        for t, v in tv:
            times.append(t)

            if isinstance(v, str) and ('x' in v or 'z' in v):
                values.append(0)
            else:
                try:
                    values.append(int(v, 2))
                except:
                    values.append(0)

        shifted = [val + offset for val in values]

        plt.step(times, shifted, where='post', label=sig)

        offset += max(values) + 2 if values else 2

    except Exception as e:
        print(f"Skipping signal {sig}: {e}")

plt.xlabel("Time")
plt.ylabel("Signals")
plt.title("Waveform")
plt.legend()
plt.grid(True)

plt.savefig(output)

print("PNG generated:", output)
