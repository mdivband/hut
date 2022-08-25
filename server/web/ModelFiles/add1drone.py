import os
import numpy as np
import sys

webRef = str(sys.argv[1])
salt = str(sys.argv[2])
# For now I'm calling this a salt. It's a random identifier

# Set dir
DroneStatusDir = './'+webRef+'/ModelFiles/add1drone.txt'
TaskConfigurationDir = './'+webRef+'/ModelFiles/add1tasks.txt'
PrismDir = './libs/prism-4.7-linux64/bin/prism'
PrismModelDir = './'+webRef+'/PrismModel'
PrismOutputDir1 = './'+webRef+'/ModelFiles/add1results'+str(salt)+'.txt'
PrismOutputDir2 = './'+webRef+'/ModelFiles/add1results_boundedT'+str(salt)+'.txt'

# Process parameters
DroneStatus = np.loadtxt(DroneStatusDir)
PendingTasks = np.loadtxt(TaskConfigurationDir)
NumofDrones = DroneStatus.shape[0]
NumofPendingTasks = PendingTasks.shape[0]
NumofRegions = 7
TaskConfiguration = np.empty(NumofRegions)

for i in range(0, NumofDrones):
    # Abstract drone location
    if DroneStatus[i][0] < 299: DroneStatus[i][0] = 0
    if 299 <= DroneStatus[i][0] < 399: DroneStatus[i][0] = 1
    if 399 <= DroneStatus[i][0] < 499: DroneStatus[i][0] = 2
    if 499 <= DroneStatus[i][0] < 599: DroneStatus[i][0] = 3
    if 599 <= DroneStatus[i][0] < 699: DroneStatus[i][0] = 4
    if 699 <= DroneStatus[i][0] < 799: DroneStatus[i][0] = 5
    if 799 <= DroneStatus[i][0] < 899: DroneStatus[i][0] = 6
    if DroneStatus[i][0] >= 899: DroneStatus[i][0] = 7
    # Abstract task location
    if DroneStatus[i][1] < 399: DroneStatus[i][1] = 1
    if 399 <= DroneStatus[i][1] < 499: DroneStatus[i][1] = 2
    if 499 <= DroneStatus[i][1] < 599: DroneStatus[i][1] = 3
    if 599 <= DroneStatus[i][1] < 699: DroneStatus[i][1] = 4
    if 699 <= DroneStatus[i][1] < 799: DroneStatus[i][1] = 5
    if 799 <= DroneStatus[i][1] < 899: DroneStatus[i][1] = 6
    if DroneStatus[i][1] >= 899: DroneStatus[i][1] = 7
    # Abstract battery level
    if DroneStatus[i][2] < 0.15: DroneStatus[i][2] = 3
    if 0.15 <= DroneStatus[i][2] < 0.4: DroneStatus[i][2] = 0
    if 0.4 <= DroneStatus[i][2] < 0.8: DroneStatus[i][2] = 1
    if DroneStatus[i][2] >= 0.8: DroneStatus[i][2] = 2
    # Correct 'return' and 'delivered' guards
    if DroneStatus[i][0] == 0:
        DroneStatus[i][3] = 0
        DroneStatus[i][5] = 0

for j in range(0, NumofPendingTasks):
    # Abstract task configuration
    if PendingTasks[j] < 399: PendingTasks[j] = 1
    if 399 <= PendingTasks[j] < 499: PendingTasks[j] = 2
    if 499 <= PendingTasks[j] < 599: PendingTasks[j] = 3
    if 599 <= PendingTasks[j] < 699: PendingTasks[j] = 4
    if 699 <= PendingTasks[j] < 799: PendingTasks[j] = 5
    if 799 <= PendingTasks[j] < 899: PendingTasks[j] = 6
    if PendingTasks[j] >= 899: PendingTasks[j] = 7

DroneStatus = DroneStatus.astype(int)
PendingTasks = PendingTasks.astype(int)

for j in range(0, NumofRegions):
    TaskConfiguration[j] = np.count_nonzero(PendingTasks == j + 1)

TaskConfiguration = TaskConfiguration.astype(int)

# Create the PRISM model
text_for_parameters = ''
text_for_agents = ''

