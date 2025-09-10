// Mission Parameter View Controller
class MissionView {
    constructor() {
        this.initializeEventListeners();
        this.updateStatus();
    }

    initializeEventListeners() {
        const ids = [
            { id: 'deploySTABtn', handler: this.deploySTA.bind(this) },
            { id: 'deployFSABtn', handler: this.deployFSA.bind(this) },
            { id: 'deployAllBtn', handler: this.deployAll.bind(this) },
            { id: 'generateReportBtn', handler: this.generateMissionReport.bind(this) },
            { id: 'abortMissionBtn', handler: this.abortMission.bind(this) }
        ];
        ids.forEach(({ id, handler }) => {
            const el = document.getElementById(id);
            if (el) {
                el.addEventListener('click', handler);
            }
        });
    }

    updateStatus() {
        // Update position
        document.getElementById('positionValue').textContent = "Loading...";
        
        // Update operator(s)
        document.getElementById('operatorValue').textContent = "Loading...";
        
        // Update STA status
        document.getElementById('staActive').textContent = "0";
        document.getElementById('staInactive').textContent = "0";
        document.getElementById('staReady').textContent = "0";
        
        // Update FSA status
        document.getElementById('fsaActive').textContent = "0";
        document.getElementById('fsaInactive').textContent = "0";
        document.getElementById('fsaReady').textContent = "0";
    }

    deploySTA() {
        console.log('Deploy STA clicked');
        // Implement STA deployment
    }

    deployFSA() {
        console.log('Deploy FSA clicked');
        // Implement FSA deployment
    }

    deployAll() {
        console.log('Deploy All clicked');
        // Implement deploy all functionality
    }

    generateMissionReport() {
        console.log('Generate Mission Report clicked');
        // Implement report generation
    }

    abortMission() {
        if (confirm('Are you sure you want to abort the mission?')) {
            console.log('Mission aborted');
            // Implement mission abort
        }
    }
}

async function fetchHubStatus() {
    try {
        const response = await fetch('/idds/hubstatus');
        if (response.ok) {
            const data = await response.json();
            // Example expected data:
            // {
            //   "location": "(37.7749, -122.4194)",
            //   "operators": 2,
            //   "sta": { "active": 3, "inactive": 1, "ready": 2 },
            //   "fsa": { "active": 1, "inactive": 2, "ready": 1 }
            // }
            updateHubStatus(data);
        } else {
            console.error('Failed to fetch hub status:', response.status);
        }
    } catch (error) {
        console.error('Error fetching hub status:', error);
    }
}

// Update the DOM with hub status data
function updateHubStatus(data) {
    document.getElementById('positionValue').textContent = data.location || "Unknown";
    document.getElementById('operatorValue').textContent = data.operators ?? "0";
    document.getElementById('staActive').textContent = data.sta?.active ?? "0";
    document.getElementById('staInactive').textContent = data.sta?.inactive ?? "0";
    document.getElementById('staReady').textContent = data.sta?.ready ?? "0";
    document.getElementById('fsaActive').textContent = data.fsa?.active ?? "0";
    document.getElementById('fsaInactive').textContent = data.fsa?.inactive ?? "0";
    document.getElementById('fsaReady').textContent = data.fsa?.ready ?? "0";
}

// Add this to MissionView class (or outside if you prefer)
function startHubStatusPolling() {
    fetchHubStatus();
    setInterval(fetchHubStatus, 5000); // Poll every 5 seconds
}

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new MissionView();
    startHubStatusPolling();
});