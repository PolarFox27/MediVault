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

async function generateAndEncryptUMK(credentialId) {
    const umk = crypto.getRandomValues(new Uint8Array(32));

    const prf = await generateWebAuthnPRF(hexToUint8Array(credentialId));

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
        await crypto.subtle.encrypt({ name: "AES-GCM", iv }, wrapKey, umk)
    );

    return {
        encryptedUmk: uint8ArrayToHex(encryptedUmk),
        iv: uint8ArrayToHex(iv),
        credentialId: credentialId
    };
}

// --- Decrypt UMK (login time) ---
async function decryptUMK({ encryptedUmk, iv, credentialId }) {

    const prf = await generateWebAuthnPRF(hexToUint8Array(credentialId));

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

    return new Uint8Array(
        await crypto.subtle.decrypt(
            {name: "AES-GCM", iv: hexToUint8Array(iv)},
            wrapKey,
            hexToUint8Array(encryptedUmk)
        )
    );
}