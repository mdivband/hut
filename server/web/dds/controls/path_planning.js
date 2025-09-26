$(document).ready(function() {
    console.log('DDS Path Planning with Agent Tracking initialized...');
    
    // Global variables
    let map = null;
    let agentMarkers = {};
    let waypointLines = {};
    let agentData = {};
    
    // Initialize Google Map
    function initializeMap() {
        if (!document.getElementById('map_canvas') || !window.google || !window.google.maps) {
            console.error('Map canvas not found or Google Maps not available');
            return null;
        }
        
        console.log('Initializing Google Map...');
        
        const mapOptions = {
            zoom: 15,
            center: new google.maps.LatLng(30.65582, -96.42533), // Texas center
            mapTypeId: google.maps.MapTypeId.TERRAIN,
            disableDefaultUI: false,
            zoomControl: true,
            mapTypeControl: true,
            streetViewControl: false,
            scaleControl: true
        };
        
        map = new google.maps.Map(document.getElementById('map_canvas'), mapOptions);
        
        // Store map reference globally
        window.ddsMap = map;
        
        console.log('Map initialized successfully');
        return map;
    }
    
    // Create or update agent marker
    function updateAgentMarker(agentId, lat, lng, heading = 0) {
        if (!map) return;
        
        const position = new google.maps.LatLng(lat, lng);
        
        if (agentMarkers[agentId]) {
            // Update existing marker
            agentMarkers[agentId].setPosition(position);
        } else {
            // Create new marker
            const marker = new google.maps.Marker({
                position: position,
                map: map,
                title: agentId,
                icon: {
                    path: google.maps.SymbolPath.FORWARD_CLOSED_ARROW,
                    scale: 6,
                    fillColor: getAgentColor(agentId),
                    fillOpacity: 0.8,
                    strokeColor: '#000',
                    strokeWeight: 1,
                    rotation: heading
                }
            });
            
            // Add info window
            const infoWindow = new google.maps.InfoWindow({
                content: `<div><strong>${agentId}</strong><br/>Lat: ${lat.toFixed(6)}<br/>Lng: ${lng.toFixed(6)}<br/>Heading: ${heading.toFixed(1)}°</div>`
            });
            
            marker.addListener('click', function() {
                infoWindow.open(map, marker);
            });
            
            agentMarkers[agentId] = marker;
            console.log(`Created marker for agent ${agentId} at (${lat}, ${lng})`);
        }
    }
    
    // Get color for agent based on ID
    function getAgentColor(agentId) {
        const colors = {
            'STA': '#FF0000',  // Red for STA agents
            'FSA': '#0000FF',  // Blue for FSA agents
            'UAV': '#00FF00',  // Green for UAV agents
            'default': '#FF8C00' // Orange for others
        };
        
        const agentType = agentId.split('-')[0] || 'default';
        return colors[agentType] || colors['default'];
    }
    
    // Draw waypoint lines for an agent
    function drawWaypointLines(agentId, waypoints, currentPosition) {
        if (!map) return;
        
        // Remove existing line
        if (waypointLines[agentId]) {
            waypointLines[agentId].setMap(null);
            delete waypointLines[agentId];
        }
        
        if (!waypoints || Object.keys(waypoints).length === 0) {
            return;
        }
        
        // Build path coordinates
        const pathCoordinates = [];
        
        // Add current position if available
        if (currentPosition) {
            pathCoordinates.push(new google.maps.LatLng(currentPosition.lat, currentPosition.lng));
        }
        
        // Add waypoints in order
        const sortedIndices = Object.keys(waypoints).sort((a, b) => parseInt(a) - parseInt(b));
        sortedIndices.forEach(index => {
            const waypoint = waypoints[index];
            if (typeof waypoint === 'string' && waypoint.includes(',')) {
                const coords = waypoint.split(',');
                if (coords.length >= 2) {
                    const lat = parseFloat(coords[0]);
                    const lng = parseFloat(coords[1]);
                    pathCoordinates.push(new google.maps.LatLng(lat, lng));
                }
            }
        });
        
        if (pathCoordinates.length < 2) {
            return;
        }
        
        // Create polyline
        const waypointLine = new google.maps.Polyline({
            path: pathCoordinates,
            geodesic: true,
            strokeColor: getAgentColor(agentId),
            strokeOpacity: 0.8,
            strokeWeight: 3,
            map: map
        });
        
        waypointLines[agentId] = waypointLine;
        
        // Add waypoint markers
        sortedIndices.forEach((index, i) => {
            const waypoint = waypoints[index];
            if (typeof waypoint === 'string' && waypoint.includes(',')) {
                const coords = waypoint.split(',');
                if (coords.length >= 2) {
                    const lat = parseFloat(coords[0]);
                    const lng = parseFloat(coords[1]);
                    
                    new google.maps.Marker({
                        position: new google.maps.LatLng(lat, lng),
                        map: map,
                        title: `${agentId} Waypoint ${index}`,
                        icon: {
                            path: google.maps.SymbolPath.CIRCLE,
                            scale: 4,
                            fillColor: getAgentColor(agentId),
                            fillOpacity: 0.6,
                            strokeColor: '#FFF',
                            strokeWeight: 2
                        }
                    });
                }
            }
        });
        
        console.log(`Drew waypoint line for ${agentId} with ${pathCoordinates.length} points`);
    }
    
    // Fetch and display waypoints
    function fetchWaypoints() {
        $.ajax({
            url: '/idds/waypoints',
            method: 'GET',
            dataType: 'json',
            success: function(data) {
                console.log('=== AGENT WAYPOINTS ===');
                console.log('Raw waypoints data:', data);
                
                // Check if data is empty
                if (Object.keys(data).length === 0) {
                    console.log('No agents with waypoints found');
                    return;
                }
                
                // Process each agent
                Object.keys(data).forEach(function(agentId) {
                    const agentWaypoints = data[agentId];
                    console.log(`\n--- Agent: ${agentId} ---`);
                    
                    // Check if agent has any waypoints
                    const waypointCount = Object.keys(agentWaypoints).length;
                    if (waypointCount === 0) {
                        console.log(`  No waypoints for ${agentId}`);
                        return;
                    }
                    
                    console.log(`  Waypoint count: ${waypointCount}`);
                    
                    // Display each waypoint
                    Object.keys(agentWaypoints).forEach(function(index) {
                        const waypoint = agentWaypoints[index];
                        if (typeof waypoint === 'string' && waypoint.includes(',')) {
                            const coords = waypoint.split(',');
                            if (coords.length >= 2) {
                                const lat = parseFloat(coords[0]);
                                const lng = parseFloat(coords[1]);
                                console.log(`  Waypoint ${index}: [${lat.toFixed(6)}, ${lng.toFixed(6)}]`);
                            }
                        } else {
                            console.log(`  Waypoint ${index}: ${waypoint}`);
                        }
                    });
                    
                    // Draw waypoint lines on map
                    const currentPos = agentData[agentId] ? agentData[agentId].position : null;
                    drawWaypointLines(agentId, agentWaypoints, currentPos);
                });
                
                console.log('=== END WAYPOINTS ===\n');
            },
            error: function(xhr, status, error) {
                console.error('Failed to fetch waypoints:', error);
                console.error('Status:', status);
                console.error('Response:', xhr.responseText);
            }
        });
    }
    
    // Fetch latest DDS data for agent positions
    function fetchDDSData() {
        $.ajax({
            url: '/idds/latest',
            method: 'GET',
            dataType: 'json',
            success: function(response) {
                // Parse the DDS message to extract agent positions
                if (response && response.message) {
                    try {
                        const data = JSON.parse(response.message);
                        if (data.agents && Array.isArray(data.agents)) {
                            data.agents.forEach(function(agent) {
                                const agentId = agent.agent_id;
                                const lat = agent.coordinate ? agent.coordinate.lat : 0;
                                const lng = agent.coordinate ? agent.coordinate.lng : 0;
                                const heading = agent.heading || 0;
                                
                                // Store agent data
                                agentData[agentId] = {
                                    position: { lat: lat, lng: lng },
                                    heading: heading,
                                    speed: agent.speed || 0,
                                    altitude: agent.altitude || 0
                                };
                                
                                // Update agent marker on map
                                updateAgentMarker(agentId, lat, lng, heading);
                            });
                            
                            console.log(`Updated ${data.agents.length} agent positions`);
                        }
                    } catch (e) {
                        console.log('No valid DDS data to parse:', e.message);
                    }
                }
            },
            error: function(xhr, status, error) {
                console.log('DDS data fetch failed - this is normal if no data is available');
            }
        });
    }
    
    // Function to periodically fetch data
    function startDataPolling(intervalSeconds = 2) {
        console.log(`Starting data polling every ${intervalSeconds} seconds`);
        
        // Fetch immediately
        fetchDDSData();
        fetchWaypoints();
        
        // Set up periodic fetching
        setInterval(function() {
            fetchDDSData();
        }, intervalSeconds * 1000);
        
        // Fetch waypoints less frequently
        setInterval(function() {
            fetchWaypoints();
        }, (intervalSeconds * 2) * 1000);
    }
    
    // Initialize the application
    function initializeDDSApp() {
        console.log('=== DDS Path Planning with Agent Tracking ===');
        console.log('Features: Agent positions, waypoint lines, real-time updates');
        
        // Initialize map
        initializeMap();
        
        if (!map) {
            console.error('Failed to initialize map - continuing with console-only mode');
        }
        
        // Start data polling
        startDataPolling(2);
        
        // Make body visible
        if (document.body) {
            document.body.style.visibility = 'visible';
            document.body.classList.add('ready');
        }
        
        console.log('DDS Application initialized successfully');
    }
    
    // Global functions for manual control
    window.refreshWaypoints = function() {
        console.log('Manual waypoint refresh triggered');
        fetchWaypoints();
    };
    
    window.refreshAgents = function() {
        console.log('Manual agent refresh triggered');
        fetchDDSData();
    };
    
    window.clearMap = function() {
        console.log('Clearing map data');
        // Clear markers
        Object.values(agentMarkers).forEach(marker => marker.setMap(null));
        agentMarkers = {};
        
        // Clear lines
        Object.values(waypointLines).forEach(line => line.setMap(null));
        waypointLines = {};
        
        // Clear data
        agentData = {};
        
        console.log('Map cleared');
    };
    
    // Start the application
    initializeDDSApp();
    
    console.log('=== Available Commands ===');
    console.log('refreshWaypoints() - Manually fetch waypoints');
    console.log('refreshAgents() - Manually fetch agent positions');
    console.log('clearMap() - Clear all map data');
    console.log('window.ddsMap - Access map instance');
});