ctmc

// Number of rings
const int nplaces = 7;

// Battery drain rates
const double battery_drain_high_mid = 0.00125;
const double battery_drain_mid_low = 0.000625;
const double battery_drain_low_critical = 0.001;
const double battery_charge = 0.0025;

// Background failure rate
// (further from base station => higher failure rate?)
const double place1_fail_r = 0.00004;
const double place2_fail_r = 0.00004;
const double place3_fail_r = 0.00004;
const double place4_fail_r = 0.00004;
const double place5_fail_r = 0.00004;
const double place6_fail_r = 0.00004;
const double place7_fail_r = 0.00004;

// Movement rates (made up for now)
const double mv_0_1_r = 0.01;
const double mv_1_2_r = 0.01;
const double mv_2_3_r = 0.01;
const double mv_3_4_r = 0.01;
const double mv_4_5_r = 0.01;
const double mv_5_6_r = 0.01;
const double mv_6_7_r = 0.01;

// Movement rates
const double mv_1_0_r = 0.01;
const double mv_2_1_r = 0.01;
const double mv_3_2_r = 0.01;
const double mv_4_3_r = 0.01;
const double mv_5_4_r = 0.01;
const double mv_6_5_r = 0.01;
const double mv_7_6_r = 0.01;

// Rotate rate
const double rt_r = 0.03;

// Parameters
const int initPlace0; // 0-7
const int initTaskLoc0; // 0-7
const int initBattery0; // 0,1,2
const int initDelivered0;
const int initCharge0;
const int initReturn0;
const int initAlive0;
const int initTurning0;

const int initPlace1;
const int initTaskLoc1;
const int initBattery1;
const int initDelivered1;
const int initCharge1;
const int initReturn1;
const int initAlive1;
const int initTurning1;

const int initPlace2;
const int initTaskLoc2;
const int initBattery2;
const int initDelivered2;
const int initCharge2;
const int initReturn2;
const int initAlive2;
const int initTurning2;

const int initPlace3;
const int initTaskLoc3;
const int initBattery3;
const int initDelivered3;
const int initCharge3;
const int initReturn3;
const int initAlive3;
const int initTurning3;

const int initPlace4;
const int initTaskLoc4;
const int initBattery4;
const int initDelivered4;
const int initCharge4;
const int initReturn4;
const int initAlive4;
const int initTurning4;

const int initPlace5;
const int initTaskLoc5;
const int initBattery5;
const int initDelivered5;
const int initCharge5;
const int initReturn5;
const int initAlive5;
const int initTurning5;

const int initPlace6;
const int initTaskLoc6;
const int initBattery6;
const int initDelivered6;
const int initCharge6;
const int initReturn6;
const int initAlive6;
const int initTurning6;

const int initPlace7;
const int initTaskLoc7;
const int initBattery7;
const int initDelivered7;
const int initCharge7;
const int initReturn7;
const int initAlive7;
const int initTurning7;

const int initPlace8;
const int initTaskLoc8;
const int initBattery8;
const int initDelivered8;
const int initCharge8;
const int initReturn8;
const int initAlive8;
const int initTurning8;


