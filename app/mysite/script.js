function openPage(evt, pageName) {
    let i, tabcontent, tablinks;

    tabcontent = document.getElementsByClassName("tabcontent");
    for (i = 0; i < tabcontent.length; i++) {
        tabcontent[i].style.display = "none";
    }

    tablinks = document.getElementsByClassName("tablinks");
    for (i = 0; i < tablinks.length; i++) {
        tablinks[i].className = tablinks[i].className.replace(" active", "");
    }

    document.getElementById(pageName).style.display = "block";
    evt.currentTarget.className += " active";

    if (pageName === 'Home') {
        document.getElementById('home-image').style.display = "block";
        document.getElementById('check-image').style.display = "none";
    } else {
        document.getElementById('home-image').style.display = "none";
        document.getElementById('check-image').style.display = "block";
    }
}

document.addEventListener("DOMContentLoaded", function() {
    document.querySelector(".tablinks").click();
    getIPs();
});

function getIPs() {
    fetch('https://api.ipify.org?format=json')
        .then(response => response.json())
        .then(data => {
            document.getElementById('external-ip').textContent = data.ip;
        });

    fetch('https://api64.ipify.org?format=json')
        .then(response => response.json())
        .then(data => {
            document.getElementById('local-ip').textContent = data.ip;
        });
}
