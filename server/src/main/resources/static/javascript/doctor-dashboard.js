// Doctor Dashboard JavaScript
let PATIENTS = []
let SELECTED_PATIENT_FILES = [];

let selectedPatientId = null;

/**
 * Loads the doctor information and appointed patients, then updates the HTML accordingly
 */
function loadDoctorDashboard() {
    loadDoctorInfo();
    loadPatients();
}

/**
 * Show an (error) message on the screen
 *
 * @param msg
 * @param isError
 */
function showMessage(msg, isError) {
    const area = document.getElementById('messageArea');
    const cssClass = isError ? 'error-msg' : 'success-msg';
    area.innerHTML = '<div class="' + cssClass + '">' + escapeHtml(msg) + '</div>';
    setTimeout(function() { 
        area.innerHTML = ''; 
    }, 5000);
}

/**
 * Loads the doctor personal information from the server and loads it in the HTML.
 *
 * @returns {Promise<void>}
 */
async function loadDoctorInfo() {
    try {
        const response = await fetch('/doctor/api/me');
        if (response.ok) {
            const doctor = await response.json();
            document.getElementById('doctorName').textContent = doctor.fullName;
            document.getElementById('doctorOrg').textContent = 'Organization: ' + doctor.organization;
            document.getElementById('certSerial').textContent = doctor.certificateSerial;
            // Update topbar with doctor name
            const topbarName = document.getElementById('topbar-doctor-name');
            if (topbarName) {
                topbarName.textContent = doctor.fullName;
            }
        }
    } catch (e) {
        console.error('Failed to load doctor info:', e);
    }
}

/**
 * Loads the appointed patients from the server and decrypts their personal information using the doctor private key
 * Updates the UI to show the list of patients.
 *
 * @returns {Promise<void>}
 */
async function loadPatients() {
    try {
        const response = await fetch('/doctor/api/patients');
        const container = document.getElementById('patientList');

        if (response.ok) {
            const patients = await response.json();
            if (patients.length === 0) {
                container.innerHTML = '<div class="empty-state">No appointed patients</div>';
                return;
            }


            let html = '';
            PATIENTS = [];
            for (let i = 0; i < patients.length; i++) {
                const p = patients[i];
                const fek = await rsaDecrypt(DOCTOR_PRIVATE_KEY, hexToUint8Array(p.fek));
                const dob = uint8ArrayToString(await decrypt(hexToUint8Array(p.dob), hexToUint8Array(p.dobIv), fek));
                const name = uint8ArrayToString(await decrypt(hexToUint8Array(p.name), hexToUint8Array(p.nameIv), fek));
                PATIENTS.push({
                    id: p.id,
                    name: name,
                    dob: dob,
                    fek: fek
                });

                html += `<div class="patient-item" data-id=${p.id} onclick="selectPatient(${p.id})">`;
                html += '<div>';
                html += '<strong>' + escapeHtml(name) + '</strong>';
                html += '<div style="font-size: 0.85em; color: #666;">' + dob + '</div>';
                html += '</div>';
                html += '<span style="color: #1976d2;">View Files</span>';
                html += '</div>';
            }
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="error-msg">Failed to load patients</div>';
        }
    } catch (e) {
        console.error('Failed to load patients:', e);
        document.getElementById('patientList').innerHTML = '<div class="error-msg">Error loading patients</div>';
    }
}

/**
 * Decrypt a file DTO from the server into a usable file object with decrypted attributes
 *
 * @param file
 * @returns {Promise<{id: *, filename: string, updatedAt: string, fek: Uint8Array<ArrayBufferLike> | Uint8Array<ArrayBuffer>}>}
 */
async function decryptFileForDoctor(file) {
    const fek = await rsaDecrypt(DOCTOR_PRIVATE_KEY, hexToUint8Array(file.fek));
    const name = await decrypt(hexToUint8Array(file.name), hexToUint8Array(file.nameIv), fek);

    return {
        id: file.id,
        filename: uint8ArrayToString(name),
        updatedAt: new Date(file.updatedAt).toLocaleString(),
        fek: fek
    }
}

