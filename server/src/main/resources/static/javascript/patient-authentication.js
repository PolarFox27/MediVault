// HTML references for the slider animation
const forms = document.getElementById("forms");
const slider = document.getElementById("slider");

// Animation to show the Login Form
function showLogin() {
    forms.style.transform = "translateX(0%)";
    slider.style.transform = "translateX(0)";
}

// Animation to show the Registration Form
function showRegister() {
    forms.style.transform = "translateX(-50%)";
    slider.style.transform = "translateX(100%)";
}


// Initiates the Webauthn login procedure and fetches an assertion from the server.
// Then, submits the form to complete the login procedure.
async function checkCredentials() {
    this.form = document.getElementById("loginForm");
    const formData = new FormData(form);
    fetch('/webauthn/login/start', {
        method: 'POST',
        body: formData
    })
    .then(response => initialCheckStatus(response))
    .then(credentialGetJson => ({
        publicKey: {
        ...credentialGetJson.publicKey,
        allowCredentials: credentialGetJson.publicKey.allowCredentials
            && credentialGetJson.publicKey.allowCredentials.map(credential => ({
            ...credential,
            id: base64urlToUint8array(credential.id),
            })),
        challenge: base64urlToUint8array(credentialGetJson.publicKey.challenge),
        extensions: credentialGetJson.publicKey.extensions,
        },
    }))
    .then(credentialGetOptions =>
        navigator.credentials.get(credentialGetOptions))
    .then(publicKeyCredential => ({
        type: publicKeyCredential.type,
        id: publicKeyCredential.id,
        response: {
        authenticatorData: uint8arrayToBase64url(publicKeyCredential.response.authenticatorData),
        clientDataJSON: uint8arrayToBase64url(publicKeyCredential.response.clientDataJSON),
        signature: uint8arrayToBase64url(publicKeyCredential.response.signature),
        userHandle: publicKeyCredential.response.userHandle && uint8arrayToBase64url(publicKeyCredential.response.userHandle),
        },
        clientExtensionResults: publicKeyCredential.getClientExtensionResults(),
    }))
    .then((encodedResult) => {
        document.getElementById("credential").value = JSON.stringify(encodedResult);
        this.form.submit();
    })
    .catch(error => displayError(error))
}


// Performs the full registration procedure based on the given form data (the credential name)
async function register(formData) {
    return fetch('/webauthn/register/start', {
        method: 'POST',
        body: formData
    })
        .then(response => {
            if (!response.ok) {
                return response.text().then(text => {
                    throw new Error(`ServerError: ${text}`);
                });
            }
            return response.json();
        })
        .then(credentialCreateJson => ({
            publicKey: {
                ...credentialCreateJson.publicKey,
                challenge: base64urlToUint8array(credentialCreateJson.publicKey.challenge),
                user: {
                    ...credentialCreateJson.publicKey.user,
                    id: base64urlToUint8array(credentialCreateJson.publicKey.user.id),
                },
                excludeCredentials: credentialCreateJson.publicKey.excludeCredentials.map(credential => ({
                    ...credential,
                    id: base64urlToUint8array(credential.id),
                })),
                extensions: credentialCreateJson.publicKey.extensions,
            },
        }))
        .then(credentialCreateOptions =>
            navigator.credentials.create(credentialCreateOptions))
        .then(publicKeyCredential => ({
            type: publicKeyCredential.type,
            id: publicKeyCredential.id,
            response: {
                attestationObject: uint8arrayToBase64url(publicKeyCredential.response.attestationObject),
                clientDataJSON: uint8arrayToBase64url(publicKeyCredential.response.clientDataJSON),
                transports: publicKeyCredential.response.getTransports && publicKeyCredential.response.getTransports() || [],
            },
            clientExtensionResults: publicKeyCredential.getClientExtensionResults(),
        }))
        .then((encodedResult) => {
            const form = document.getElementById("registerForm");
            const formData = new FormData(form);
            formData.append("credential", JSON.stringify(encodedResult));
            return fetch("/webauthn/register/finish", {
                method: 'POST',
                body: formData
            })
        })
}


// Binds the registration function to the submission of the registration form
document.addEventListener("submit", (e) => {
    e.preventDefault();
    register(new FormData(e.target))
        .then((response) => {
            console.log(response.json());
            //window.location.href = "/patient-dashboard";
            //followRedirect(response);
        })
        .catch((error) => {
            displayError(error);
        });

})