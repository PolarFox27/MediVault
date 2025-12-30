let UMK = null;
let CURRENT_CREDENTIALS = null;

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

async function encryptUMK(credentialId, prf) {
    if(UMK === null){
        UMK = crypto.getRandomValues(new Uint8Array(32));
        console.log("UMK GENERATED: " + uint8ArrayToHex(UMK));
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

// --- Decrypt UMK (login time) ---
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

async function sendEncryptedUmk(credentialId) {

    const prf = await generateWebAuthnPRF(hexToUint8Array(credentialId));

    const encryptedUmk = await encryptUMK(credentialId, prf);
    await fetch("/umk/set", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(encryptedUmk)
    });
}

async function fetchEncryptedUmk(prf) {
    const encryptedUmkResponse = await fetch("/umk/get", {
        method: "GET",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        }
    });
    const encryptedUmk = await encryptedUmkResponse.json();
    await decryptUMK(encryptedUmk, prf);
}