let UMK = null;
let CURRENT_CREDENTIALS = null;

// Retrieve the PRF result from the webauthn credential
async function generateWebAuthnPRF(credentialId){
    const assertion = await navigator.credentials.get({
        publicKey: {
            challenge: crypto.getRandomValues(new Uint8Array(32)),
            allowCredentials: [{ id: credentialId, type: "public-key" }],
            userVerification: "required",
            extensions: {
                prf: { eval: { first: new Uint8Array(32) } }
            }
        }
    });
    return assertion.getClientExtensionResults().prf.results.first;
}

// Encrypt the UMK for the given credential and PRF value
// If the UMK is null, a random one is generated
async function encryptUMK(credentialId, prf) {
    if(UMK === null){
        UMK = crypto.getRandomValues(new Uint8Array(32));
    }

    const hkdfKey = await crypto.subtle.importKey(
        "raw",
        prf,
        "HKDF",
        false,
        ["deriveKey"]
    );

    const wrapKey = await crypto.subtle.deriveKey(
        {
            name: "HKDF",
            hash: "SHA-256",
            salt: new Uint8Array(32),
            info: new TextEncoder().encode("UMK wrapping")
        },
        hkdfKey,
        { name: "AES-GCM", length: 256 },
        false,
        ["encrypt"]
    );

    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encryptedUmk = new Uint8Array(
        await crypto.subtle.encrypt({ name: "AES-GCM", iv }, wrapKey, UMK)
    );

    return {
        encryptedUmk: uint8ArrayToHex(encryptedUmk),
        iv: uint8ArrayToHex(iv),
        credentialId: credentialId
    };
}

// Decrypt the UMK based on the PRF value
async function decryptUMK({ encryptedUmk, iv, credentialId }, prf) {
    const hkdfKey = await crypto.subtle.importKey(
        "raw",
        prf,
        "HKDF",
        false,
        ["deriveKey"]
    );

    const wrapKey = await crypto.subtle.deriveKey(
        {
            name: "HKDF",
            hash: "SHA-256",
            salt: new Uint8Array(32),
            info: new TextEncoder().encode("UMK wrapping")
        },
        hkdfKey,
        { name: "AES-GCM", length: 256 },
        false,
        ["decrypt"]
    );

    UMK = new Uint8Array(
        await crypto.subtle.decrypt(
            {name: "AES-GCM", iv: hexToUint8Array(iv)},
            wrapKey,
            hexToUint8Array(encryptedUmk)
        )
    );
}

// Encrypt and send the UMK to the server for the given credential
async function sendEncryptedUmk(credentialId) {

    const prf = await generateWebAuthnPRF(hexToUint8Array(credentialId));

    const encryptedUmk = await encryptUMK(credentialId, prf);
    await fetch("/patient/umk", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(encryptedUmk)
    });
}

// Retrieve the encrypted UMK from the server and decrypt it
async function fetchEncryptedUmk(prf) {
    const encryptedUmkResponse = await fetch("/patient/umk", {
        method: "GET",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        }
    });
    const encryptedUmk = await encryptedUmkResponse.json();
    await decryptUMK(encryptedUmk, prf);
}

async function encrypt(data) {
    const key = await crypto.subtle.importKey(
        "raw",
        UMK,
        { name: "AES-GCM" },
        false,
        ["encrypt", "decrypt"]
    );

    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encryptedData = new Uint8Array(
        await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, data)
    );
    return {encryptedData, iv};
}

async function decrypt(encryptedData, iv) {
    const key = await crypto.subtle.importKey(
        "raw",
        UMK,
        { name: "AES-GCM" },
        false,
        ["encrypt", "decrypt"]
    );

    return new Uint8Array(
        await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, encryptedData)
    );
}