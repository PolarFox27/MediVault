let DOCTOR_PRIVATE_KEY = new Uint8Array(0);

function renderDoctorCaptcha() {
    if (window.hcaptcha && document.getElementById('hcaptcha-doctor-login')) {
        try {
            window.doctorCaptchaWidgetId = hcaptcha.render('hcaptcha-doctor-login', {sitekey: 'e2cc3850-5ae4-487a-a932-570a206ccebe', size: 'invisible', callback: onDoctorLoginCaptcha});
            console.debug('hCaptcha widgets rendered', window.doctorCaptchaWidgetId);
        } catch (err) {
            console.warn('hcaptcha.render failed', err);
        }
    } else {
        setTimeout(renderDoctorCaptcha, 100);
    }
}

function onDoctorLoginCaptcha(token) {
    const el = document.getElementById('doctorLoginCaptchaToken');
    if (el) el.value = token;
    document.dispatchEvent(new CustomEvent('hcaptcha-doctor-done'));
}

function triggerDoctorLoginWithCaptcha() {
    const password = document.getElementById('doctorPassword').value;
    if(password.length < 8) {
        showConfirm("Password must be 8 characters long", "Ok", "", () => {});
        return;
    }

    document.getElementById("button-doctor-login").textContent = 'Loading Captcha...';
    if (window.hcaptcha && typeof window.hcaptcha.execute === 'function' && typeof window.doctorCaptchaWidgetId !== 'undefined') {
        document.addEventListener('hcaptcha-doctor-done', function once() {
            document.getElementById("button-doctor-login").textContent = 'Login';
            document.removeEventListener('hcaptcha-doctor-done', once);

            tryLogin(password);
        });
        try {
            hcaptcha.execute(window.doctorCaptchaWidgetId);
        } catch (err) {
            console.warn('hcaptcha.execute failed, falling back to direct register()', err);

            tryLogin(password);
        }
    } else {
        console.debug('No hCaptcha available, calling register() directly');
        tryLogin(password);
    }
}


async function tryLogin(password) {
    const response = await fetch('/doctor/api/key');
    if (response.status === 404) {
        await generateAndSendKeys(password);
        navigate("/doctor/dashboard");
    }
    else if (response.ok) {
        try {
            const result = await response.json();
            const privateKey = hexToUint8Array(result.privateKey);
            const privateKeySalt = hexToUint8Array(result.privateKeySalt);
            const privateKeyIv = hexToUint8Array(result.privateKeyIv);

            UMK = await deriveKeyFromPasswordAndSalt(password, privateKeySalt);
            DOCTOR_PRIVATE_KEY = await decrypt(privateKey, privateKeyIv);
            navigate("/doctor/dashboard");
        }
        catch (e) {
            console.error(e);
            showConfirm("Invalid Password. Please try again.", "Ok", "", () => {});
        }
    }
}

async function deriveKeyFromPasswordAndSalt(password, salt) {
    const baseKey = await crypto.subtle.importKey(
        "raw",
        stringToUint8Array(password),
        "PBKDF2",
        false,
        ["deriveKey"]
    );

    const umk = await crypto.subtle.deriveKey(
        {
            name: "PBKDF2",
            salt,
            iterations: 150_000,
            hash: "SHA-256"
        },
        baseKey,
        { name: "AES-GCM", length: 256 },
        true,
        ["encrypt", "decrypt"]
    );

    const umkBuf = await crypto.subtle.exportKey("raw", umk);
    return new Uint8Array(umkBuf);
}

async function generateAndSendKeys(password) {
    const keyPair = await crypto.subtle.generateKey(
        {
            name: "RSA-OAEP",
            modulusLength: 2048,
            publicExponent: new Uint8Array([1, 0, 1]),
            hash: "SHA-256"
        },
        true,
        ["encrypt", "decrypt"]
    );

    const publicKeyBuf = await crypto.subtle.exportKey("spki", keyPair.publicKey);
    const privateKeyBuf = await crypto.subtle.exportKey("pkcs8", keyPair.privateKey);

    const publicKey = uint8ArrayToHex(new Uint8Array(publicKeyBuf));

    DOCTOR_PRIVATE_KEY = new Uint8Array(privateKeyBuf);
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const privateKeySalt = uint8ArrayToHex(salt);


    UMK = await deriveKeyFromPasswordAndSalt(password, salt);

    const ciphertext = await encrypt(DOCTOR_PRIVATE_KEY);

    const privateKey = uint8ArrayToHex(ciphertext.encryptedData);
    const privateKeyIv = uint8ArrayToHex(ciphertext.iv);

    await fetch("/doctor/api/key", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            publicKey,
            privateKey,
            privateKeyIv,
            privateKeySalt
        })
    });
}


async function rsaEncrypt(publicKey, data) {
    const key = await crypto.subtle.importKey(
        "spki",
        publicKey.buffer,
        {
            name: "RSA-OAEP",
            hash: "SHA-256",
        },
        false,
        ["encrypt"]
    );

    const encrypted = await crypto.subtle.encrypt(
        { name: "RSA-OAEP" },
        key,
        data
    );

    return new Uint8Array(encrypted);
}


async function rsaDecrypt(privateKey, encryptedData) {
    const key = await crypto.subtle.importKey(
        "pkcs8",
        privateKey.buffer,
        {
            name: "RSA-OAEP",
            hash: "SHA-256",
        },
        false,
        ["decrypt"]
    );

    const decrypted = await crypto.subtle.decrypt(
        { name: "RSA-OAEP" },
        key,
        encryptedData
    );

    return new Uint8Array(decrypted);
}