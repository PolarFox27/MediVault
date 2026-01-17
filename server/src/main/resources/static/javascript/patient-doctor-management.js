let DOCTORS = []
let APPOINTED_DOCTORS = []

/**
 * Fetch and display the list of available doctors.
 * Security: Uses authenticated session, receives only safe data via DTO.
 */
async function fetchDoctors() {
    try {
        const response1 = await fetch('/patient/api/appointed-doctors', {
            method: 'GET',
            credentials: 'same-origin'
        });

        const response2 = await fetch('/patient/api/doctors', {
            method: 'GET',
            credentials: 'same-origin'
        });

        APPOINTED_DOCTORS = await initialCheckStatus(response1);
        DOCTORS = await initialCheckStatus(response2);

        renderAppointedDoctorList(APPOINTED_DOCTORS);
        renderDoctorList(DOCTORS);
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
            <button class="doctor-select-btn" onclick="selectDoctor(${doctor.id})">
                Select
            </button>
        </div>
    `).join('');
}

/**
 * Render the list of doctors in the UI.
 * @param {Array} doctors - Array of doctor objects with id, name, organization
 */
function renderAppointedDoctorList(doctors) {
    const container = document.getElementById('appointed-doctors-list');
    if (!container) return;

    if (doctors.length === 0) {
        container.innerHTML = '<p class="no-doctors-text">No doctors appointed</p>';
        return;
    }

    container.innerHTML = doctors.map(doctor => `
        <div class="doctor-card" data-doctor-id="${doctor.id}">
            <div class="doctor-info">
                <span class="doctor-name">${escapeHtml(doctor.name)}</span>
                <span class="doctor-organization">${escapeHtml(doctor.organization)}</span>
            </div>
            <button class="delete-btn btn-with-icon tooltip-button" onclick="deleteDoctor(${doctor.id})">
                <img src="/icons/delete.png" alt="Delete" class="icon delete-icon width-30">
                <span class="tooltip">Delete</span>
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
 */
async function selectDoctor(doctorId) {
    const name = DOCTORS.find(d => d.id === doctorId)?.name;
    const publicKey = DOCTORS.find(d => d.id === doctorId)?.publicKey;

    const body = await Promise.all(
        FILES.map(async f => ({
            doctorId: f.id,
            encryptedFek: uint8ArrayToHex(
                await rsaEncrypt(hexToUint8Array(publicKey), f.fek)
            )
        }))
    );

    const body2 = [{
        doctorId: doctorId,
        encryptedFek: uint8ArrayToHex(
            await rsaEncrypt(hexToUint8Array(publicKey), DETAILS_FEK)
        )
    }];


    showConfirm(
        `Are you sure you want to appoint ${name} ? They will get access to your medical record.`,
        'OK',
        'Cancel',
        () => {
            fetch("/patient/api/doctors/" + doctorId, {
                method: 'PUT',
                credentials: 'same-origin',
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(body)
            }).then(() => fetchDoctors());

            fetch("/patient/details/fek", {
                method: 'PUT',
                credentials: 'same-origin',
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(body2)
            });
        }
    );
}

/**
 * Handle doctor deletion (placeholder for future file sharing feature).
 * @param {number} doctorId - The selected doctor's ID
 */
function deleteDoctor(doctorId) {
    const name = APPOINTED_DOCTORS.find(d => d.id === doctorId)?.name;
    showConfirm(
        `Are you sure you want to remove ${name} from your appointed doctors ? They will lose access to your medical record.`,
        'OK',
        'Cancel',
        () => {
            fetch("/patient/api/doctors/" + doctorId, {
                method: 'DELETE',
                credentials: 'same-origin'
            }).then(() => fetchDoctors());
        }
    );
}