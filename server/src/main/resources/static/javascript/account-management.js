let DOB = null;
let FULL_NAME = null;


// Show/Hide the new key registration box
function toggleRegistration() {
    document.getElementById('registrationBox').classList.toggle("hidden");
    document.getElementById('keyListBox').classList.toggle("hidden");
}

// Call the DELETE key endpoint to forget the authentication key provided
function deleteKey(credentialId){
    fetch(`/webauthn/remove?credentialId=${encodeURIComponent(credentialId)}`, {
        method: "DELETE",
        credentials: "same-origin"
    })
        .then(() => {
            navigate("/patient/account", () => {}, false);
        })
        .catch((error) => {
            displayError(error);
        });
}

// Bind the deleteKey function to the delete buttons in the key list
document.addEventListener("click", e => {
    const btn = e.target.closest(".key-delete-button");
    if (!btn) return;

    showConfirm("Are you sure you want to delete this key?", "Yes", "No", () => {
        deleteKey(btn.dataset.credentialId);
    });
});


// Performs the full key registration procedure based on the given form data (the credential name)
async function registerNewKey() {
    const newCredentials = await registerKey("/webauthn/newkey");

    showConfirm("Confirm this security key as an additional encryption method ?", "Yes", "", () => {
        sendEncryptedUmk(newCredentials.credentialId);
    })
    navigate("/patient/account", () => {}, false);
}

// Save the full name and DOB of the patient
async function savePersonalDetails() {
    const nameInput = document.getElementById("name");
    const dobInput = document.getElementById("dob");

    if(nameInput.value === "" || dobInput.value === "") {
        showConfirm("Please fill all the fields before saving.", "Ok", "", () => {});
        return;
    }

    const fek = crypto.getRandomValues(new Uint8Array(32));
    const encryptedDob = await encrypt(stringToUint8Array(dobInput.value), fek);
    const encryptedName = await encrypt(stringToUint8Array(nameInput.value), fek);
    const encryptedFek = await encrypt(fek);
    const request = {
        dob: uint8ArrayToHex(encryptedDob.encryptedData),
        dobIv: uint8ArrayToHex(encryptedDob.iv),
        name: uint8ArrayToHex(encryptedName.encryptedData),
        nameIv: uint8ArrayToHex(encryptedName.iv),
        fek: uint8ArrayToHex(encryptedFek.encryptedData),
        fekIv: uint8ArrayToHex(encryptedFek.iv)
    }

    const response = await fetch("/patient/details", {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    });

    checkStatus(response);
    DOB = dobInput.value;
    FULL_NAME = nameInput.value;
    loadWelcomeMessage();
}

async function fetchPersonalDetails() {
    const response = await fetch("/patient/details", {
        method: "GET",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        }
    });

    const result = await initialCheckStatus(response);

    const fek = await decrypt(hexToUint8Array(result.fek), hexToUint8Array(result.fekIv));
    DOB = uint8ArrayToString(await decrypt(hexToUint8Array(result.dob), hexToUint8Array(result.dobIv), fek));
    FULL_NAME = uint8ArrayToString(await decrypt(hexToUint8Array(result.name), hexToUint8Array(result.nameIv), fek));
}

function loadPersonalDetails() {
    document.getElementById("name").value = FULL_NAME || '';
    document.getElementById("dob").value = DOB || '';
    loadWelcomeMessage();
}

function loadWelcomeMessage(){
    const welcomeEl = document.getElementById("welcome-message");
    if (welcomeEl && FULL_NAME) {
        welcomeEl.textContent = "Welcome " + FULL_NAME;
    } else if (welcomeEl) {
        welcomeEl.textContent = "Welcome";
    }
}