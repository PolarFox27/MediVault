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

// hCaptcha rendering and callbacks (moved from fragment so SPA injection works)
function renderCaptchas() {
    if (window.hcaptcha && document.getElementById('hcaptcha-login') && document.getElementById('hcaptcha-register')) {
        try {
            window.loginWidgetId = hcaptcha.render('hcaptcha-login', {sitekey: 'e2cc3850-5ae4-487a-a932-570a206ccebe', size: 'invisible', callback: onLoginCaptcha});
            window.registerWidgetId = hcaptcha.render('hcaptcha-register', {sitekey: 'e2cc3850-5ae4-487a-a932-570a206ccebe', size: 'invisible', callback: onRegisterCaptcha});
            console.debug('hCaptcha widgets rendered', window.loginWidgetId, window.registerWidgetId);
        } catch (err) {
            console.warn('hcaptcha.render failed', err);
        }
    } else {
        setTimeout(renderCaptchas, 100);
    }
}

function onLoginCaptcha(token) {
    const el = document.getElementById('loginCaptchaToken');
    if (el) el.value = token;
    document.dispatchEvent(new CustomEvent('hcaptcha-login-done'));
}

function onRegisterCaptcha(token) {
    const el = document.getElementById('registerCaptchaToken');
    if (el) el.value = token;
    document.dispatchEvent(new CustomEvent('hcaptcha-register-done'));
}

function triggerLoginWithCaptcha() {
    console.debug('triggerLoginWithCaptcha called');
    document.getElementById("button-login").textContent = 'Loading Captcha...';
    if (window.hcaptcha && typeof window.hcaptcha.execute === 'function' && typeof window.loginWidgetId !== 'undefined') {
        document.addEventListener('hcaptcha-login-done', function once() {
            document.removeEventListener('hcaptcha-login-done', once);
            document.getElementById("button-login").textContent = 'Login with Security Key';
            login();
        });
        try {
            hcaptcha.execute(window.loginWidgetId);
        } catch (err) {
            login();
        }
    } else {
        login();
    }
}

function triggerRegisterWithCaptcha() {
    console.debug('triggerRegisterWithCaptcha called');
    document.getElementById("button-register").textContent = 'Loading Captcha...';
    if (window.hcaptcha && typeof window.hcaptcha.execute === 'function' && typeof window.registerWidgetId !== 'undefined') {
        document.addEventListener('hcaptcha-register-done', function once() {
            document.getElementById("button-register").textContent = 'Register';
            document.removeEventListener('hcaptcha-register-done', once);
            register();
        });
        try {
            hcaptcha.execute(window.registerWidgetId);
        } catch (err) {
            console.warn('hcaptcha.execute failed, falling back to direct register()', err);
            register();
        }
    } else {
        console.debug('No hCaptcha available, calling register() directly');
        register();
    }
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
    // Safely read PRF extension result — it may be absent depending on the authenticator
    let prf = null;
    try {
        const ext = publicKeyCredential.getClientExtensionResults && publicKeyCredential.getClientExtensionResults();
        if (ext && ext.prf && ext.prf.results && ext.prf.results.first) {
            prf = ext.prf.results.first;
        } else {
            console.debug('login: PRF extension not present on credential, continuing without it', ext);
        }
    } catch (err) {
        console.warn('login: error reading client extension results', err);
    }

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
    const loginCaptchaToken = document.getElementById("loginCaptchaToken").value;
    newFormData.append("captchaToken", loginCaptchaToken);
    console.debug('login: sending /finish with captchaToken =', loginCaptchaToken ? 'present' : 'EMPTY');
    const newResponse = await fetch("/webauthn/login/finish", {
        method: 'POST',
        body: newFormData
    });

    CURRENT_CREDENTIALS = await initialCheckStatus(newResponse);
    if (prf) {
        try {
            await fetchEncryptedUmk(prf);
        } catch (err) {
            console.warn('fetchEncryptedUmk failed, continuing', err);
        }
        try {
            await fetchPersonalDetails();
        } catch (err) {
            console.warn('fetchPersonalDetails failed (decryption?), continuing', err);
        }
    } else {
        console.debug('No PRF available; skipping fetchEncryptedUmk and personal details decryption');
    }
    navigate("/patient/dashboard");
}


async function registerKey(baseUrl = "/webauthn/register") {
    this.form = document.getElementById("registerNewKeyForm");
    const formData = new FormData();
    formData.append("name", this.form.querySelector('[name="name"]').value);
    formData.append("dob", this.form.querySelector('[name="dob"]').value);
    formData.append("credname", this.form.querySelector('[name="credname"]').value);
    formData.append("captchaToken", document.getElementById("registerCaptchaToken").value);

    console.debug('registerKey: sending /start', { name: this.form.querySelector('[name="name"]').value, dob: this.form.querySelector('[name="dob"]').value, credname: this.form.querySelector('[name="credname"]').value, captcha: formData.get('captchaToken') });

    const response = await fetch(baseUrl + "/start", {
        method: 'POST',
        body: formData
    });

    console.debug('registerKey: /start response status', response.status);
    const credentialCreateJson = await initialCheckStatus(response);
    console.debug('registerKey: credentialCreateJson', credentialCreateJson);

    const credentialCreateOptions = {
        publicKey: {
            ...credentialCreateJson.publicKey,
            challenge: base64urlToUint8array(credentialCreateJson.publicKey.challenge),
            user: {
                ...credentialCreateJson.publicKey.user,
                id: base64urlToUint8array(credentialCreateJson.publicKey.user.id),
            },
            excludeCredentials: (credentialCreateJson.publicKey.excludeCredentials || []).map(credential => ({
                ...credential,
                id: base64urlToUint8array(credential.id),
            })),
            extensions: credentialCreateJson.publicKey.extensions,
        },
    };

    let publicKeyCredential;
    try {
        console.debug('registerKey: calling navigator.credentials.create');
        publicKeyCredential = await navigator.credentials.create(credentialCreateOptions);
        console.debug('registerKey: publicKeyCredential', publicKeyCredential);
    } catch (err) {
        console.error('registerKey: navigator.credentials.create failed', err);
        throw err;
    }

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
    console.debug('registerKey: sending /finish', { credentialPreview: encodedResult.type + ':' + encodedResult.id });

    const newResponse = await fetch(baseUrl + "/finish", {
        method: 'POST',
        body: formData
    });

    console.debug('registerKey: /finish response status', newResponse.status);
    return await initialCheckStatus(newResponse);
}


// Performs the full registration procedure based on the given form data
async function register() {
    console.debug('register() called');

    const nameInput = document.getElementById("name");
    const dobInput = document.getElementById("dob");
    const crednameInput = document.getElementById("credname");

    if(nameInput.value === "" || dobInput.value === "" || crednameInput.value === "") {
        showConfirm("Please fill all the fields.", "Ok", "", () => {});
        return;
    }

    try {
        CURRENT_CREDENTIALS = await registerKey("/webauthn/register");
        showConfirm("Confirm this key as encryption method.", "Yes", "", () => {
            sendEncryptedUmk(CURRENT_CREDENTIALS.credentialId).then(() => {
                savePersonalDetails().then(() => {
                    navigate("/patient/dashboard");
                });
            })
        });
    } catch (err) {
        console.error('registration failed', err);
        showConfirm('Registration failed. Check console for details.', 'Ok', '', () => {});
    }
}