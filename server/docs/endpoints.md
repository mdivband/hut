# DJI Hut Server - REST API
### University of Southampton
##### Last Updated: 13/09/2018, Joe Early
------
This document details all of the REST API endpoints that are exposed by the DJI Hut Server. In addition to the error responses listed for each endpoint, all endpoints throw a 400 error if a request is missing any non-optional parameters. Optional parameters are denoted as [param], as opposed to non-optional parameters which aren't in square brackets. URL variables are denoted using <var>, for example <id> indicates a variable string in the URL that represents the id of an object. 

### Endpoint list

[/agents](#markdown-header-agents)  
[/tasks](#markdown-header-tasks)  
[/targets](#markdown-header-targets)  
[/allocation](#markdown-header-allocation)  
[/mode](#markdown-header-mode)  
[/visualizer](#markdown-header-visualizer)  
[Root](#markdown-header-root)  
[Unused](#markdown-header-unused)  

### Endpoint descriptions

#### /agents

Used for creating and deleting agents.

[Back to List](#markdown-header-endpoint-list)

|Method|URL|Params|Description|Success Response|Error Response|
|:---:|:---:|:---:|:---:|:---:|:---:|
|POST|/agents|lat, lng, heading|Create a new virtual agent at the location lat, lng, with the given heading.|201|-|
|POST|/agents/time-out/<id>|timedOut|Set the timed out state of an agent (virtual agents only). |200|**400** - Unable to set (likely the agent is not virtual). **404** - No agent found for id.|
|POST|/route/add/<id>|index, lat, lng|Add a new point (lat, lng) to an agent's route at the given index.|200|-|
|POST|/route/edit/<id>|index, lat, lng|Edit a point (lat, lng) in an agent's route at the given index.|200|-|
|DELETE|/agents/route/<id>|index|Delete the point at the given index from an agent's route.|200|-|
|DELETE|/agents/<id>|-|Delete an existing virtual agent with given id.|200|**400** - Unable to delete (generic). **404** - No agent found for id.|

#### /tasks

Used for creating, updating and deleting tasks.

[Back to List](#markdown-header-endpoint-list)

|Method|URL|Params|Description|Success Response|Error Response|
|:---:|:---:|:---:|:---:|:---:|:---:|
|POST|/tasks|type, lat, lng|Create a new task of the given type at the location lat, lng.|201|-|
|POST|/tasks/patrol|path|Create a new patrol task with the given path.|201|-|
|POST|/tasks/patrol/update/<id>|path|Set the path of the given task.|201|**400** - Not a patrol task.|
|POST|/tasks/region|corners|Create a new region task with the given corners.|201|-|
|POST|/tasks/region/update/<id>|corners|Set the corners of the given task.|201|**400** - Not a region task.|
|POST|/tasks/<id>|[lat], [lng], [group], [priority]|Update one or more of the task's attributes.|200|**404** - No task found for id.|
|DELETE|/tasks/<id>|-|Delete an existing task with given id.|200|**400** - Unable to delete (generic). **404** - No task found for id.|

#### /targets

Used for creating, updating and deleting targets.

[Back to List](#markdown-header-endpoint-list)

|Method|URL|Params|Description|Success Response|Error Response|
|:---:|:---:|:---:|:---:|:---:|:---:|
|POST|/targets|lat, lng, type|Create a new target of the given type at the location lat, lng.|201|-|
|POST|/targets/reveal/<id>|-|Reveal the given target on the map.|200|-|
|DELETE|/targets/<id>|-|Delete an existing target with given id.|200|**400** - Unable to delete (generic). **404** - No task found for id.|

#### /allocation

Used for allocating agents to tasks.

[Back to List](#markdown-header-endpoint-list)

|Method|URL|Params|Description|Success Response|Error Response|
|:---:|:---:|:---:|:---:|:---:|:---:|
|POST|/allocation/auto-allocate|-|Run the auto-allocation algorithm. The result is then stored in the temporary allocation.|200|-|
|POST|/allocation/confirm|-|Update the main allocation to the temporary allocation.|200|-|
|POST|/allocation/allocate|agentId, taskId|Put an allocation into the temporary allocation.|200|**404** - Agent or task not found for given ids.|
|POST|/allocation/undo|-|Undo a change to the temporary allocation.|200|-|
|POST|/allocation/redo|-|Redo a change to the temporary allocation.|200|-|
|POST|/allocation/reset|-|Reset the temporary allocation so it matches the real allocation.|200|-|
|DELETE|/allocation/<id>|-|Remove the allocation for an agent.|200|**404** - No agent found for id.|

#### /mode

Used for starting different modes of the application.

[Back to List](#markdown-header-endpoint-list)

|Method|URL|Params|Description|Success Response|Error Response|
|:---:|:---:|:---:|:---:|:---:|:---:|
|POST|/mode/sandbox|-|Start sandbox mode.|200|-|
|POST|/mode/scenario|file-name|Load a scenario from a file (file-name should point to file in scenarios folder).|200|**400** - Unable to start scenario from given file name.|
|POST|/mode/scenario/start|-|Start a scenario after loading it.|200|-|
|GET|/mode/scenario-list|-|Get a list of the available scenarios. Returns a JSON array of the form {fileName, gameId}.|200|-|
|GET|/mode/in-progress|-|Get a boolean that indicates if an operation is in progress.|200|-|

#### /visualizer

Used by the visualizer extension to get the relevant state information from the server.

[Back to List](#markdown-header-endpoint-list)

|Method|URL|Params|Description|Success Response|Error Response|
|:---:|:---:|:---:|:---:|:---:|:---:|
|GET|/visualizer|-|Get the agent and task states as the JSON string.|200|-|

#### Root

Any endpoints that are not grouped into the above categories are process in the RootHandler; they are all of the form /command.

[Back to List](#markdown-header-endpoint-list)

|Method|URL|Params|Description|Success Response|Error Response|
|:---:|:---:|:---:|:---:|:---:|:---:|
|POST|/provdoc|id| Set the id of the prov doc.|200|-|
|POST|/changeview|edit|Change the view in or out of edit mode.|200|-|
|POST|/reset|-|Reset the server state.|200|-|
|POST|/register|lat, lon|Register a new *real* agent.|200|-|
|GET|/state.json|-|Get the server state.|200|-|

#### Unused

The following endpoints are available and used by the client web-app, however corresponding functionality in the web-app cannot be accessed (i.e. stuff is hidden).

[Back to List](#markdown-header-endpoint-list)

|Method|URL|Params|Description|Success Response|Error Response|
|:---:|:---:|:---:|:---:|:---:|:---:|
|POST|/agents/<id>|[speed], [altitude]|Update one or more of the agent's attributes.|200|**404** - No agent found for id.|
|POST|/teleop|linear, angular, id|Move an agent with given id.|200|**404** - No agent found for id.|
|POST|/ardrone_pos|id, x, y, a|Move an agent with given id.|200|**404** - No agent found for id.|
|POST|/configjson|agent|Save current state as initial configuration based on serialized JSON agent information.|200|-|