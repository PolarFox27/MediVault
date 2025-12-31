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
async function registerNewKey() {
    const newCredentials = await registerKey("/webauthn/newkey");
    console.log(newCredentials);
    showConfirm("Confirm this security key as an additional encryption method ?", "Yes", "", () => {
        sendEncryptedUmk(newCredentials.credentialId);
    })
    navigate("/patient/account");
}

// Save the full name and DOB of the patient
function savePersonalDetails() {
    const nameInput = document.getElementById("name");
    const dobInput = document.getElementById("dob");

    if(nameInput.value === "" || dobInput.value === "") {
        showConfirm("Please fill all the fields before saving.", "Ok", "", () => {});
        return;
    }
    // TODO
}