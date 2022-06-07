$(document).ready(function () {
    $('body')[0].style.visibility = 'visible';
    $('body').addClass('ready');
    $("#overlay_div").show();
    var closeSurvey = $('<button id="close_survey" style="cursor: pointer;">Close Survey</button>').appendTo($("#overlay_div"));
    $('<br>').appendTo($("#overlay_div"));
    var initialSurvey = "https://forms.office.com/Pages/ResponsePage.aspx?id=-XhTSvQpPk2-iWadA62p2CmPPgx944RCrlRRT-uovIBURFNUT0tISzhON1U0TlM5U1gwU0ZHWFcwVS4u&embed=true";
    var surveySource = initialSurvey;
    var surveyFrame = $('<iframe width="40%" height= "90%" src=' + surveySource + ' frameborder= "0" marginwidth= "0" marginheight= "0" style= "border: none; max-width:100%; max-height:100vh" allowfullscreen webkitallowfullscreen mozallowfullscreen msallowfullscreen> </iframe>').appendTo($("#overlay_div"));
    closeSurvey.on('click', function () {
        var isSure = confirm("Have you completed and submitted the survey?");
        if (isSure) {
            $("#overlay_div").empty();
            var closeVideo = $('<button id="close_video" style="cursor: pointer;">Close Video</button>').appendTo($("#overlay_div"));
            $('<br>').appendTo($("#overlay_div"));
            var videoFrame = $('<iframe width="90%" height="90%" src="https://www.youtube.com/embed/se0vuA1uVmk" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope" allowfullscreen></iframe>').appendTo($("#overlay_div"));
            closeVideo.on('click', function () {
                var isSure = confirm("Have you watched the tutorial video?");
                if (isSure) {
                    $("#overlay_div").empty();
                    var closeVideoSurvey = $('<button id="close_survey" style="cursor: pointer;">Close Survey</button>').appendTo($("#overlay_div"));
                    $('<br>').appendTo($("#overlay_div"));
                    var videoSurvey = "https://forms.office.com/Pages/ResponsePage.aspx?id=-XhTSvQpPk2-iWadA62p2CmPPgx944RCrlRRT-uovIBUOTdKRllYODJDNFRPWU9NSVFUMjlaQVNaOC4u&embed=true";
                    var surveySource = videoSurvey;
                    var videoSurveyFrame = $('<iframe width="40%" height= "90%" src=' + surveySource + ' frameborder= "0" marginwidth= "0" marginheight= "0" style= "border: none; max-width:100%; max-height:100vh" allowfullscreen webkitallowfullscreen mozallowfullscreen msallowfullscreen> </iframe>').appendTo($("#overlay_div"));
                    closeVideoSurvey.on('click', function () {
                        var isSure = confirm("Have you completed and submitted the survey?");
                        if (isSure) {
                            $("#overlay_div").empty();
                            $("#overlay_div").hide();
                            window.location.replace("/redirect/");
                        }
                    });
                }
            });
        }
    });
});