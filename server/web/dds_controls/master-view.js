$(document).ready(function() {
    // Button state tracking
    const buttonStates = {
        'open-drone-x-view': false,
        'open-mission-view': false,
        'open-fire-view': false,
        'open-dds-log': false,
        'path-planning-view': false
    };

    // Function to capitalize first letter of each word
    function toTitleCase(str) {
        return str.replace(/\w\S*/g, function(txt) {
            return txt.charAt(0).toUpperCase() + txt.substr(1).toLowerCase();
        });
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
        const message = $('<p>Are you sure you want to ' + toTitleCase(action) + '?</p>');
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

    // Function to toggle button state
    function toggleButton(buttonId, action) {
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
            
            // Console log only after confirmation
            console.log('Opening window for:', action);
            
        } else {
            // Closing - revert to blue and original text
            button.removeClass('open');
            button.text(action);
            buttonStates[buttonId] = false;
            
            // Console log only after confirmation
            console.log('Closing window for:', action);
        }
    }

    // Attach click handlers to all control buttons
    $('.control-button').click(function() {
        const buttonId = $(this).attr('id');
        const action = $(this).data('action');
        const isOpen = buttonStates[buttonId];
        
        let confirmText;
        if (isOpen) {
            // Closing action
            if (buttonId === 'path-planning-view') {
                confirmText = 'close path planning view';
            } else {
                confirmText = 'close ' + action.toLowerCase().replace('open ', '');
            }
        } else {
            // Opening action - pass the action as-is, showConfirmationDialog will add "open" if needed
            confirmText = action.toLowerCase();
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