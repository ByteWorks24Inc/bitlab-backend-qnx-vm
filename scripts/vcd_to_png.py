import sys
from vcdvcd import VCDVCD
import matplotlib.pyplot as plt

vcd_file = sys.argv[1]
output = sys.argv[2]

print("Reading VCD:", vcd_file)

try:
    vcd = VCDVCD(vcd_file)
except Exception as e:
    print("ERROR loading VCD:", e)
    exit(1)

signals = vcd.signals

print("Signals found:", signals)

plt.figure(figsize=(10, 5))

offset = 0
plotted_any = False

for sig in signals:
    try:
        tv = vcd[sig].tv

        if not tv:
            continue

        times = []
        values = []

        for t, v in tv:
            times.append(t)

            # 🔥 UNIVERSAL VALUE HANDLING
            if isinstance(v, str):
                v = v.lower().strip()

                # binary vector
                if v.startswith("b"):
                    try:
                        values.append(int(v[1:], 2))
                    except:
                        values.append(0)

                # single bit
                elif v in ['0', '1']:
                    values.append(int(v))

                # unknown states
                elif v in ['x', 'z', 'u']:
                    values.append(0)

                else:
                    values.append(0)

            else:
                try:
                    values.append(int(v))
                except:
                    values.append(0)

        if len(times) == 0 or len(values) == 0:
            continue

        shifted = [val + offset for val in values]

        plt.step(times, shifted, where='post', label=sig)

        offset += max(values) + 2 if max(values) > 0 else 2
        plotted_any = True

    except Exception as e:
        print("Skipping:", sig, e)

# 🚨 if nothing plotted → fallback (IMPORTANT)
if not plotted_any:
    print("WARNING: No valid waveform data found")

    # dummy fallback so graph is never empty
    plt.step([0, 10, 20], [0, 1, 0], where='post', label="fallback")

plt.xlabel("Time")
plt.ylabel("Signals")
plt.title("Waveform")
plt.legend()
plt.grid(True)

plt.savefig(output)

print("PNG generated:", output)