for i in range(1, NumofDrones):
    text_for_parameters += 'const int initPlace' + str(i) + ';' + '\n' \
                           + 'const int initTaskLoc' + str(i) + ';' + '\n' \
                           + 'const int initBattery' + str(i) + ';' + '\n' \
                           + 'const int initDelivered' + str(i) + ';' + '\n' \
                           + 'const int initCharge' + str(i) + ';' + '\n' \
                           + 'const int initReturn' + str(i) + ';' + '\n' \
                           + 'const int initAlive' + str(i) + ';' + '\n' \
                           + 'const int initTurning' + str(i) + ';' + '\n' + '\n'

    text_for_agents += 'module agent' + str(i) + '=agent0[' + '\n' \
                       + 'place0=place' + str(i) + ',' + '\n' \
                       + 'taskLoc0=taskLoc' + str(i) + ',' + '\n' \
                       + 'battery0=battery' + str(i) + ',' + '\n' \
                       + 'ready0=ready' + str(i) + ',' + '\n' \
                       + 'return0=return' + str(i) + ',' + '\n' \
                       + 'delivered0=delivered' + str(i) + ',' + '\n' \
                       + 'charge0=charge' + str(i) + ',' + '\n' \
                       + 'alive0=alive' + str(i) + ',' + '\n' \
                       + 'turning0=turning' + str(i) + ',' + '\n' \
                       + 'initPlace0=initPlace' + str(i) + ',' + '\n' \
                       + 'initTaskLoc0=initTaskLoc' + str(i) + ',' + '\n' \
                       + 'initBattery0=initBattery' + str(i) + ',' + '\n' \
                       + 'initDelivered0=initDelivered' + str(i) + ',' + '\n' \
                       + 'initCharge0=initCharge' + str(i) + ',' + '\n' \
                       + 'initReturn0=initReturn' + str(i) + ',' + '\n' \
                       + 'initAlive0=initAlive' + str(i) + ',' + '\n' \
                       + 'initTurning0=initTurning' + str(i) + '\n' \
                       + ']' + '\n' \
                       + 'endmodule' + '\n' + '\n'

ModelTemplate = open(PrismModelDir + '/model_template.txt', 'r')
lines = ModelTemplate.readlines()
ModelTemplate.close()

NewModel = open(PrismModelDir + '/add1_model.txt', 'w')
for line in lines:
    if 'ADD TEXT FOR PARAMETERS' in line:
        line = line.replace(line, text_for_parameters)
    if 'ADD TEXT FOR AGENTS' in line:
        line = line.replace(line, text_for_agents)
    NewModel.write(line)
NewModel.close()

os.rename(PrismModelDir + '/add1_model.txt', PrismModelDir + '/add1_model.pm')

# Create the command
cmd1 = PrismDir + ' ' + PrismModelDir + '/add1_model.pm' + ' ' + PrismModelDir + '/property.pctl' \
       + ' ' + '-prop' + ' ' + 'unbounded' + ' ' + '-sim' + ' ' + '-exportresults' + ' ' + PrismOutputDir1 + ' ' + '-const' + ' '

cmd2 = PrismDir + ' ' + PrismModelDir + '/add1_model.pm' + ' ' + PrismModelDir + '/property.pctl' \
       + ' ' + '-prop' + ' ' + 'bounded' + ' ' + '-sim' + ' ' + '-exportresults' + ' ' + PrismOutputDir2 + ' ' + '-const' + ' '

for i in range(0, NumofDrones):
    cmd1 += 'initPlace' + str(i) + '=' + str(int(DroneStatus[i][0])) + ',' \
            + 'initTaskLoc' + str(i) + '=' + str(DroneStatus[i][1]) + ',' \
            + 'initBattery' + str(i) + '=' + str(DroneStatus[i][2]) + ',' \
            + 'initDelivered' + str(i) + '=' + str(DroneStatus[i][3]) + ',' \
            + 'initCharge' + str(i) + '=' + str(DroneStatus[i][4]) + ',' \
            + 'initReturn' + str(i) + '=' + str(DroneStatus[i][5]) + ',' \
            + 'initAlive' + str(i) + '=' + str(DroneStatus[i][6]) + ',' \
            + 'initTurning' + str(i) + '=' + str(DroneStatus[i][7]) + ','

    cmd2 += 'initPlace' + str(i) + '=' + str(int(DroneStatus[i][0])) + ',' \
            + 'initTaskLoc' + str(i) + '=' + str(DroneStatus[i][1]) + ',' \
            + 'initBattery' + str(i) + '=' + str(DroneStatus[i][2]) + ',' \
            + 'initDelivered' + str(i) + '=' + str(DroneStatus[i][3]) + ',' \
            + 'initCharge' + str(i) + '=' + str(DroneStatus[i][4]) + ',' \
            + 'initReturn' + str(i) + '=' + str(DroneStatus[i][5]) + ',' \
            + 'initAlive' + str(i) + '=' + str(DroneStatus[i][6]) + ',' \
            + 'initTurning' + str(i) + '=' + str(DroneStatus[i][7]) + ','

for j in range(0, NumofRegions):
    cmd1 += 'initTaskP' + str(j + 1) + '=' + str(TaskConfiguration[j])
    cmd2 += 'initTaskP' + str(j + 1) + '=' + str(TaskConfiguration[j])
    if j < NumofRegions - 1:
        cmd1 += ','
    cmd2 += ','

cmd2 += 'T=300:300:18000'

os.system(cmd1)

# Remove first line ('Result') in results.txt
old_file = open(PrismOutputDir1, 'r')
lines = old_file.readlines()
old_file.close()

new_file = open(PrismOutputDir1, 'w')
for line in lines:
    if line.strip('\n') != 'Result':
        new_file.write(line)
new_file.close()

os.system(cmd2)

# Remove first line in results_bounded.txt
old_file = open(PrismOutputDir2, 'r')
lines = old_file.readlines()
old_file.close()

new_file = open(PrismOutputDir2, 'w')
for line in lines[1:]:
    new_file.write(line)
new_file.close()

# Remove the model file
os.remove(PrismModelDir + '/add1_model.pm')

# print('add1drone model done \n')
