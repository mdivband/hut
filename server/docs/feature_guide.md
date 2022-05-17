# DJI Hut Server - Feature Guide
### University of Southampton
##### Last Updated: 13/09/2018, Joe Early
------
This document gives a brief overview of all the features that are implemented or planned in the Multi UAV Controller platform. It is ordered by chronological progression of how a user would interact with the application. A video demonstrating some of the features can be found [here][1].

### Table of Contents

1. [Start Menu](#markdown-header-start-menu)
2. [Operating Modes](#markdown-header-operating-modes)
3. [Agents](#markdown-header-operating-modes)
4. [Tasks](#markdown-header-tasks)
5. [Allocation](#markdown-header-allocation)
6. [Targets](#markdown-header-targets)
7. [Hazards](#markdown-header-hazards)
8. [Overlays](#markdown-header-overlays)
9. [Visualizer](#markdown-header-visualizer)

### Start Menu

Once the DJI Hut Server is running, it can be accessed at localhost:8000 by default. This takes the user to the start screen, which presents the options [Create Scenario](#markdown-header-create-scenario), [Load Scenario](#markdown-header-load-scenario) and [Sandbox Mode](#markdown-header-sandbox-mode):

![Start Menu][start_menu]

##### Create Scenario

Unimplemented. Should allow the user to create a scenario and save it to a JSON file.

##### Load Scenario

Presents the user with a list of scenarios available on the server (in the [scenarios folder][2]). The user can select one of them to start an operation in [Scenario Mode](#markdown-header-scenario-mode).

##### Returning to the start menu

If the user returns to the start menu while an operation is running, they are presented with an alternative screen that allows them to resume the current operation, or abort it (unimplemented).

### Operating Modes

There are currently two operating modes that the application can run in: [Scenario Mode](#markdown-header-scenario-mode) and [Sandbox Mode](#markdown-header-sandbox-mode). They are both initiated from the [Start Menu](#markdown-header-start-menu). In future, more operating modes could be added, such as the [Create Scenario](#markdown-header-create-scenario) mode and a field operation mode for real operations.

##### Scenario Mode

This operating mode is used to demonstrate how the application could be used for an actual mission. A scenario presents a possible situation in which the application might be used, such as in a disaster environment. The user is presented with the mission parameters, and then given a number of agents to use in order to complete the mission. There is no completion point of the mission as yet, however the user is given basic goals through the mission parameters presented at the start of the mission.

Scenarios are loaded from JSON files that provide all the information required to initiate the starting state of the operation. A breakdown of the scenario file is provided [here][3].

##### Sandbox Mode

In sandbox mode, the user starts with a blank canvas. Unlike scenario mode, they are not given any agents to start with, nor is there any objective for them to complete. Instead, the user can add as many agents as they like, and certain features are available that aren't in scenario mode, such as deleting agents and triggering communication losses with UAVs. Such features wouldn't make any sense in scenario mode or when running the application in the field, but they can be useful for testing.

### Agents

UAVs (and potentially other autonomous vehicles) are known as agents within the application. There are two types of agents in the application - virtual and 'real'. Virtual agents allow a user to run an operation without needing physical drones, meaning things like scalability testing can be performed with ease. The application is mostly agnostic about whether an agent is virtual or not, however certain features such as deleting agents are disabled for physical agents. The underlying implementation for virtual and physical agents differs for a few things; for example, movement for a virtual agent is simply done within the server but a physical agent actually communicates with the UAV to get its current position (be it in a simulation - DJI Simulator - or actually flying).

In [Sandbox Mode](#markdown-header-sandbox-mode), the user can add agents in a similar way to how they add tasks. The user can also trigger communication black-outs for agents - this is useful for testing ways of reacting to communication losses with an agent. In [Scenario Mode](#markdown-header-scenario-mode), the user cannot add or delete agents, or trigger drop-outs; instead agents are given to them as part of the scenario initialisation.

### Tasks

Tasks allow the user to control [Agents](#markdown-header-agents). There are currently four different types of tasks. The tasks can be added by entering edit mode and using the tools presented in the top right hand corner of the map:

![Edit Task Interface 1][edit_task_interface_1]

##### Waypoint Tasks
Waypoint tasks direct an agent to a given position, and are complete when an agent reaches that position. They are the simplest tasks available to the user. Only one agent can be assigned to a waypoint task. The use case for a waypoint task performing a one-time investigation of a specific location.
##### Monitor Tasks
Monitor tasks work similarly to waypoint tasks, except once an agent arrives at the task position, it remains there - the task is now in progress. The agent can be manually reassigned to another task, but it won't be automatically allocated to another task once it has reached the target position. Monitor tasks are useful when a particular position requires constant monitoring.
##### Patrol Tasks
Patrol tasks are composed of a path of points forming a loop. If an agent is assigned to a patrol task, it will take the shortest route to get to the patrol, and then begin following the path of points until manually reassigned. Multiple agents can be assigned to a patrol task, and they will pause if they get too close together - this means they are consistently spaced around the patrol to maximise their combined coverage. Patrol tasks are useful for maintaining up to date information about a perimeter.
##### Region Tasks
Region tasks are an extension of patrol tasks - they use the same underlying idea of following a path of points that form a loop - but that path is generated to scan over a region. The user drags out a rectangular region, to which multiple agents can be assigned to continuously scan. The region path is calculated as follows:

![Region Path][region_path]

Tasks can be edited once they have been placed. Waypoint and monitor tasks can be moved by dragging their marker when in edit mode, patrol tasks can be edited by dragging the points that make up their path, and region tasks can be edited by moving the corners of the region.

### Allocation

Once [Tasks](#markdown-header-tasks) have been placed, [Agents](#markdown-header-agents) need to be allocated to them to actually carry out the task objectives. The user must be in edit mode to create allocations.

There are two ways of creating allocations. The first is manual allocation: an agent can be assigned to a task by dragging the agent towards the task; this will produce an allocation arrow and assign the task to that agent. The other method is by pressing the 'Run Auto Allocation', which will allocate agents to tasks using an algorithm (currently MaxSum). Once the allocation is complete, the user can click 'Confirm Allocation' to commit to the allocation and actually begin to move the agents towards their respective tasks. While in edit mode, the agents are told to hold their current position, until the user returns to monitor mode (either by confirming the allocation or returning to the old allocation).

Once allocated, it is possible to edit the route that an agent will take to get to its task. This is useful for exploring extra areas without having to position a task there. By using the handles on the yellow allocation arrow in edit mode, the path can be changed:

![Edit Path][edit_path]

### Targets

Targets are used to give some kind of objective in [Scenario Mode](#markdown-header-scenario-mode). They are hidden before hand, but once an agent gets close enough to one it appears. The only target at the moment is a human target (i.e. spotting people caught in a disaster zone). Targets could be used as an objective for scenario mode, for example find a certain number of targets in a zone within a certain time.

### Hazards

In a similar vein to targets, hazards are used to give a context in [Scenario Mode](#markdown-header-scenario-mode). Hazards represent something in the environment that needs monitoring, for example a fire (currently the only hazard that is implemented). Hazards are currently static, but could be extended to be made dynamic, for example a fire that spreads. This would more accurately simulate real environments.

### Overlays

The MultiUAV controller uses map overlays to display extra information to the user. There are currently two heatmap overlays - one for showing the areas in which a fire [Hazard](#markdown-header-hazards) has been detected, and the other to display where [Agents](#markdown-header-agents) have recently explored. The explored heatmap decays over time as the situation at the position might have changed since the agent was last there, but the fire overlay does not decay following the assumption that the fire will still be in that position. There is one additional overlay that displays the path for [Region Tasks](#markdown-header-region-tasks). The overlays can be turned on and off using the menu in the top left hand corner of the map:

![Overlay Menu][overlay_menu]

### Visualizer

A potential extension to the project is to use an existing piece of visualization software to display the state of the agents to assist the user. There is already a REST API endpoint in place that provides all the necessary information for this plugin, just the plugin itself has not been implemented.

[1]: https://bitbucket.org/dhaminda/hutserver/downloads/MultiUAVControllerVideo.zip
[2]: ../web44101/scenarios
[3]: ./scenario_files.md

[start_menu]: https://bitbucket.org/dhaminda/hutserver/raw/master/docs/img/start_menu.png "Start Menu"
[edit_task_interface_1]: https://bitbucket.org/dhaminda/hutserver/raw/master/docs/img/edit_task_interface_1.png "Edit Task Interface 1"
[region_path]: https://bitbucket.org/dhaminda/hutserver/raw/master/docs/img/region_path.png "Region Path"
[edit_path]: https://bitbucket.org/dhaminda/hutserver/raw/master/docs/img/edit_path.png "Edit Path"
[overlay_menu]: https://bitbucket.org/dhaminda/hutserver/raw/master/docs/img/overlay_menu.png "Overlay Menu"
