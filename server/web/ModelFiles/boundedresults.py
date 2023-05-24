import numpy as np
import matplotlib.pyplot as plt
import sys

webRef = str(sys.argv[1])

x = np.arange(0, 60, 1)
currentbounded = np.loadtxt('./'+webRef+'/ModelFiles/currentResults_boundedT.txt')
add1bounded = np.loadtxt('./'+webRef+'/ModelFiles/add1results_boundedT.txt')
remove1bounded = np.loadtxt('./'+webRef+'/ModelFiles/remove1results_boundedT.txt')
#currentbounded = np.loadtxt('./currentResults_boundedT.txt')
#add1bounded = np.loadtxt('./add1results_boundedT.txt')
#remove1bounded = np.loadtxt('./remove1results_boundedT.txt')


# situations without recharging
# currentbounded_norecharge = np.loadtxt('./PrismOutput/currentresults_bounded_norecharge.txt')
# add1bounded_norecharge = np.loadtxt('./PrismOutput/add1results_bounded_norecharge.txt')
# remove1bounded_norecharge = np.loadtxt('./PrismOutput/remove1results_bounded_norecharge.txt')

fig, ax = plt.subplots()
plt.plot(x, currentbounded[:,1], 'b', label='Current')
plt.plot(x, add1bounded[:,1], 'g', label='Add 1 Drone')
plt.plot(x, remove1bounded[:,1], 'r', label='Remove 1 Drone')

# situations without recharging
# plt.plot(x, currentbounded_norecharge[:,1], 'b', linestyle='--', label='Current (without recharging)')
# plt.plot(x, add1bounded_norecharge[:,1], 'g', linestyle='--', label='Add 1 Drone (without recharging)')
# plt.plot(x, remove1bounded_norecharge[:,1], 'r', linestyle='--', label='Remove 1 Drone (without recharging)')

plt.xlim(1, 30, 1)
plt.ylim(0, 1.0, 0.2)
plt.xlabel('Simulation Time (min)', fontsize='18')
plt.ylabel('P Complete All Tasks', fontsize='18')
plt.legend(loc='upper left')
plt.grid(color='lightgrey', linestyle='-.', linewidth=2)
ax.spines['top'].set_visible(False)
ax.spines['right'].set_visible(False)
plt.savefig('./'+webRef+'/ModelFiles/bounded_results.png', format='png', dpi=100)
# plt.show()