# DJI Hut Server - Scenario Files
### University of Southampton
##### Last Updated: 13/09/2018, Joe Early
------

Scenarios can be loaded from a JSON file. The application looks for scenario files in the [scenarios folder][1]. The following document outlines the format of those files. Fields are the base keys in the JSON file (list in the [Fields](#markdown-header-fields) section); some of them are composed of objects which are outlined in the [Objects](#markdown-header-objects) section.

### Fields

|Field|Description|Required Parameters|Optional Parameters|
|:---:|:---:|:---:|:---:|
|gameId|The name of the scenario, doesn't have to match the file name but should be unique.|-|-|
|gameDescription|The text that is showed to the user before they begin the scenario. Should give a clear overview of the objectives of the task.|-|-|
|gameCentre|The position at the centre of the map at the beginning of the scenario.|lat, lng|-
|agents|A JSON array composed of the agent objects available in the scenario.|-|-|
|hazards|A JSON array composed of the hazard objects present in the scenario.|-|-|
|targets|A JSON array composed of the target objects present in the scenario.|-|-|

### Objects

|Object|Description|Required Parameters|Optional Parameters|
|:---:|:---:|:---:|:---:|
|agent|An agent (UAV) that the user can use to complete the scenario.|lat, lng (starting position)|battery (starting battery life)|
|hazard|A hazard that is present throughout the scenario.|lat, lng (position)|type (type of hazard - fire etc.)|
|target|A target that the user has to find during the scenario.|lat, lng (position)|type (target of target - human etc.)|

### Example
```
{
  "gameId": "Test Scenario",
  "gameDescription": "A large fire has been reported in the North of the Southampton Common.\nYou have been provided with three UAVs and must assist emergency services by locating the fire and pinpointing the location of people in the vicinity.",
  "gameCentre": {
    "lat": 50.929378522204615,
    "lng": -1.4080147702592285
  },
  "agents": [
    {
      "lat": 50.92880002299894,
      "lng": -1.4094788872327502
    },
    {
      "lat": 50.929378522204615,
      "lng": -1.4078189690011413,
      "battery": 0.5
    },
    {
      "lat": 50.93008636656946,
      "lng": -1.4065928983124234,
      "battery": 0.75
    }
  ],
  "hazards": [
    {
      "lat": 50.93229829426595,
      "lng": -1.4090726010153958,
      "type": 0
    }
  ],
  "targets": [
    {
      "lat": 50.92993483453923,
      "lng": -1.4083502511379038,
      "type": 0
    },
    {
      "lat": 50.92918290193244,
      "lng": -1.4116303880113037,
      "type": 0
    },
    {
      "lat": 50.93029188552423,
      "lng": -1.4125745255845459,
      "type": 0
    },
    {
      "lat": 50.93117093909728,
      "lng": -1.4113943536179931,
      "type": 0
    },
    {
      "lat": 50.93224606665045,
      "lng": -1.4123277623551758,
      "type": 0
    },
    {
      "lat": 50.93334145406898,
      "lng": -1.4108793694871338,
      "type": 0
    },
    {
      "lat": 50.93258415193955,
      "lng": -1.4068131406205566,
      "type": 0
    },
    {
      "lat": 50.93359839306123,
      "lng": -1.4084975678819092,
      "type": 0
    }
  ]
}
```
[1]: ../web/scenarios