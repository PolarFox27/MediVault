let DOB = null;
let FULL_NAME = null;


// Show/Hide the new key registration box
function toggleRegistration() {
    const temp = document.getElementById('registrationBox').style.display;
    document.getElementById('registrationBox').style.display = document.getElementById('keyListBox').style.display;
    document.getElementById('keyListBox').style.display = temp;
}

// Call the DELETE key endpoint to forget the authentication key provided
function deleteKey(credentialId){
    fetch(`/webauthn/remove?credentialId=${encodeURIComponent(credentialId)}`, {
        method: "DELETE",
        credentials: "same-origin"
    })
        .then(() => {
            navigate("/patient/account");
        })
        .catch((error) => {
            displayError(error);
        });
}

// Bind the deleteKey function to the delete buttons in the key list
document.addEventListener("click", e => {
    const btn = e.target.closest(".delete-btn");
    if (!btn) return;

    showConfirm("Are you sure you want to delete this key?", "Yes", "No", () => {
        deleteKey(btn.dataset.credentialId);
    });
});


// Performs the full key registration procedure based on the given form data (the credential name)
async function accountManagement() {
    const newCredentials = await registerKey("/webauthn/newkey");
    console.log(newCredentials);
    showConfirm("Confirm this security key as an additional encryption method ?", "Yes", "", () => {
        sendEncryptedUmk(newCredentials.credentialId);
    })
    navigate("/patient/account");
}

// Save the full name and DOB of the patient
async function savePersonalDetails() {
    const nameInput = document.getElementById("name");
    const dobInput = document.getElementById("dob");

    if(nameInput.value === "" || dobInput.value === "") {
        showConfirm("Please fill all the fields before saving.", "Ok", "", () => {});
        return;
    }

    const encryptedDob = await encrypt(stringToUint8Array(dobInput.value));
    const encryptedName = await encrypt(stringToUint8Array(nameInput.value));
    const request = {
        dob: uint8ArrayToHex(encryptedDob.encryptedData),
        dobIv: uint8ArrayToHex(encryptedDob.iv),
        name: uint8ArrayToHex(encryptedName.encryptedData),
        nameIv: uint8ArrayToHex(encryptedName.iv)
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

    DOB = uint8ArrayToString(await decrypt(hexToUint8Array(result.dob), hexToUint8Array(result.dobIv)));
    FULL_NAME = uint8ArrayToString(await decrypt(hexToUint8Array(result.name), hexToUint8Array(result.nameIv)));
}

function loadPersonalDetails() {
    document.getElementById("name").value = FULL_NAME;
    document.getElementById("dob").value = DOB;
    loadWelcomeMessage();
}

function loadWelcomeMessage(){
    document.getElementById("welcome-message").textContent = "Welcome " + FULL_NAME;
}