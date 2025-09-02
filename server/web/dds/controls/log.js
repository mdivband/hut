function addLogEntry(message) {
    const logContent = document.getElementById('logContent');
    
    // Check if user is at the bottom before adding new content
    const isAtBottom = logContent.scrollTop + logContent.clientHeight >= 
                        logContent.scrollHeight - 5;
    
    const logEntry = document.createElement('div');
    // Preserve newlines by using innerHTML and replacing \n with <br>
    logEntry.innerHTML = message.replace(/\n/g, '<br>');
    logEntry.style.marginBottom = '10px'; // Add space between messages
    logEntry.style.whiteSpace = 'pre-wrap'; // Preserve whitespace and wrap text
    logContent.appendChild(logEntry);
    
    // Only auto-scroll if user was already at the bottom
    if (isAtBottom) {
        logContent.scrollTo(0, logContent.scrollHeight);
    }
}

// For initial load with minimal content, add empty spacer
function initializeLog() {
    const logContent = document.getElementById('logContent');
    const spacer = document.createElement('div');
    spacer.style.flexGrow = '1';
    logContent.insertBefore(spacer, logContent.firstChild);
}

// Rate limiting variables
let lastErrorTime = 0;
let lastWaitingForPublisherTime = 0;
// seconds is seconds * 1000 as time is in milliseconds
const fetchInterval = 2000;
const errorRateLimit = 100000;
const waitingForPublisherRateLimit = 100000;

// Fetch latest DDS message from server
async function fetchLatestDDSMessage() {
    try {
        const response = await fetch('/idds/latest');
        if (response.ok) {
            const message = await response.text();
            if (message && message.trim() !== '') {
                // Check for "waiting for publisher" message and rate limit it
                if (message.toLowerCase().includes('waiting for publisher')) {
                    const currentTime = Date.now();
                    if (currentTime - lastWaitingForPublisherTime >= 
                        waitingForPublisherRateLimit) {
                        addLogEntry(message);
                        lastWaitingForPublisherTime = currentTime;
                    }
                } else {
                    addLogEntry(message);
                }
            }
        } else {
            console.error('Failed to fetch latest DDS message:', 
                response.status);
            
            // Rate-limited error logging
            const currentTime = Date.now();
            if (currentTime - lastErrorTime >= errorRateLimit) {
                addLogEntry(`Error: Failed to fetch DDS message (HTTP ${response.status})`);
                lastErrorTime = currentTime;
            }
        }
    } catch (error) {
        console.error('Error fetching latest DDS message:', error);
        
        // Rate-limited error logging
        const currentTime = Date.now();
        if (currentTime - lastErrorTime >= errorRateLimit) {
            addLogEntry('Error: Unable to fetch DDS message - connection failed');
            lastErrorTime = currentTime;
        }
    }
}

// Start polling for latest DDS messages
function startDDSMessagePolling() {
    // Clear the initial loading message if available
    const logContent = document.getElementById('logContent');
    const loadingText = logContent.querySelector('br');
    if (loadingText && loadingText.nextSibling) {
        loadingText.nextSibling.remove();
    }
    if (loadingText) {
        loadingText.remove();
    }
    
    // Fetch immediately and set interval
    fetchLatestDDSMessage();
    setInterval(fetchLatestDDSMessage, fetchInterval);
}

// Initialize when page loads
document.addEventListener('DOMContentLoaded', function() {
    initializeLog();
    startDDSMessagePolling();
});