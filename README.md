# Hut - Documentation
## DJI Hut Controller - Documentation
------
The DJI Hut Controller is an Android mobile app designed to allow a drone operator to monitor and control a drone that is flying autonomously or under manual control. In autonomous mode, the drone receives commands from a server (see the [server repository][1]), and carries them out without required any input from the operator. The drone can be swapped to manual control, in which case the operator has complete control over the drone and any incoming commands from the server are ignored. 

### Table of Contents

1. [Repository Structure](#repository-structure)
2. [Project Architecture](#project-architecture)
3. [Setup Guide](#setup-guide)
4. [API Keys](#api-keys)
5. [App Architecture](#app-architecture)
6. [Logging](#logging)

### Repository Structure

The Android app repository has a relatively simple structure. The root directory [hut_dji_controller][2] contains three sub-directories: [app][3], [dJISDKLIB][4] and [docs][5] (ignoring the gradle directory required for a Gradle project). The app directory contains the actual Android application code, including tests, and is structured in the usual way with src folders and such. The dJISDKLIB directory contains the [DJI SDK][6] library that is used by the app to interact with the drone and its remote controller. Finally, the docs folder contains the documentation of the project and any images that are used in the documentation.  

### Project Architecture

The overall project architecture is spread across two repostiories: this one and the [server repository][1]. The mobile application contained in the repository communicates with the drone remote controller via USB, which in turn communicates with the drone itself. The [DJI SDK][6] provides an interface for communicating with the drone and provides flight control and mission manager components to interact with the drone. 

The mobile application communicates with the server to relay information about the drone (such as position), and also to receive commands regarding the drone's current mission. The server is reponsible for all the planning and coordination of drone's missions. The mobile application initially registers itself with the server using the server's REST API. After establishing a connection, the server configures a channel on its [RabbitMQ][7] server for this particular drone and informs the application of this newly opened channel. All communication between the application and the server then proceeds via RabbitMQ rather than the REST API.

The drone can be configured to run in a simulation without requiring actual flight; this is done using the [DJI Simulator for PC][8] ([installation guide][9]). In this case, the drone is connected to a Windows laptop or PC running the DJI Simulator, and all 'flying' is done in a virtual environment - both autonomously and via the drone remote controller. An overview of the entire project architecture is given below:

![Project Architecture Overview][project_architecture]

### Setup Guide

There are several steps required to setup a working version of the application and server. The recommended development environment is [Android Studio][11]. In addition, the [DJI PC Simulator][8] ([installation guide][9]) is required if you want to simulate the drone movements without having to fly it (recommended).

Since there is an element of networking between the mobile app and the server, there is some configuration required to connect succesfully. If the server is configured correctly (see the server setup guide found [here][25]), then there should be a REST API and a RabbitMQ server that the mobile application can connect to. To configure the mobile application to connect to your server, replace *server-ip* with the IP address of your server in the following line of the [ServerHandler][12] class:
```java
private static final String serverAddress = "http:/server-ip:8000/";
```
In addition to configuring the server IP address, it may also be necessary to configure a VPN on the Android device if the server is running on the University network. A guide to setting up an Android VPN for the University network can be found [here][13].

Once configured correctly, the following steps allow for a working installation on an Android device:

1. Run server (if not already running).
2. Connect Android device to PC running development environment via USB and install mobile application on Android device.
3. Start application - should go to drone connection interface showing no connection with drone controller or drone.
4. Connect Android device to drone controller via USB (requires disconnection of Android device from PC).
5. Power on drone controller and drone; app should show active connection with both after a few seconds.
6. (Optional  - Simulation Mode) Run DJI PC Simulator and connect drone to PC via USB to fly the drone in simulation mode. The DJI PC Simulator should show the serial number of the drone if succesfully connected. Click 'Start Simulation' and then 'Display Simulation' to begin simulating drone control within the DJI PC Simulator.
7. Press 'Connect to Server' on mobile app (this button is only displayed if the drone controller and drone are succesfully connected to each other and the mobile app). This will navigate the app to the main user interface if the application is able to connect to server succesfully. 
8. Push both sticks on the drone controller to the opposite bottom outer corners to activate the drone. If running in simulation mode, the DJI PC Simulator should show the propellers of the simulated drone begin to spin. The drone can now be flown within the simulation by using the drone controller, as well as by sending commands from the server. The drone's position should be visibly chaning on the map interface on the application as the drone moves within the simulation. 

### API Keys

There are two API keys requied for the project, one for the [Google Maps SDK][22] and another for the [DJI SDK][23]. The repository already contains working API keys so you shouldn't need to generate your own, but if you do, the above links will allow you to generate the keys. To update the API keys, simply change the following lines in the [AndroidManifest.xml][24] file:
```
<meta-data
    android:name="com.dji.sdk.API_KEY"
    android:value="DJI SDK Key" />
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="Google Maps SDK Key" />
```
replacing `DJI SDK Key` and `Google Maps SDK Key` with the respective keys.

### App Architecture

The mobile application follows a [model-view-controller][14] (MVC) architecture pattern. The view is comprised of the XML files that take care of the on-screen rendering and layout. The controller is made of three different sections: the activity controllers, the application instance and the model handlers. The activity controllers are reponsible for managing the views; there is one activity class per XML (view) file. [DJIHutApplication][15] is the application instance - the main controller file. It has references to each of the model handlers: the parts of the controller that are responsible for dealing with the model. The [DroneHandler][16] is the primary manager of the DJI SDK (the DJI SDK is seen as part of the model); it takes care of establishing connections with the drone controller and the drone, and broadcasts any changes of the drone connection to relevant listeners. The [MissionHandler][17] processes incoming missions from the server and updates the commands for the drone if necessary. Finally, the [ServerHandler][18] is responsible for establishing a connection to the server, sending incoming messages to the correct handler for processing (e.g. mission messages to the [MissionHandler][17]), and sending information from the mobile application to the server (e.g. relaying the drone's position). An overview of the app architure is shown below:

<p align="center">
    <img width="400" src="https://github.com/mdivband/hut/blob/master/controller/docs/app_architecture.png?raw=true" alt="Material Bread logo" >
</p>


The app has two main views (or activities as they are called in Android app devleopment) - the connection activity and the overview activity. The connection activity is responsible for setting up the connection with the drone controller and the drone, as well as initiating the connection with the server. The overview activity displays the drone's position on a map as well as the feed from the drone's camera. The diagram below summaries the flow between the activities:

![App Flow][app_flow]

### Logging

The Android app SDK comes with its own [logging API][19] - `android.util.Log`. Each call to the log function requires a tag to indicate the source of the log message. The [DJIHutApplication][15] class has a master tag (currently DJIHut) which other classes can use to generate their own (unique) tags, for example in the [ServerHandler][18]:
```java
private static final String LOG_TAG = DJIHutApplication.MASTER_TAG + "_ServerHandler";
```
Since the Android device has to be connected to the drone controller via USB when using the application, calls to print to the standard output and other logging calls will not be shown in the console of the development environment. There are methods to send the log files wirelessly, however the easiest solution is to install a logging app onto the Android device itself to monitor the logs in realtime. The recommended app is [CatLog][20], and although it asks for root access on the Android device it is possible to use it without doing so by using [this guide][21]. Once installed, filter by the master tag (DJIHut) in CatLog to see the log for the DJI Hut Controller application.

[1]: https://github.com/mdivband/hut/tree/master/server
[2]: /controller
[3]: /controller/app/
[4]: /controller/dJISDKLIB/
[5]: /controller/docs/
[6]: https://developer.dji.com/mobile-sdk/
[7]: http://www.rabbitmq.com/
[8]: https://developer.dji.com/mobile-sdk/downloads/
[9]: https://forum.dji.com/thread-42536-1-1.html
[10]: https://www.jetbrains.com/idea/
[11]: https://developer.android.com/studio/
[12]: /controller/app/src/main/java/com/dji/hut_controller/handler/ServerHandler.java
[13]: https://www.southampton.ac.uk/ageing/postgraduate/welcome/vpn.page
[14]: https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller
[15]: /controller/app/src/main/java/com/dji/hut_controller/DJIHutApplication.java
[16]: /controller/app/src/main/java/com/dji/hut_controller/handler/DroneHandler.java
[17]: /controller/app/src/main/java/com/dji/hut_controller/handler/MissionHandler.java
[18]: /controller/app/src/main/java/com/dji/hut_controller/handler/ServerHandler.java
[19]: https://developer.android.com/reference/android/util/Log
[20]: https://play.google.com/store/apps/details?id=com.nolanlawson.logcat&hl=en_GB
[21]: https://www.utest.com/articles/capture-android-logs-with-an-app-no-root-or-usb-cable-required
[22]: https://developers.google.com/maps/documentation/android-sdk/signup
[23]: https://developer.dji.com/mobile-sdk/documentation/quick-start/index.html#generate-an-app-key
[24]: /controller/app/src/main/AndroidManifest.xml
[25]: https://github.com/mdivband/hut/blob/master/README.md#setup-guide-1

[project_architecture]: https://github.com/mdivband/hut/blob/master/controller/docs/project_architecture.png?raw=true "Project Architecture Overview"
[app_architecture]: https://github.com/mdivband/hut/blob/master/controller/docs/app_architecture.png?raw=true "App Architecture Overview"
[app_flow]: https://github.com/mdivband/hut/blob/master/controller/docs/app_flow.png?raw=true "App Flow"


## DJI Hut Server - Documentation
##### Last Updated: 13/09/2018, Joe Early
------
The DJI Hut Server is a client-server application that is used for coordinating a fleet of drones. The organisation and planning is handled by a browser application, and the commands are relayed through to the drones through a mobile aplication (see the [Android app repository][35]). 

### Table of Contents

1. [Repository Structure](#repository-structure-1)
2. [Project Architecture](#project-architecture-1)
3. [Setup Guide](#setup-guide-1)
4. [Setup Troubleshooting](#setup-troubleshooting)
5. [Server Architecture](#server-architecture)
5. [Allocation Process](#allocation-process)

### Repository Structure

The repository has two main directories: [src][36] is for the server code and [web][37] is for the client (browser) code. 

Within the src directory, there are three packages: [maxsum][38], [server][39] and [tool][40]. The maxsum package contains the code to execute the maxsum algorithm that is used to assign agents to tasks. The server package contains the server code, which is further split into controller and model packages. Finally, the tool package contains a utility class for JSON serialisation using [GSON][41], as well as [Java Lightweight HTTP Server][42] class that actually handles the web server's HTTP connections (meaning Apache or equivalent is not required). For a more in-dept overview of the code, see the file tree documentation [here][67].

### Project Architecture

The overall project architecture is spread across two repostiories: this one and the [mobile app repository][35]. The mobile application is responsible for communicating with the drone controller and drone itself, however all the planning and coordinate of the drones is done by the server.

The mobile application communicates with the server to relay information about the drone (such as position), and also to receive commands regarding the drone's current mission. The mobile application initially registers itself with the server using the server's REST API. After establishing a connection, the server configures a channel on its [RabbitMQ][43] server (running [here][66])for this particular drone and informs the application of this newly opened channel. All communication between the application and the server then proceeds via RabbitMQ rather than the REST API.

The client application that runs in the browser communicates with the server via the server's REST API. The broswer application allows a user to specifiy custom allocations of drones to missions, as well as see the position of all the drones at once. An overview of the project architecture is shown below:

![Project Architecture Overview][project_architecture]

### Setup Guide

There are several steps required to setup a working version of the mobile application and server:  

1. Clone this repository to your machine.  
2. Setup a project in Intellij (recommended) or Eclipse from the repository code.  
3. Go to Settings > Project Structure, in Project add a SDK and in Module > Add New Module and select the server folder in the hut project as a java module.
3. Run the [Simulator][48] class, and open http://localhost:8000/ in a browser to connect to the client application.  
4. (Optional) Configuration for connecting drones via the mobile app:  
    * Configure a [VPN via Global Protect][45] if running the server locally on the University network.  
    * Find the IP of the server (check the Global Protect settings if using the VPN).  
    * Check that port 8000 is open on the server machine.  
    * Follow the mobile app setup guide, found [here][35].  

### Setup Troubleshooting

| Error      | Solution |
| ----------- | ----------- |
| `Calling invokeAndWait from read-action leads to possible deadlock`      | Select *Run*, *Edit Configurations...*, then change the *JDK or JRE* version to whichever version says "*SDK of 'hut-server' module*".       |

### Server Architecture

The server application has a model component and a controller componment, as represented by the package structure. Parts of the model are exposed through the server's REST API, with connections to the client broswer application and mobile application handled by the [ConnectionController][49] and the [QueueManager][50] respectively. The ConnectionController uses different handlers for processing REST requests: [AgentHandler][62], [TaskHandler][63], [TargetHandler][64] and [RootHandler][65]. The first three handlers correspond with equivalent controller classes ([AgentController][51], [TargetController][52], [TaskController][53]), while the RootHandler is responsible for all other endpoints that don't fall under the first three categories. The REST endpoints exposed by the server are summarised [here][61].

The model component of the server is fully encapsulated in the [State][54] class - this is the central class that references the other models classes such as [Agent][55], [Target][57] and [Task][58]. The single state instance is created by the [Simulator][48] class (the entry point for the server application), and updated & maintained by the various controller classes, with each controller class reponsbile for a different component of the state.

In addition to the model and controller components, the server is also reponsbile for the allocation of agents to tasks and creating a schedule for performing the tasks given by the user (via the browser application). The allocation is handled by the [Allocator][59] class, which uses the algorithm contained in the [maxsum package][60] to actually compute the allocation. The architecture is designed so that the allocation algorithm is not deeply integrated into the code - it can easily be substitued for an alternative planning algorithm if required.

An overview of the server architecture is given below:

![Server Architecture][server_architecture]

### Allocation Process

The allocation process is the mechanism for assigning agents to tasks. An allocation is simply a mapping from agents to tasks - an agent can only be assigned to one task, but multiple agents can be assigned to same task. The server maintains two allocations - one that is the current, main allocation and another that is a temporary, work-in-progress allocation. When the user changes the assignment of agents to tasks, they are altering the temporary allocation. Once they confirm their changes, the main allocation is updated to match the temporary allocation. 

The browser app operates in two modes: monitor and edit. In monitor mode, the user can see an overview of the current allocation and the progress of the agents on their various tasks. In edit mode, the user can edit the allocation (which alters the temporary allocation, see above), as well as add new tasks and move existing tasks. There are two methods for assigning agents to tasks. The first uses an automated allocation algorithm (currently Maxsum) to assign each agent to a task. This is triggered by pressing the 'Run Auto Allocation' button in edit mode. The second method is used to assign a single agent to a specific task: a user can drag an allocation arrow from an agent to a task to assign the agent to that task. Once the user presses 'Confirm Allocation', the temporary allocation becomes the main allocation and the agents begin carrying out their assigned tasks. 

An overview of the allocation process is given below:

![Allocation Process][allocation_process]

[35]: /controller/app/
[36]: /server/src/
[37]: /server/web/
[38]: /server/src/maxsum
[39]: /server/src/server
[40]: /server/src/tool
[41]: https://en.wikipedia.org/wiki/Gson
[42]: https://www.freeutils.net/source/jlhttp/
[43]: http://www.rabbitmq.com/
[44]: http://www.rabbitmq.com/install-windows.html
[45]: https://www.southampton.ac.uk/ageing/postgraduate/welcome/vpn.page
[46]: /server/src/server/QueueManager.java
[48]: /server/src/server/Simulator.java
[49]: /server/src/server/controller/ConnectionController.java
[50]: /server/src/server/QueueManager.java
[51]: /server/src/server/controller/AgentController.java
[52]: /server/src/server/controller/TargetController.java
[53]: /server/src/server/controller/TaskController.java
[54]: /server/src/server/model/State.java
[55]: /server/src/server/model/Agent.java
[57]: /server/src/server/model/target/Target.java
[58]: /server/src/server/model/task/Task.java
[59]: /server/src/server/Allocator.java
[60]: /server/src/maxsum
[61]: /server/docs/endpoints.md
[62]: /server/src/server/controller/handler/AgentHandler.java
[63]: /server/src/server/controller/handler/TaskHandler.java
[64]: /server/src/server/controller/handler/TargetHandler.java
[65]: /server/src/server/controller/handler/RootHandler.java
[66]: https://www.cloudamqp.com/
[67]: /server/docs/file_tree.md

[project_architecture]: https://github.com/mdivband/hut/blob/master/controller/docs/project_architecture.png?raw=true "Project Architecture Overview"
[server_architecture]: https://github.com/mdivband/hut/blob/master/server/docs/img/server_architecture.png?raw=true "Server Architecture"
[allocation_process]: https://github.com/mdivband/hut/blob/5d4d57ffc541bedcd78db6688a4fca21a44edecb/server/docs/img/allocation_flow.png?raw=true "Allocation Process"