import sys
from vcdvcd import VCDVCD
import matplotlib.pyplot as plt

vcd_path = sys.argv[1]
output_path = sys.argv[2]

vcd = VCDVCD(vcd_path)

plt.figure(figsize=(14, 5))

offset = 0

for signal in vcd.signals[:8]:  # limit signals (important)
    tv = vcd[signal]['tv']

    times = []
    values = []

    for t, v in tv:
        try:
            val = int(v, 2)
        except:
            val = 0

        times.append(t)
        values.append(val + offset)

    if times:
        plt.step(times, values, where='post', label=signal)

    offset += 2

plt.legend(loc="upper right")
plt.xlabel("Time")
plt.ylabel("Signals")
plt.title("Waveform")

plt.tight_layout()
plt.savefig(output_path)
