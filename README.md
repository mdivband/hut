# Hut - Documentation
### University of Southampton
------
This repository is split into two parts:

## DJI Hut Controller

The DJI Hut Controller is an Android mobile app designed to allow a drone operator to monitor and control a drone that is flying autonomously or under manual control. In autonomous mode, the drone receives commands from a server (see the [server repository][1]), and carries them out without required any input from the operator. The drone can be swapped to manual control, in which case the operator has complete control over the drone and any incoming commands from the server are ignored. 

## DJI Hut Server

The DJI Hut Server is a client-server application that is used for coordinating a fleet of drones. The organisation and planning is handled by a browser application, and the commands are relayed through to the drones through a mobile aplication (see the [Android app repository][1]). 

Installation guides for each of the two parts can be found in the documentation in each of their respective directories.

## Repository Guidelines

The workflow we are using for this repository is essentially the Github Flow:
https://guides.github.com/introduction/flow/

Any code in the master branch should be tested and useable. The master branch should only be updated via pull request from the dev branch. The dev branch should contain code that is in the process of being tested before being merged with the master branch. When code is being actively developed, this should be done in feature branches created by the developer working on the code. A feature branch should only be merged directly to the dev branch, not to the master branch.

All functional changes should be made optional. These options should be added as optional fields in the scenario files. This new option should be documented in the scenario files documentation.