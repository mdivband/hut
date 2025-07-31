# Supported Topics

| **Topic Key**                          | **Description**                                  | **Message Type**                                      |
| -------------------------------------- | ------------------------------------------------ | ----------------------------------------------------- |
| aircraft/[TYPE]/[ID]/set_next_waypoint | Set the next waypoint for the UAV to navigate to | [`aircraft_waypoint.fbs`](msgs/aircraft_waypoint.fbs) |
| aircraft/[TYPE]/[ID]/set_takeoff       | Set the UAV to takeoff                           | [`aircraft_waypoint.fbs`](msgs/aircraft_waypoint.fbs) |
| aircraft/[TYPE]/[ID]/set_landing       | Set the UAV to land                              | [`aircraft_waypoint.fbs`](msgs/aircraft_waypoint.fbs) |
| aircraft/[TYPE]/[ID]/position          | Position information of the UAV                  | [`asset_position.fbs`](msgs/asset_position.fbs)       |
| aircraft/[TYPE]/[ID]/velocity          | Velocity information of the UAV                  | [`asset_velocity.fbs`](msgs/asset_velocity.fbs)       |
| aircraft/[TYPE]/[ID]/heading           | Heading information of the UAV                   | [`asset_heading.fbs`](msgs/asset_heading.fbs)         |
