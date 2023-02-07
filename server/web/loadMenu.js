/**
 * Load all the required libraries.
 * Uses .script from LABjs to import libraries.
 */

$LAB.setOptions({
    CacheBust: true
})

// JQuery API: http://api.jquery.com
.script("lib/jquery/jquery-1.9.1.min.js")
// Menu file
.script("app/menu.js")

.wait(function() {
    $(document).ready(function() {
        menuInit();
    });
});
