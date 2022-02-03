function menuInit() {
    var mainButtonGroup = $("#mainButtonGroup");
    var inProgressDiv = $("#inProgressDiv");
    var loadScenarioDiv = $("#loadScenarioDiv");

    mainButtonGroup.hide();
    inProgressDiv.hide();

    // ------ Main Screen ------
    $("#buttonSandboxMode").on('click', function () {
        mainButtonGroup[0].style = 'animation: popout 0.8s;';
        mainButtonGroup[0].addEventListener("animationend", function() {
            mainButtonGroup[0].style.display = 'none';
            $.post('/mode/sandbox', function () {
                window.location = "/sandbox.html";
            });
        });
    });

    $("#buttonLoadScenario").on('click', function () {
        mainButtonGroup[0].style = 'animation: middleToLeft 0.5s forwards;';
        loadScenarioDiv[0].style = 'animation: rightToMiddle 0.5s forwards;';
        loadScenarioDiv[0].style.visibility = 'visible';
    });

    // ------ In Progress Screen ------
    $("#buttonResumeOperation").on('click', function () {
        inProgressDiv[0].style = 'animation: popout 0.5s forwards;';
        inProgressDiv[0].addEventListener("animationend", function () {
            window.location = "/sandbox.html";
        })
    });


    $("#buttonAbortOperation").on('click', function () {
        // Close the resume/abort window
        inProgressDiv[0].style = 'animation: popout 0.5s forwards;';

        // Load the scenario window
        mainButtonGroup[0].style = 'animation: leftToMiddle 0.5s forwards;';
        loadScenarioDiv[0].style = 'animation: middleToRight 0.5s forwards;';
        loadScenarioDiv[0].style.visibility = 'visible';
        setSelected(null);

        $.post('/reset', function () {
            state.reset();

        });
    });

    // ------ Load Screen ------
    var gameIds = [];
    var fileNames = [];
    var selectedIndex = null;
    var scenarioSearch = $("#scenarioSearch");
    var scenarioList = $('#scenarioList');
    var scenarioStartButton = $('#buttonStartScenario');

    var createList = function () {
        for(var i = 0; i < gameIds.length; i++) {
            var li = document.createElement("li");
            li.classList.add('scenarioListItem');
            li.addEventListener('click', function (e) {
                setSelected(e.target);
            });
            li.appendChild(document.createTextNode(gameIds[i]));
            scenarioList.append(li);
        }
    };

    var setSelected = function (selectedLi) {
        for(var i = 0; i < gameIds.length; i++) {
            var li = scenarioList[0].children[i];
            if(li === selectedLi) {
                li.classList.add('selected');
                selectedIndex = i;
            }
            else
                li.classList.remove('selected');
        }
        scenarioStartButton.prop('disabled', false);
    };

    var updateSearch = function () {
        var search = scenarioSearch.val().toLowerCase();
        for(var i = 0; i < gameIds.length; i++) {
            var li = scenarioList[0].children[i];
            var val = li.innerHTML.toLowerCase();
            li.style.display = val.includes(search) ? 'block' : 'none';
        }
    };

    scenarioSearch.focus(function () {
        var val = scenarioSearch.val();
        if(val === 'Search')
            scenarioSearch.val('');
    });
    scenarioSearch.focusout(function () {
        var val = scenarioSearch.val();
        if(val === '')
            scenarioSearch.val('Search');
    });
    scenarioSearch.on('input', function () {
        updateSearch();
    });

    scenarioStartButton.click(function () {
        $.post('/mode/scenario', {'file-name': fileNames[selectedIndex]}, function () {
            loadScenarioDiv[0].style = 'animation: popout 0.5s forwards;';
            loadScenarioDiv[0].addEventListener("animationend", function () {
                window.location = "/sandbox.html";
            })
        }).fail(function () {
            showError("Unable to start scenario.");
        })
    });
    $("#buttonCancelLoadScenario").click(function() {
        mainButtonGroup[0].style = 'animation: leftToMiddle 0.5s forwards;';
        loadScenarioDiv[0].style = 'animation: middleToRight 0.5s forwards;';
        loadScenarioDiv[0].style.visibility = 'visible';
        setSelected(null);
    });

    var showError = function(msg) {
        var errorEl = $("#loadScenarioErrorText");
        errorEl.text(msg);
        errorEl.fadeIn(300);
        setTimeout(function () {
            errorEl.fadeOut(300);
        }, 3000);
    };

    $.get("/mode/scenario-list", {}, function(data) {
        gameIds = [];
        fileNames = [];
        for(var i in data) {
            gameIds.push(data[i].gameId);
            fileNames.push(data[i].fileName);
        }
        createList();
    }).fail(function () {
        showError("Unable to get scenario list.");
    });

    $.get("/mode/in-progress", {}, function(data) {
        data = String(data);
        var inProgress = (data === 'true');
        if (inProgress) {
            inProgressDiv.show();
        } else {
            mainButtonGroup.show();
            // Ensure any scenario is loaded with a clean slate
            $.post('/reset', function () {
                        state.reset();
                    });
        }
    }).fail(function () {
        showError("Unable to connect to server.");
    })
}
