## DJI Hut Server - Documentation
### University of Southampton
##### Last Updated: 04/11/21, William Hunt
------
The DJI Hut Server is a client-server application that is used for coordinating a fleet of drones. The organisation and planning is handled by a browser application, and the commands are relayed through to the drones through a mobile aplication (see the [Android app repository][1]).

### Table of Contents

1. [Running and Using](#running-and-using)
2. [Repository Structure](#repository-structure)
3. [Project Architecture](#project-architecture)
4. [Setup Guide](#setup-guide)
5. [Setup Troubleshooting](#setup-troubleshooting)
6. [Server Architecture](#server-architecture)
7. [Allocation Process](#allocation-process)

### Running and Using

#### Running Locally

1. Clone this repository to your machine.
2. Setup a project in Intellij (recommended) or Eclipse from the repository code.
3. Run the Simulator class, and open http://localhost:8000/ in a browser to connect to the client application.

#### Hosting the Server Online

1. Clone this repository to your machine.
2. Setup a project in Intellij (recommended) or Eclipse from the repository code.
3. Run the Simulator class with the desired port as an argument
4. Connect from a different PC with IP:Port in your browser

#### Alternative: Run the .jar from command line

1. Take the hut_server.jar file, and the web/ folder into a directory together (or as they are in the repo)
2. Run the .jar with the following command: ```java -jar hut_server.jar [PORT]```
3. Connect from a different PC with IP:Port in your browser

### Repository Structure

The repository has two main directories: [src][2] is for the server code and [web][3] is for the client (browser) code. 

Within the src directory, there are three packages: [maxsum][4], [server][5] and [tool][6]. The maxsum package contains the code to execute the maxsum algorithm that is used to assign agents to tasks. The server package contains the server code, which is further split into controller and model packages. Finally, the tool package contains a utility class for JSON serialisation using [GSON][7], as well as [Java Lightweight HTTP Server][8] class that actually handles the web server's HTTP connections (meaning Apache or equivalent is not required). For a more in-dept overview of the code, see the file tree documentation [here][34].

### Project Architecture

The overall project architecture is spread across two repostiories: this one and the [mobile app repository][1]. The mobile application is responsible for communicating with the drone controller and drone itself, however all the planning and coordinate of the drones is done by the server.

The mobile application communicates with the server to relay information about the drone (such as position), and also to receive commands regarding the drone's current mission. The mobile application initially registers itself with the server using the server's REST API. After establishing a connection, the server configures a channel on its [RabbitMQ][9] server (running [here][33])for this particular drone and informs the application of this newly opened channel. All communication between the application and the server then proceeds via RabbitMQ rather than the REST API.

The client application that runs in the browser communicates with the server via the server's REST API. The broswer application allows a user to specifiy custom allocations of drones to missions, as well as see the position of all the drones at once. An overview of the project architecture is shown below:

![Project Architecture Overview][project_architecture]

### Setup Guide

There are several steps required to setup a working version of the mobile application and server:  

1. Clone this repository to your machine.  
2. Setup a project in Intellij (recommended) or Eclipse from the repository code.  
3. Run the [Simulator][14] class, and open http://localhost:8000/ in a browswer to connect to the client application.  
4. (Optional) Configuration for connecting drones via the mobile app:  
    * Configure a [VPN via Global Protect][11] if running the server locally on the University network.  
    * Find the IP of the server (check the Global Protect settings if using the VPN).  
    * Check that port 8000 is open on the server machine.  
    * Follow the mobile app setup guide, found [here][1].  

### Setup Troubleshooting

| Error      | Solution |
| ----------- | ----------- |
| `Calling invokeAndWait from read-action leads to possible deadlock`      | Select *Run*, *Edit Configurations...*, then change the *JDK or JRE* version to whichever version says "*SDK of 'hut-server' module*".       |

### Server Architecture

The server application has a model component and a controller componment, as represented by the package structure. Parts of the model are exposed through the server's REST API, with connections to the client broswer application and mobile application handled by the [ConnectionController][15] and the [QueueManager][16] respectively. The ConnectionController uses different handlers for processing REST requests: [AgentHandler][29], [TaskHandler][30], [TargetHandler][31] and [RootHandler][32]. The first three handlers correspond with equivalent controller classes ([AgentController][17], [TargetController][18], [TaskController][19]), while the RootHandler is responsible for all other endpoints that don't fall under the first three categories. The REST endpoints exposed by the server are summarised [here][28].

The model component of the server is fully encapsulated in the [State][21] class - this is the central class that references the other models classes such as [Agent][22], [Schedule][23], [Target][24] and [Task][25]. The single state instance is created by the [Simulator][14] class (the entry point for the server application), and updated & maintained by the various controller classes, with each controller class reponsbile for a different component of the state.

In addition to the model and controller components, the server is also reponsbile for the allocation of agents to tasks and creating a schedule for performing the tasks given by the user (via the browser application). The allocation is handled by the [Allocator][26] class, which uses the algorithm contained in the [maxsum package][27] to actually compute the allocation. The architecture is designed so that the allocation algorithm is not deeply integrated into the code - it can easily be substitued for an alternative planning algorithm if required.

An overview of the server architecture is given below:

![Server Architecture][server_architecture]

### Allocation Process

The allocation process is the mechanism for assigning agents to tasks. An allocation is simply a mapping from agents to tasks - an agent can only be assigned to one task, but multiple agents can be assigned to same task. The server maintains two allocations - one that is the current, main allocation and another that is a temporary, work-in-progress allocation. When the user changes the assignment of agents to tasks, they are altering the temporary allocation. Once they confirm their changes, the main allocation is updated to match the temporary allocation. 

The browser app operates in two modes: monitor and edit. In monitor mode, the user can see an overview of the current allocation and the progress of the agents on their various tasks. In edit mode, the user can edit the allocation (which alters the temporary allocation, see above), as well as add new tasks and move existing tasks. There are two methods for assigning agents to tasks. The first uses an automated allocation algorithm (currently Maxsum) to assign each agent to a task. This is triggered by pressing the 'Run Auto Allocation' button in edit mode. The second method is used to assign a single agent to a specific task: a user can drag an allocation arrow from an agent to a task to assign the agent to that task. Once the user presses 'Confirm Allocation', the temporary allocation becomes the main allocation and the agents begin carrying out their assigned tasks. 

An overview of the allocation process is given below:

![Server Architecture][allocation_process]

[1]: https://bitbucket.org/jearly97/hut_dji_controller
[2]: ../src
[3]: ../web
[4]: ../src/maxsum
[5]: ../src/server
[6]: ../src/tool
[7]: https://en.wikipedia.org/wiki/Gson
[8]: https://www.freeutils.net/source/jlhttp/
[9]: http://www.rabbitmq.com/
[10]: http://www.rabbitmq.com/install-windows.html
[11]: https://www.southampton.ac.uk/ageing/postgraduate/welcome/vpn.page
[12]: ../src/server/QueueManager.java
[13]: https://bitbucket.org/jearly97/hut_dji_controller/src/master/docs/documentation.md#markdown-header-setup-guide
[14]: ../src/server/Simulator.java
[15]: ../src/server/controller/ConnectionController.java
[16]: ../src/server/QueueManager.java
[17]: ../src/server/controller/AgentController.java
[18]: ../src/server/controller/TargetController.java
[19]: ../src/server/controller/TaskController.java
[21]: ../src/server/model/State.java
[22]: ../src/server/model/Agent.java
[23]: ../src/server/model/Schedule.java
[24]: ../src/server/model/Target.java
[25]: ../src/server/model/Task.java
[26]: ../src/server/Allocator.java
[27]: ../src/maxsum
[28]: ./endpoints.md
[29]: ../src/server/controller/handler/AgentHandler.java
[30]: ../src/server/controller/handler/TaskHandler.java
[31]: ../src/server/controller/handler/TargetHandler.java
[32]: ../src/server/controller/handler/RootHandler.java
[33]: https://www.cloudamqp.com/
[34]: ./file_tree.md

[project_architecture]: https://bitbucket.org/jearly97/hut_dji_controller/raw/master/docs/project_architecture.png "Project Architecture Overview"
[server_architecture]: https://bitbucket.org/dhaminda/hutserver/raw/master/docs/img/server_architecture.png "Server Architecture"
[allocation_process]: https://bitbucket.org/dhaminda/hutserver/raw/master/docs/img/allocation_flow.png "Allocation Process"
