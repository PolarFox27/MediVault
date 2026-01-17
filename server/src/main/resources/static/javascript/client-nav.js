const DEFAULT_CALLBACKS = {
    "/patient/dashboard": () => { fetchAndRenderFiles(); fetchDoctors(); },
    "/patient/account": () => loadPersonalDetails(),
    "/auth/patient": () => renderCaptchas(),
    "/auth/doctor": () => renderDoctorCaptcha(),
    "/doctor/dashboard": () => loadDoctorDashboard(),
    "/welcome": () => cleanSensitiveVariables(),
    "/patient/appointed-doctors": () => fetchDoctors()
}


// Function loading the correct page fragment when navigating to a URL on the site
// It handles the browser history to prevent bugs with the back and forward buttons
function navigate(url, callback = () => {}, defaultCallback = true) {
    const app_content = document.getElementById("app-content");
    const app_header = document.getElementById("app-header");
    app_content.innerHTML = "<p>Loading...</p>";

    fetch(url, {
        credentials: "same-origin",
        headers: {
            "X-Requested-With": "SPA",
        }
    })
        .then((response) => response.text())
        .then((text) => {
            app_content.innerHTML = text;
            history.pushState(null, "", url);
            if(defaultCallback) {
                DEFAULT_CALLBACKS[url]?.();
            }
            callback();
        });

    if(url.includes("/patient/")) {
        fetch("/patient/header", {
            credentials: "same-origin",
            headers: {
                "X-Requested-With": "SPA",
            }
        })
            .then((response) => response.text())
            .then((text) => {
                app_header.innerHTML = text;
                app_header.style.display = "block";
                loadWelcomeMessage();
            })
    }
    else if(url.includes("/doctor/")) {
        fetch("/doctor/header", {
            credentials: "same-origin",
            headers: {
                "X-Requested-With": "SPA",
            }
        })
            .then((response) => response.text())
            .then((text) => {
                app_header.innerHTML = text;
                app_header.style.display = "block";
                loadDoctorInfo();
            })
    }
    else {
        app_header.style.display = "none";
    }
}

// If a link marked with "data-client-nav" is pressed, the navigate function is called instead.
document.addEventListener("click", function (e) {
    const link = e.target.closest("a[data-client-nav]");
    if (!link) return;

    e.preventDefault();
    navigate(link.getAttribute("href"));
});

// If the back / forward browser buttons are pressed, the navigate function is called
window.addEventListener("popstate", () => {
    navigate(location.pathname);
});

// When the website is loaded for th first time, load the welcome page
document.addEventListener("DOMContentLoaded", () => {
    if (location.pathname === "/") {
        navigate("/welcome");
    }
    else {
        navigate(location.pathname);
    }
});

// This function clear the encryption keys from the memory, preventing data remanence attacks.
function cleanSensitiveVariables() {
    if(UMK) UMK.fill(0);
    if(DOCTOR_PRIVATE_KEY) DOCTOR_PRIVATE_KEY.fill(0);
}
