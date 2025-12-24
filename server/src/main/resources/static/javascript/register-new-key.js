// HTML references for the new key registration box animation
let showRegistration = false;
let registrationBox = document.getElementById('registrationBox');
let keyListBox = document.getElementById('keyListBox');

// Show/Hide the new key registration box
function toggleRegistration() {
    showRegistration = !showRegistration;
    registrationBox.style.display = showRegistration ? 'block' : 'none';
    keyListBox.style.display = showRegistration ? 'none' : 'block';
}


// Performs the full key registration procedure based on the given form data (the credential name)
async function register(formData) {
    return fetch('/webauthn/newkey/start', {
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
            const form = document.getElementById("registerNewKeyForm");
            const formData = new FormData(form);
            formData.append("credential", JSON.stringify(encodedResult));
            return fetch("/webauthn/newkey/finish", {
                method: 'POST',
                body: formData
            })
        })
}


// Binds the registration function to the submission of the new key registration form
document.addEventListener("submit", (e) => {
    e.preventDefault();
    register(new FormData(e.target))
        .then((response) => {
            followRedirect(response);
        })
        .catch((error) => {
            displayError(error);
        });

})