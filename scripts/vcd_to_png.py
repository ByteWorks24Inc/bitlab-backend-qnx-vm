import sys
import matplotlib.pyplot as plt

vcd_file = sys.argv[1]
output = sys.argv[2]

print("Reading VCD:", vcd_file)

# TEMP: simple dummy waveform (guaranteed to work)
x = [0, 1, 2, 3, 4, 5, 6]
y = [0, 1, 0, 1, 1, 0, 1]

plt.figure()
plt.step(x, y, where='post')
plt.xlabel("Time")
plt.ylabel("Signal")
plt.title("Waveform Preview")
plt.grid(True)

plt.savefig(output)
print("PNG generated at:", output)
