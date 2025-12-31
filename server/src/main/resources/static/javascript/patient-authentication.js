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
    await fetchPersonalDetails();
    navigate("/patient/dashboard");
}


async function registerKey(baseUrl = "/webauthn/register") {
    this.form = document.getElementById("registerNewKeyForm");
    const formData = new FormData();
    formData.append("credname", this.form.querySelector('[name="credname"]').value);
    const response = await fetch(baseUrl + "/start", {
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


    formData.append("credential", JSON.stringify(encodedResult));
    const newResponse = await fetch(baseUrl + "/finish", {
        method: 'POST',
        body: formData
    });

    return await initialCheckStatus(newResponse);
}


// Performs the full registration procedure based on the given form data (the credential name)
async function register() {

    const nameInput = document.getElementById("name");
    const dobInput = document.getElementById("dob");
    const crednameInput = document.getElementById("credname");

    if(nameInput.value === "" || dobInput.value === "" || crednameInput.value === "") {
        showConfirm("Please fill all the fields.", "Ok", "", () => {});
        return;
    }

    CURRENT_CREDENTIALS = await registerKey("/webauthn/register");
    showConfirm("Confirm this key as encryption method.", "Yes", "", () => {
        sendEncryptedUmk(CURRENT_CREDENTIALS.credentialId).then(() => {
            savePersonalDetails().then(() => {
                navigate("/patient/dashboard");
            });
        })
    });
}