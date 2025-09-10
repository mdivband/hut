$(document).ready(function() {
    // Button state tracking
    const buttonStates = {
        'open-FSA-1-view': false,
        'open-STA-1-view': false,
        'open-mission-view': false,
        'open-fire-view': false,
        'open-dds-log': false,
        'path-planning-view': false
    };

    // Window references for tracking opened windows
    const windowReferences = {
        'open-dds-log': null,
        'open-mission-view': null,
        'open-drone-x-view': null
    };

    // Function to open windows based on button ID
    function openWindow(buttonId) {
        let url = '';
        let windowName = '';

        switch(buttonId) {
            case 'open-dds-log':
                url = 'dds/views/log.html';
                windowName = 'DDSLogView';
                break;
            case 'open-mission-view':
                url = 'dds/views/mission.html';
                windowName = 'MissionView';
                break;
            case 'open-FSA-1-view':
                url = buildFoxgloveURL('FSA', 1, 'soorati-lab', null);
                console.log('Opening Foxglove FSA-1 URL:', url);
                windowName = 'ws://localhost:9201 | Foxglove';
                break;
            case 'open-STA-1-view':
                url = buildFoxgloveURL('STA', 1, 'soorati-lab', null);
                console.log('Opening Foxglove STA-1 URL:', url);
                windowName = 'ws://localhost:9101 | Foxglove';
                break;
            default:
                return false;
        }
        
        // Get current window dimensions
        const currentWidth = window.outerWidth;
        const currentHeight = window.outerHeight;
        const windowFeatures = `width=${currentWidth},height=${currentHeight},
                        scrollbars=yes,resizable=yes,location=yes,
                        menubar=yes,toolbar=yes,status=yes`;
        windowReferences[buttonId] = window.open(url, windowName, windowFeatures);

        // Check if window was blocked by popup blocker
        if (!windowReferences[buttonId]) {
            alert('Popup blocked! Please allow popups for this site and try again.');
            return false;
        }
        
        // Only monitor window closed by user if NOT FSA or STA in buttonId
        if (!/FSA|STA/i.test(buttonId)) {
            const checkClosed = setInterval(function() {
                if (windowReferences[buttonId].closed) {
                    clearInterval(checkClosed);
                    // Reset button state if window was closed by user
                    if (buttonStates[buttonId]) {
                        // Only log if the button state was open
                        console.log('Window closed by user:', buttonId);
                        const button = $('#' + buttonId);
                        const originalAction = button.data('action');
                        toggleButton(buttonId, originalAction, true);
                    }
                }
            }, 1000);
        }
        
        return true;
    }

    // Function to close window based on button ID
    function closeWindow(buttonId) {
        if (windowReferences[buttonId] && !windowReferences[buttonId].closed) {
            windowReferences[buttonId].close();
            windowReferences[buttonId] = null;
            return true;
        }
        return false;
    }

    // Function to toggle button state
    function toggleButton(buttonId, action, skipWindowAction = false) {
        const button = $('#' + buttonId);
        const isOpen = buttonStates[buttonId];
        console.log(`Toggling button: ${buttonId}, Current state: ${isOpen}, Action: ${action}`);

        if (!isOpen) {
            // Opening - change to red and update text
            button.addClass('open');
            
            // Update button text based on type
            if (buttonId === 'path-planning-view') {
                button.text('Close Path Planning View');
            } else {
                button.text('Close ' + action.replace('Open ', ''));
            }
            
            buttonStates[buttonId] = true;
            
            // Handle window opening for supported buttons
            if (
                buttonId === 'open-dds-log' ||
                buttonId === 'open-mission-view' ||
                buttonId === 'open-drone-x-view' ||
                buttonId === 'open-FSA-1-view' ||
                buttonId === 'open-STA-1-view'
            ) {
                const success = openWindow(buttonId);
                if (!success) {
                    // Revert button state if window opening failed
                    button.removeClass('open');
                    button.text(action);
                    buttonStates[buttonId] = false;
                    return;
                }
            }
            
            // Console log only after confirmation
            console.log('Opening window for:', action);
            
        } else {
            // Closing - revert to blue and original text
            button.removeClass('open');
            button.text(action);
            buttonStates[buttonId] = false;
            
            // Handle window closing for supported buttons
            if ((buttonId === 'open-dds-log' || buttonId === 'open-mission-view' 
                || buttonId === 'open-drone-x-view') && !skipWindowAction) {
                closeWindow(buttonId);
            }
            
            // Console log only after confirmation
            console.log('Closing window for:', action);
        }
    }

    // Function to show confirmation dialog
    function showConfirmationDialog(action, callback) {
        // Create dialog elements using jQuery DOM creation
        const dialog = $('<div class="confirmation-dialog"></div>');
        const title = $('<h3>Confirm Action</h3>');

        // Check if action contains "Open" or "Close", if not add "Open" to it
        let actionText = action;
        if (!action.toLowerCase().includes('open') && !action.toLowerCase().includes('close')) {
            actionText = 'open ' + action;
        }
        const message = $('<p>Are you sure you want to ' + action + '?</p>');
        const buttonContainer = $('<div class="dialog-buttons"></div>');
        const confirmBtn = $('<button class="confirm-btn">Yes</button>');
        const cancelBtn = $('<button class="cancel-btn">Cancel</button>');
        
        // Assemble the dialog
        buttonContainer.append(confirmBtn).append(cancelBtn);
        dialog.append(title).append(message).append(buttonContainer);

        // Create overlay
        const overlay = $('<div></div>').css({
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            background: 'rgba(0,0,0,0.5)',
            zIndex: 10000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
        });
        
        overlay.append(dialog);
        $('body').append(overlay);

        // Handle button clicks
        confirmBtn.click(function() {
            overlay.remove();
            callback(true);
        });

        cancelBtn.click(function() {
            overlay.remove();
            callback(false);
        });

        // Close on overlay click
        overlay.click(function(e) {
            if (e.target === overlay[0]) {
                overlay.remove();
                callback(false);
            }
        });
    }

    // Function to build Foxglove URL with drone type and ID
    const orgId = 'AIVE';   //'soorati-lab'; // Default organization ID
    function buildFoxgloveURL(droneType, droneId, layoutId = null) {
        let websocketPort;
        
        // Determine websocket port based on drone type and ID
        switch(droneType.toUpperCase()) {
            case 'STA':
                websocketPort = 9100 + parseInt(droneId);
                break;
            case 'FSA':
                websocketPort = 9200 + parseInt(droneId);
                break;
            default:
                throw new Error(`Unknown drone type: ${droneType}. Supported types: STA, FSA`);
        }
        
        const baseURL = 'https://app.foxglove.dev';
        const websocketURL = `ws://localhost:${websocketPort}`;
        const encodedWebsocketURL = encodeURIComponent(websocketURL);
        
        let url = `${baseURL}/${orgId}/view?ds=foxglove-websocket&ds.url=${encodedWebsocketURL}`;
        
        // Add layout ID if provided
        if (layoutId) {
            url += `&layoutId=${layoutId}`;
        }
        
        return url;
    }

    // Function to capitalize first letter of each word
    function toTitleCase(str) {
        return str.replace(/\w\S*/g, function(txt) {
            return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
        });
    }

    // Attach click handlers to all view buttons
    $('.view-button').click(function() {
        const buttonId = $(this).attr('id');
        const action = $(this).data('action');
        const isOpen = buttonStates[buttonId];
        
        let confirmText;
        if (isOpen) {
            // Closing action
            if (buttonId === 'path-planning-view') {
                confirmText = 'Close Path Planning View';
            } else {
                confirmText = 'Close ' + action.replace('Open ', '');
            }
        } else {
            // Opening action
            if (buttonId === 'path-planning-view') {
                confirmText = 'Open Path Planning View';
            } else {
                confirmText = action;
            }
        }

        showConfirmationDialog(confirmText, function(confirmed) {
            if (confirmed) {
                toggleButton(buttonId, action);
            }
        });
    });

    // Initialize button states
    Object.keys(buttonStates).forEach(function(buttonId) {
        buttonStates[buttonId] = false;
    });
});