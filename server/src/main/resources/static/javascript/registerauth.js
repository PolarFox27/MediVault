document.addEventListener("submit", (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    fetch('/webauthn/register/user', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => {
                throw new Error(`ServerError: ${text}`);
            });
        }
        return response.json(); // ✅ parse JSON
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
    .then((response) => {
        followRedirect(response);
    })
    .catch((error) => {
        displayError(error);
    });
})