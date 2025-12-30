let showRegistration = false;

// Show/Hide the new key registration box
function toggleRegistration() {
    showRegistration = !showRegistration;
    document.getElementById('registrationBox').style.display = showRegistration ? 'block' : 'none';
    document.getElementById('keyListBox').style.display = showRegistration ? 'none' : 'block';
}

// Call the DELETE key endpoint to forget the authentication key provided
function deleteKey(credentialId){
    fetch(`/webauthn/remove?credentialId=${encodeURIComponent(credentialId)}`, {
        method: "DELETE",
        credentials: "same-origin"
    })
        .then(() => {
            navigate("/patient/key-management");
        })
        .catch((error) => {
            displayError(error);
        });
}

// Bind the deleteKey function to the delete buttons in the key list
document.addEventListener("click", e => {
    const btn = e.target.closest(".delete-btn");
    if (!btn) return;
    deleteKey(btn.dataset.credentialId);
});


// Performs the full key registration procedure based on the given form data (the credential name)
async function registerNewKey() {

    this.form = document.getElementById("registerNewKeyForm");
    const formData = new FormData(this.form);

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

function showFullKeyManagementPage(showAll) {
    document.getElementById("app-header").style.display = showAll ? "block" : "none";
    document.getElementById("back-button").style.display = showAll ? "block" : "none";
    document.getElementById('keyListBox').style.display = showAll ? "block" : "none";
    document.getElementById('registrationBox').style.display = "none";
}

function savePersonalDetails() {
    if(UMK == null){
        sendEncryptedUmk(CURRENT_CREDENTIALS.credentialId).then(() => {
            showFullKeyManagementPage(true);
        });
    }
}