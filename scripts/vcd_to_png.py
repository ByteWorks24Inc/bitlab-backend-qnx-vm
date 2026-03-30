import sys
from vcdvcd import VCDVCD
import matplotlib.pyplot as plt

vcd_file = sys.argv[1]
output = sys.argv[2]

print("Reading VCD:", vcd_file)

try:
    vcd = VCDVCD(vcd_file)
except Exception as e:
    print("VCD PARSE ERROR:", e)
    exit(1)

signals = vcd.signals  # ✅ FIXED

if not signals:
    print("NO SIGNALS FOUND")
    exit(1)

plt.figure(figsize=(10, 5))

offset = 0

for sig in signals[:3]:  # limit signals
    try:
        tv = vcd[sig].tv

        times = []
        values = []

        for t, v in tv:
            times.append(t)

            if isinstance(v, str):
                if 'x' in v or 'z' in v:
                    values.append(0)
                else:
                    try:
                        values.append(int(v, 2))
                    except:
                        values.append(0)
            else:
                values.append(int(v))

        shifted = [v + offset for v in values]

        plt.step(times, shifted, where='post', label=sig)

        offset += max(values) + 2 if values else 2

    except Exception as e:
        print("Skipping signal:", sig, e)

plt.xlabel("Time")
plt.ylabel("Signals")
plt.title("Waveform")
plt.legend()
plt.grid(True)

plt.savefig(output)

print("PNG generated:", output)