// Agents
module agent0
  place0   : [ 0..nplaces ] init initPlace0; // 0 is base
  taskLoc0 : [0..nplaces] init initTaskLoc0;
  battery0 : [ 0..3 ] init initBattery0; // 0: low, 1: mid 2: high 3: critical

  ready0 : bool init false;
  delivered0 : [0..1] init initDelivered0; // true to force an initial allocation
  charge0: [0..1] init initCharge0; // true to charge
  return0: [0..1] init initReturn0; // true to return
  alive0 : [0..1] init initAlive0;
  turning0: [0..1] init initTurning0; // true to rotate

  // Battery drain while travelling
  [] (battery0 = 2) & (alive0 = 1) & (place0 != 0) -> battery_drain_high_mid : (battery0' = 1);
  [] (battery0 = 1) & (alive0 = 1) & (place0 != 0) -> battery_drain_mid_low : (battery0' = 0);
  [] (battery0 = 0) & (alive0 = 1) & (place0 != 0) -> battery_drain_low_critical : (battery0' = 3) & (charge0' = 1) & (return0' = 1) & (turning0' = 1);

  // Battery charge at base
  [] (battery0 = 3) & (charge0 = 1) & (alive0 = 1) & (place0 = 0) -> battery_charge: (battery0' = 2) & (charge0' = 0);

  // Generic background failure
  // On its way out
  [] (alive0 = 1) & (place0 = 1) -> place1_fail_r : (alive0' = 0);
  [] (alive0 = 1) & (place0 = 2) -> place2_fail_r : (alive0' = 0);
  [] (alive0 = 1) & (place0 = 3) -> place3_fail_r : (alive0' = 0);
  [] (alive0 = 1) & (place0 = 4) -> place4_fail_r : (alive0' = 0);
  [] (alive0 = 1) & (place0 = 5) -> place5_fail_r : (alive0' = 0);
  [] (alive0 = 1) & (place0 = 6) -> place6_fail_r : (alive0' = 0);
  [] (alive0 = 1) & (place0 = 7) -> place7_fail_r : (alive0' = 0);

  // Task allocation
  [] (place0 = 0) & (alive0 = 1) & (charge0 = 0) & (taskLoc0 = 0) & (!ready0) -> (alloc' = true) & (ready0' = true);
  [] (ready0) & (nextTask !=0) -> (taskLoc0' = nextTask) & (nextTask' = 0) & (ready0' = false) & (delivered0' = 0);

  // Task reallocation
  [] (taskLoc0 = 1) & (charge0 = 1) & (delivered0 = 0) & (allocTasksP1 <= maxTasksPerRegion - 1) -> (taskLoc0' = 0) & (allocTasksP1' = allocTasksP1 + 1) & (nextTask' = 0) & (done' = false);
  [] (taskLoc0 = 2) & (charge0 = 1) & (delivered0 = 0) & (allocTasksP2 <= maxTasksPerRegion - 1) -> (taskLoc0' = 0) & (allocTasksP2' = allocTasksP2 + 1) & (nextTask' = 0) & (done' = false);
  [] (taskLoc0 = 3) & (charge0 = 1) & (delivered0 = 0) & (allocTasksP3 <= maxTasksPerRegion - 1) -> (taskLoc0' = 0) & (allocTasksP3' = allocTasksP3 + 1) & (nextTask' = 0) & (done' = false);
  [] (taskLoc0 = 4) & (charge0 = 1) & (delivered0 = 0) & (allocTasksP4 <= maxTasksPerRegion - 1) -> (taskLoc0' = 0) & (allocTasksP4' = allocTasksP4 + 1) & (nextTask' = 0) & (done' = false);
  [] (taskLoc0 = 5) & (charge0 = 1) & (delivered0 = 0) & (allocTasksP5 <= maxTasksPerRegion - 1) -> (taskLoc0' = 0) & (allocTasksP5' = allocTasksP5 + 1) & (nextTask' = 0) & (done' = false);
  [] (taskLoc0 = 6) & (charge0 = 1) & (delivered0 = 0) & (allocTasksP6 <= maxTasksPerRegion - 1) -> (taskLoc0' = 0) & (allocTasksP6' = allocTasksP6 + 1) & (nextTask' = 0) & (done' = false);
  [] (taskLoc0 = 7) & (charge0 = 1) & (delivered0 = 0) & (allocTasksP7 <= maxTasksPerRegion - 1) -> (taskLoc0' = 0) & (allocTasksP7' = allocTasksP7 + 1) & (nextTask' = 0) & (done' = false);

  // Success
  [] (place0 = taskLoc0) & (taskLoc0 != 0) & (alive0 = 1) -> (delivered0' = 1) & (return0' = 1) & (taskLoc0' = 0) & (turning0' = 1);

  // Rotate
  [] (turning0 = 1) & (alive0 = 1) -> rt_r: (turning0' = 0);

  // Movement
  // Go out
  [] (place0 = 0) & (alive0 = 1) & (place0 != taskLoc0) & (delivered0 = 0) & (return0 = 0)  & (turning0 = 0) -> mv_0_1_r : (place0' = 1);
  [] (place0 = 1) & (alive0 = 1) & (place0 != taskLoc0) & (delivered0 = 0) & (return0 = 0) -> mv_1_2_r : (place0' = 2);
  [] (place0 = 2) & (alive0 = 1) & (place0 != taskLoc0) & (delivered0 = 0) & (return0 = 0) -> mv_2_3_r : (place0' = 3);
  [] (place0 = 3) & (alive0 = 1) & (place0 != taskLoc0) & (delivered0 = 0) & (return0 = 0) -> mv_3_4_r : (place0' = 4);
  [] (place0 = 4) & (alive0 = 1) & (place0 != taskLoc0) & (delivered0 = 0) & (return0 = 0) -> mv_4_5_r : (place0' = 5);
  [] (place0 = 5) & (alive0 = 1) & (place0 != taskLoc0) & (delivered0 = 0) & (return0 = 0) -> mv_5_6_r : (place0' = 6);
  [] (place0 = 6) & (alive0 = 1) & (place0 != taskLoc0) & (delivered0 = 0) & (return0 = 0) -> mv_6_7_r : (place0' = 7);

  // Come back
  [] (place0 = 1) & (alive0 = 1) & (return0 = 1) & (turning0 = 0) -> mv_1_0_r : (place0' = 0) & (return0' = 0) & (turning0' = 1);
  [] (place0 = 2) & (alive0 = 1) & (return0 = 1) & (turning0 = 0) -> mv_2_1_r : (place0' = 1);
  [] (place0 = 3) & (alive0 = 1) & (return0 = 1) & (turning0 = 0) -> mv_3_2_r : (place0' = 2);
  [] (place0 = 4) & (alive0 = 1) & (return0 = 1) & (turning0 = 0) -> mv_4_3_r : (place0' = 3);
  [] (place0 = 5) & (alive0 = 1) & (return0 = 1) & (turning0 = 0) -> mv_5_4_r : (place0' = 4);
  [] (place0 = 6) & (alive0 = 1) & (return0 = 1) & (turning0 = 0) -> mv_6_5_r : (place0' = 5);
  [] (place0 = 7) & (alive0 = 1) & (return0 = 1) & (turning0 = 0) -> mv_7_6_r : (place0' = 6);
endmodule

module agent1=agent0[
place0=place1,
taskLoc0=taskLoc1,
battery0=battery1,
ready0=ready1,
return0=return1,
delivered0=delivered1,
charge0=charge1,
alive0=alive1,
turning0=turning1,
initPlace0=initPlace1,
initTaskLoc0=initTaskLoc1,
initBattery0=initBattery1,
initDelivered0=initDelivered1,
initCharge0=initCharge1,
initReturn0=initReturn1,
initAlive0=initAlive1,
initTurning0=initTurning1
]
endmodule

module agent2=agent0[
place0=place2,
taskLoc0=taskLoc2,
battery0=battery2,
ready0=ready2,
return0=return2,
delivered0=delivered2,
charge0=charge2,
alive0=alive2,
turning0=turning2,
initPlace0=initPlace2,
initTaskLoc0=initTaskLoc2,
initBattery0=initBattery2,
initDelivered0=initDelivered2,
initCharge0=initCharge2,
initReturn0=initReturn2,
initAlive0=initAlive2,
initTurning0=initTurning2
]
endmodule

module agent3=agent0[
place0=place3,
taskLoc0=taskLoc3,
battery0=battery3,
ready0=ready3,
return0=return3,
delivered0=delivered3,
charge0=charge3,
alive0=alive3,
turning0=turning3,
initPlace0=initPlace3,
initTaskLoc0=initTaskLoc3,
initBattery0=initBattery3,
initDelivered0=initDelivered3,
initCharge0=initCharge3,
initReturn0=initReturn3,
initAlive0=initAlive3,
initTurning0=initTurning3
]
endmodule

module agent4=agent0[
place0=place4,
taskLoc0=taskLoc4,
battery0=battery4,
ready0=ready4,
return0=return4,
delivered0=delivered4,
charge0=charge4,
alive0=alive4,
turning0=turning4,
initPlace0=initPlace4,
initTaskLoc0=initTaskLoc4,
initBattery0=initBattery4,
initDelivered0=initDelivered4,
initCharge0=initCharge4,
initReturn0=initReturn4,
initAlive0=initAlive4,
initTurning0=initTurning4
]
endmodule

module agent5=agent0[
place0=place5,
taskLoc0=taskLoc5,
battery0=battery5,
ready0=ready5,
return0=return5,
delivered0=delivered5,
charge0=charge5,
alive0=alive5,
turning0=turning5,
initPlace0=initPlace5,
initTaskLoc0=initTaskLoc5,
initBattery0=initBattery5,
initDelivered0=initDelivered5,
initCharge0=initCharge5,
initReturn0=initReturn5,
initAlive0=initAlive5,
initTurning0=initTurning5
]
endmodule

module agent6=agent0[
place0=place6,
taskLoc0=taskLoc6,
battery0=battery6,
ready0=ready6,
return0=return6,
delivered0=delivered6,
charge0=charge6,
alive0=alive6,
turning0=turning6,
initPlace0=initPlace6,
initTaskLoc0=initTaskLoc6,
initBattery0=initBattery6,
initDelivered0=initDelivered6,
initCharge0=initCharge6,
initReturn0=initReturn6,
initAlive0=initAlive6,
initTurning0=initTurning6
]
endmodule

module agent7=agent0[
place0=place7,
taskLoc0=taskLoc7,
battery0=battery7,
ready0=ready7,
return0=return7,
delivered0=delivered7,
charge0=charge7,
alive0=alive7,
turning0=turning7,
initPlace0=initPlace7,
initTaskLoc0=initTaskLoc7,
initBattery0=initBattery7,
initDelivered0=initDelivered7,
initCharge0=initCharge7,
initReturn0=initReturn7,
initAlive0=initAlive7,
initTurning0=initTurning7
]
endmodule

module agent8=agent0[
place0=place8,
taskLoc0=taskLoc8,
battery0=battery8,
ready0=ready8,
return0=return8,
delivered0=delivered8,
charge0=charge8,
alive0=alive8,
turning0=turning8,
initPlace0=initPlace8,
initTaskLoc0=initTaskLoc8,
initBattery0=initBattery8,
initDelivered0=initDelivered8,
initCharge0=initCharge8,
initReturn0=initReturn8,
initAlive0=initAlive8,
initTurning0=initTurning8
]
endmodule


const int maxTasksPerRegion = 40;

// Tweak these based on the initial task allocation
const int initTaskP1;
const int initTaskP2;
const int initTaskP3;
const int initTaskP4;
const int initTaskP5;
const int initTaskP6;
const int initTaskP7;

global allocTasksP1 : [ 0 .. maxTasksPerRegion ] init initTaskP1;
global allocTasksP2 : [ 0 .. maxTasksPerRegion ] init initTaskP2;
global allocTasksP3 : [ 0 .. maxTasksPerRegion ] init initTaskP3;
global allocTasksP4 : [ 0 .. maxTasksPerRegion ] init initTaskP4;
global allocTasksP5 : [ 0 .. maxTasksPerRegion ] init initTaskP5;
global allocTasksP6 : [ 0 .. maxTasksPerRegion ] init initTaskP6;
global allocTasksP7 : [ 0 .. maxTasksPerRegion ] init initTaskP7;

// Allocate tasks to drones 
global nextTask : [0 .. nplaces] init 0;
global alloc : bool init false;
global done : bool init false;

module Allocator
  checkNext : [ 0 .. nplaces ] init 1;

  // Redirect checkNext
  [] (nextTask = 0) & (!done) -> (checkNext' = 1);

  // Logic to find next region
  [] (checkNext = 1) & (allocTasksP1 = 0) -> (checkNext' = 2) & (done' = true);
  [] (checkNext = 2) & (allocTasksP2 = 0) -> (checkNext' = 3) & (done' = true);
  [] (checkNext = 3) & (allocTasksP3 = 0) -> (checkNext' = 4) & (done' = true);
  [] (checkNext = 4) & (allocTasksP4 = 0) -> (checkNext' = 5) & (done' = true);
  [] (checkNext = 5) & (allocTasksP5 = 0) -> (checkNext' = 6) & (done' = true);
  [] (checkNext = 6) & (allocTasksP6 = 0) -> (checkNext' = 7) & (done' = true);
  [] (checkNext = 7) & (allocTasksP7 = 0) -> (checkNext' = 0) & (done' = true); // No more tasks

  // Assign the next task
  [] (checkNext = 1) & (allocTasksP1 > 0) & (alloc) -> (nextTask' = 1) & (allocTasksP1' = allocTasksP1 - 1) & (alloc' = false);
  [] (checkNext = 2) & (allocTasksP2 > 0) & (alloc) -> (nextTask' = 2) & (allocTasksP2' = allocTasksP2 - 1) & (alloc' = false);
  [] (checkNext = 3) & (allocTasksP3 > 0) & (alloc) -> (nextTask' = 3) & (allocTasksP3' = allocTasksP3 - 1) & (alloc' = false);
  [] (checkNext = 4) & (allocTasksP4 > 0) & (alloc) -> (nextTask' = 4) & (allocTasksP4' = allocTasksP4 - 1) & (alloc' = false);
  [] (checkNext = 5) & (allocTasksP5 > 0) & (alloc) -> (nextTask' = 5) & (allocTasksP5' = allocTasksP5 - 1) & (alloc' = false);
  [] (checkNext = 6) & (allocTasksP6 > 0) & (alloc) -> (nextTask' = 6) & (allocTasksP6' = allocTasksP6 - 1) & (alloc' = false);
  [] (checkNext = 7) & (allocTasksP7 > 0) & (alloc) -> (nextTask' = 7) & (allocTasksP7' = allocTasksP7 - 1) & (alloc' = false);
endmodule