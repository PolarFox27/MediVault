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

            tryLogin();
        });
        try {
            hcaptcha.execute(window.doctorCaptchaWidgetId);
        } catch (err) {
            console.warn('hcaptcha.execute failed, falling back to direct register()', err);

            tryLogin();
        }
    } else {
        console.debug('No hCaptcha available, calling register() directly');
        tryLogin();
    }
}


async function tryLogin() {
    const response = await fetch('/doctor/api/key');
    if (response.status === 404) {
        console.log("No Key Set");
        generateAndSendKeys();
    }
    else {
        console.log("Key Found");
    }
    navigate("/doctor/dashboard");
}

async function generateAndSendKeys() {
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
    const publicKey = new Uint8Array(publicKeyBuf);
    const privateKeyBuf = await crypto.subtle.exportKey("pkcs8", keyPair.privateKey);
    const privateKey = new Uint8Array(privateKeyBuf);

}