// Animation to show the Login Form
function showLogin() {
    document.getElementById("forms").style.transform = "translateX(0%)";
    document.getElementById("slider").style.transform = "translateX(0)";
}

// Animation to show the Registration Form
function showRegister() {
    document.getElementById("forms").style.transform = "translateX(-50%)";
    document.getElementById("slider").style.transform = "translateX(100%)";
}


// Initiates the Webauthn login procedure and fetches an assertion from the server.
// Then, submits the form to complete the login procedure.
async function login() {
    this.form = document.getElementById("loginForm");
    const formData = new FormData(this.form);
    const response = await fetch('/webauthn/login/start', {
        method: 'POST',
        body: formData
    });

    const credentialGetJson = await initialCheckStatus(response);

    const credentialGetOptions = {
        publicKey: {
            ...credentialGetJson.publicKey,
            allowCredentials: credentialGetJson.publicKey.allowCredentials
                && credentialGetJson.publicKey.allowCredentials.map(credential => ({
                    ...credential,
                    id: base64urlToUint8array(credential.id),
                })),
            challenge: base64urlToUint8array(credentialGetJson.publicKey.challenge),
            extensions: {
                ...credentialGetJson.publicKey.extensions,
                prf: { eval: { first: new Uint8Array(32) } }
            },
        },
    };

    const publicKeyCredential = await navigator.credentials.get(credentialGetOptions);
    const prf = publicKeyCredential.getClientExtensionResults().prf.results.first;

    const encodedResult = {
        type: publicKeyCredential.type,
        id: publicKeyCredential.id,
        response: {
            authenticatorData: uint8arrayToBase64url(publicKeyCredential.response.authenticatorData),
            clientDataJSON: uint8arrayToBase64url(publicKeyCredential.response.clientDataJSON),
            signature: uint8arrayToBase64url(publicKeyCredential.response.signature),
            userHandle: publicKeyCredential.response.userHandle && uint8arrayToBase64url(publicKeyCredential.response.userHandle),
        },
        clientExtensionResults: {},
    };

    const newFormData = new FormData();
    newFormData.append("credential", JSON.stringify(encodedResult));

    const newResponse = await fetch("/webauthn/login/finish", {
        method: 'POST',
        body: newFormData
    });

    CURRENT_CREDENTIALS = await initialCheckStatus(newResponse);
    await fetchEncryptedUmk(prf);
    navigate("/patient/dashboard");
}


// Performs the full registration procedure based on the given form data (the credential name)
async function register() {
    this.form = document.getElementById("registerForm");
    const formData = new FormData(this.form);
    const response = await fetch('/webauthn/register/start', {
        method: 'POST',
        body: formData
    });

    const credentialCreateJson = await initialCheckStatus(response);

    const credentialCreateOptions = {
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
    };

    const publicKeyCredential = await navigator.credentials.create(credentialCreateOptions);

    const encodedResult = {
        type: publicKeyCredential.type,
        id: publicKeyCredential.id,
        response: {
            attestationObject: uint8arrayToBase64url(publicKeyCredential.response.attestationObject),
            clientDataJSON: uint8arrayToBase64url(publicKeyCredential.response.clientDataJSON),
            transports: publicKeyCredential.response.getTransports && publicKeyCredential.response.getTransports() || [],
        },
        clientExtensionResults: publicKeyCredential.getClientExtensionResults(),
    };

    const form = document.getElementById("registerForm");
    const newFormData = new FormData();
    newFormData.append("credname", form.querySelector('[name="credname"]').value);
    newFormData.append("credential", JSON.stringify(encodedResult));

    const newResponse = await fetch("/webauthn/register/finish", {
        method: 'POST',
        body: newFormData
    });

    CURRENT_CREDENTIALS = await initialCheckStatus(newResponse);

    navigate("/patient/account", () => {showFullKeyManagementPage(false)});
}