function getInternalIP() {
    return window.RTCPeerConnection
        ? new Promise((resolve, reject) => {
            const pc = new RTCPeerConnection({
                iceServers: []
            });
            pc.createDataChannel("");

            pc.createOffer(
                (sdp) => pc.setLocalDescription(sdp),
                (e) => reject(e)
            );

            pc.onicecandidate = (e) => {
                if (e.candidate) {
                    const regex = /([0-9]{1,3}(\.[0-9]{1,3}){3})/;
                    const match = regex.exec(e.candidate.candidate.split(" ")[4]);
                    if (match) {
                        resolve(match[1]);
                    }
                }
            };
        })
        : null;
}

function getExternalIP() {
    return fetch("https://api.ipify.org?format=json")
        .then((response) => response.json())
        .then((data) => data.ip)
        .catch((error) => "Не удалось получить внешний IP");
}

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

        // Получение и отображение IP-адресов
        getInternalIP().then((internalIP) => {
            document.getElementById("local-ip").textContent = internalIP || "Не удалось получить внутренний IP";
        });

        getExternalIP().then((externalIP) => {
            document.getElementById("external-ip").textContent = externalIP;
        });

        const ingressIP = "34.107.88.127"; // Замените на фактический IP-адрес Ingress Controller
        document.getElementById("ingress-ip").textContent = ingressIP;
    }
}

document.addEventListener("DOMContentLoaded", function() {
    document.querySelector(".tablinks").click();
});