/**
 * Create an HTML object to represent the given file object
 *
 * @param file
 * @returns {HTMLDivElement}
 */
function renderFileForDoctor(file) {
    // Create div
    const div = document.createElement("div");
    div.classList.add("file-item", "centered-container",  "gap-25", "key-row", "space-between", "fill-width");

    // Store ID
    div.dataset.fileId = file.id;

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
        </div>
    `;

    // Handler for the download buttons
    div.addEventListener("click", (e) => {

        const fileId = div.dataset.fileId;

        if (e.target.closest(".file-download-button")) {
            downloadFileForDoctor(selectedPatientId, fileId).then(() => {});
        }
    });

    return div;
}

/**
 * Executed when a patient is clicked in the UI, the medical record of the patient is fetched and rendered.
 *
 * @param patientId
 * @returns {Promise<void>}
 */
async function selectPatient(patientId) {
    selectedPatientId = patientId;
    const name = PATIENTS.find(p => p.id === patientId)?.name;

    // Update selection UI
    const items = document.querySelectorAll('.patient-item');
    for (let i = 0; i < items.length; i++) {
        items[i].classList.remove('selected');
    }
    const selected = document.querySelector('.patient-item[data-id="' + patientId + '"]');
    if (selected) {
        selected.classList.add('selected');
    }

    // Show file section
    document.getElementById('fileSection').style.display = 'block';
    document.getElementById('selectedPatientName').textContent = name;
    document.getElementById('fileList').innerHTML = '<div class="loading">Loading files...</div>';

    try {
        const response = await fetch('/doctor/api/patients/' + patientId + '/files');
        const container = document.getElementById('fileList');

        if (response.ok) {
            const files = await response.json();
            container.innerHTML = files.length === 0 ? "You have no files uploaded yet." : "";
            if (files.length === 0) {
                return;
            }

            SELECTED_PATIENT_FILES = [];
            console.log("Downloaded patient files:", files);
            for(const f of files) {
                const decrypted = await decryptFileForDoctor(f);
                SELECTED_PATIENT_FILES.push(decrypted);
                console.log(decrypted.filename);
                container.appendChild(renderFileForDoctor(decrypted));
            }

        } else {
            container.innerHTML = '<div class="error-msg">Failed to load files</div>';
        }
    } catch (e) {
        console.error('Failed to load files:', e);
        document.getElementById('fileList').innerHTML = '<div class="error-msg">Error loading files</div>';
    }
}

/**
 * Downloads the given file from the patient's medical record.
 * It is decrypted, then the browser asks to save it locally.
 *
 * @param patientId
 * @param fileId
 * @returns {Promise<void>}
 */
async function downloadFileForDoctor(patientId, fileId) {
    try {
        const file = SELECTED_PATIENT_FILES.find(f => String(f.id) === fileId);
        const response = await fetch('/doctor/api/patients/' + patientId + '/files/' + fileId, {
            method: 'GET',
            credentials: 'same-origin'
        });

        await checkStatus(response);
        const iv = hexToUint8Array(response.headers.get("X-File-IV"));
        const encryptedData = new Uint8Array(await response.arrayBuffer());
        const decryptedData = await decrypt(encryptedData, iv, file.fek);

        const blob = new Blob([decryptedData]);
        const url = URL.createObjectURL(blob);

        const a = document.createElement("a");
        a.href = url;
        a.download = file.filename;
        a.click();

        URL.revokeObjectURL(url);
    } catch (e) {
        console.error('Download failed:', e);
        showMessage('Error downloading file', true);
    }
}

/**
 * Create an HTML div tag wrapping the provided text
 *
 * @param text
 * @returns {string}
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Format a date object in a pretty string
 *
 * @param dateStr
 * @returns {string}
 */
function formatDate(dateStr) {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleDateString();
}
