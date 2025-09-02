$(document).ready(function() {
    // Button state tracking
    const buttonStates = {
        'open-drone-x-view': false,
        'open-mission-view': false,
        'open-fire-view': false,
        'open-dds-log': false,
        'path-planning-view': false
    };

    // Window references for tracking opened windows
    const windowReferences = {
        'open-dds-log': null,
        'open-mission-view': null
    };

    // Function to open windows based on button ID
    function openWindow(buttonId) {
        let url = '';
        let windowName = '';
        
        switch(buttonId) {
            case 'open-dds-log':
                url = 'dds/views/log.html';  // Relative path from controls to views
                windowName = 'DDSLogView';
                break;
            case 'open-mission-view':
                url = 'dds/views/mission.html';  // Relative path from controls to views
                windowName = 'MissionView';
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
        
        // Monitor if the window is closed by user (not programmatically)
        const checkClosed = setInterval(function() {
            if (windowReferences[buttonId].closed) {
                clearInterval(checkClosed);
                // Reset button state if window was closed by user
                if (buttonStates[buttonId]) {
                    const button = $('#' + buttonId);
                    const originalAction = button.data('action');
                    toggleButton(buttonId, originalAction, true);
                }
            }
        }, 1000);
        
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
            if ((buttonId === 'open-dds-log' || buttonId === 'open-mission-view') && !skipWindowAction) {
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
            if ((buttonId === 'open-dds-log' || buttonId === 'open-mission-view') && !skipWindowAction) {
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