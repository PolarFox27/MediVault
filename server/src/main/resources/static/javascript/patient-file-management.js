function renderFile(file) {
    // Create div
    const div = document.createElement("div");
    div.classList.add("file-item", "centered-container",  "gap-25", "key-row", "space-between", "fill-width");

    // Store ID
    div.dataset.fileId = file.id;
    div.dataset.fek = uint8ArrayToHex(file.fek);
    div.dataset.filename = file.filename;

    // Add inner HTML content
    div.innerHTML = `
        <div class="centered-container gap-15">
            <div class="font-20 font-bold">${file.filename}</div>
        </div>
        
        <div class="centered-container gap-50">
            <button class="btn-with-icon tooltip-button">
                <div class="font-20">${file.updatedAt}</div>
                <span class="tooltip">Last Modification</span>
            </button>
            
            <button class="btn-with-icon tooltip-button file-download-button">
                <img src="/icons/download.png" alt="Download" class="icon width-30">
                <span class="tooltip">Download</span>
            </button>
            
            <button class="delete-btn btn-with-icon tooltip-button file-delete-button">
                <img src="/icons/delete.png" alt="Delete" class="icon delete-icon width-30">
                <span class="tooltip">Delete</span>
            </button>
        </div>
    `;

    // Handler for the download / delete buttons
    div.addEventListener("click", (e) => {

        const fileId = div.dataset.fileId;
        const filename = div.dataset.filename;
        const fek = hexToUint8Array(div.dataset.fek);

        if (e.target.closest(".file-download-button")) {
            downloadFile(fileId, filename, fek).then(() => {});
        }

        else if (e.target.closest(".file-delete-button")) {
            showConfirm("Are you sure you want to delete this file ? This operation cannot be undone.", "Yes", "No", () => {
                fetch(`/patient/files/${fileId}`, {
                    method: "DELETE",
                    credentials: "same-origin"
                })
                    .then(() => {
                        div.remove();
                    })
                    .catch((error) => {
                        displayError(error);
                    });
            })
        }
    });

    return div;
}

async function decryptFile(file) {
    const fek = await decrypt(hexToUint8Array(file.fek), hexToUint8Array(file.fekIv));
    const name = await decrypt(hexToUint8Array(file.name), hexToUint8Array(file.nameIv), fek);

    return {
        id: file.id,
        filename: uint8ArrayToString(name),
        updatedAt: new Date(file.updatedAt).toLocaleString(),
        fek: fek
    }
}

async function fetchAndRenderFiles(){
    let box = document.getElementById("files-box");

    const response = await fetch("/patient/files", {
        method: "GET",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json"
        }
    });
    const files = await initialCheckStatus(response);

    box.innerHTML = files.length === 0 ? "You have no files uploaded yet." : "";

    for(const f of files){
        box.appendChild(renderFile(await decryptFile(f)));
    }
}

function openFileInput(){
    const input = document.getElementById("uploadFileInput");
    if (!input) {
        console.error("Upload input not found!");
        return;
    }

    input.onchange = async () => {
        const file = input.files[0];
        if (!file) return;

        input.value = "";

        const existingFile = document.getElementById("files-box").querySelector(`[data-filename="${CSS.escape(file.name)}"]`);
        const id = existingFile ? existingFile.dataset.fileId : 0;

        const functionEnd = async () => {
            const plaintextFek = crypto.getRandomValues(new Uint8Array(32));
            const fek = await encrypt(plaintextFek);

            const fileBytes = new Uint8Array(await file.arrayBuffer());
            const data = await encrypt(fileBytes, plaintextFek);
            const filename = await encrypt(stringToUint8Array(file.name), plaintextFek);


            const form = new FormData();
            form.append("data", new Blob([data.encryptedData]));
            form.append("dataIv", new Blob([data.iv]));
            form.append("filename", new Blob([filename.encryptedData]));
            form.append("filenameIv", new Blob([filename.iv]));
            form.append("fek", new Blob([fek.encryptedData]));
            form.append("fekIv", new Blob([fek.iv]));

            const response = await fetch("/patient/files?id=" + id, {
                method: "POST",
                credentials: "same-origin",
                body: form
            });

            initialCheckStatus(response);
            await fetchAndRenderFiles();
        };

        if(id === 0) {
            await functionEnd();
        }
        else {
            showConfirm("A file with the same name already exists. Du you want to overwrite it ?", "Yes", "No", functionEnd);
        }
    }
    input.click();
}

async function downloadFile(fileId, filename, fek){
    const response = await fetch(`/patient/files/${fileId}`, {
        method: "GET",
        credentials: "same-origin"
    });

    checkStatus(response);
    const iv = hexToUint8Array(response.headers.get("X-File-IV"));
    const encryptedData = new Uint8Array(await response.arrayBuffer());
    const decryptedData = await decrypt(encryptedData, iv, fek);

    const blob = new Blob([decryptedData]);
    const url = URL.createObjectURL(blob);

    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();

    URL.revokeObjectURL(url);
}

/**
 * Fetch and display the list of available doctors.
 * Security: Uses authenticated session, receives only safe data via DTO.
 */
async function fetchDoctors() {
    try {
        const response = await fetch('/patient/api/doctors', {
            method: 'GET',
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            throw new Error('Failed to fetch doctors');
        }
        
        const doctors = await response.json();
        renderDoctorList(doctors);
    } catch (error) {
        console.error('Error fetching doctors:', error);
        const container = document.getElementById('doctors-list');
        if (container) {
            container.innerHTML = '<p class="no-doctors-text">Failed to load doctors</p>';
        }
    }
}

/**
 * Render the list of doctors in the UI.
 * @param {Array} doctors - Array of doctor objects with id, name, organization
 */
function renderDoctorList(doctors) {
    const container = document.getElementById('doctors-list');
    if (!container) return;
    
    if (doctors.length === 0) {
        container.innerHTML = '<p class="no-doctors-text">No doctors available</p>';
        return;
    }
    
    container.innerHTML = doctors.map(doctor => `
        <div class="doctor-card" data-doctor-id="${doctor.id}">
            <div class="doctor-info">
                <span class="doctor-name">${escapeHtml(doctor.name)}</span>
                <span class="doctor-organization">${escapeHtml(doctor.organization)}</span>
            </div>
            <button class="doctor-select-btn" onclick="selectDoctor(${doctor.id}, '${escapeHtml(doctor.name)}')">
                Select
            </button>
        </div>
    `).join('');
}

/**
 * Escape HTML to prevent XSS attacks.
 * @param {string} text - Text to escape
 * @returns {string} Escaped text
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Handle doctor selection (placeholder for future file sharing feature).
 * @param {number} doctorId - The selected doctor's ID
 * @param {string} doctorName - The selected doctor's name
 */
function selectDoctor(doctorId, doctorName) {
    // For now, just show a confirmation
    showConfirm(
        `You selected ${doctorName}. File sharing feature coming soon!`,
        'OK',
        'Cancel',
        () => {
            console.log('Selected doctor:', doctorId, doctorName);
        }
    );
}
