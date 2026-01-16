// Doctor Dashboard JavaScript

let selectedPatientId = null;
let selectedFileId = null;
let selectedFileName = null;

function loadDoctorDashboard() {
    loadDoctorInfo();
    loadPatients();
    loadChangeRequests();
}

function showMessage(msg, isError) {
    const area = document.getElementById('messageArea');
    const cssClass = isError ? 'error-msg' : 'success-msg';
    area.innerHTML = '<div class="' + cssClass + '">' + escapeHtml(msg) + '</div>';
    setTimeout(function() { 
        area.innerHTML = ''; 
    }, 5000);
}

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

async function loadPatients() {
    try {
        const response = await fetch('/doctor/api/patients');
        const container = document.getElementById('patientList');

        if (response.ok) {
            const patients = await response.json();
            if (patients.length === 0) {
                container.innerHTML = '<div class="empty-state">No patients found in your organization</div>';
                return;
            }

            let html = '';
            for (let i = 0; i < patients.length; i++) {
                const p = patients[i];
                html += '<div class="patient-item" data-id="' + p.id + '" onclick="selectPatient(' + p.id + ', \'' + escapeHtml(p.username).replace(/'/g, "\\'") + '\')">';
                html += '<div>';
                html += '<strong>' + escapeHtml(p.username) + '</strong>';
                html += '<div style="font-size: 0.85em; color: #666;">' + p.fileCount + ' file(s)</div>';
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

async function selectPatient(patientId, patientName) {
    selectedPatientId = patientId;

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
    document.getElementById('selectedPatientName').textContent = patientName;
    document.getElementById('fileList').innerHTML = '<div class="loading">Loading files...</div>';

    try {
        const response = await fetch('/doctor/api/patients/' + patientId + '/files');
        const container = document.getElementById('fileList');

        if (response.ok) {
            const files = await response.json();
            if (files.length === 0) {
                container.innerHTML = '<div class="empty-state">No files found for this patient</div>';
                return;
            }

            let html = '';
            for (let i = 0; i < files.length; i++) {
                const f = files[i];
                const escapedName = escapeHtml(f.fileName).replace(/'/g, "\\'");
                html += '<div class="file-item">';
                html += '<div>';
                html += '<strong>' + escapeHtml(f.fileName) + '</strong>';
                html += '<div style="font-size: 0.85em; color: #666;">';
                html += formatSize(f.size) + ' - Uploaded ' + formatDate(f.uploadDate);
                html += '</div>';
                html += '</div>';
                html += '<div class="file-actions">';
                html += '<button class="btn btn-primary" onclick="downloadFile(' + patientId + ', ' + f.id + ', \'' + escapedName + '\')">Download</button>';
                html += '<button class="btn btn-secondary" onclick="openChangeRequestModal(' + f.id + ', \'' + escapedName + '\')">Request Change</button>';
                html += '<button class="btn btn-danger" onclick="deleteFile(' + patientId + ', ' + f.id + ', \'' + escapedName + '\')">Delete</button>';
                html += '</div>';
                html += '</div>';
            }
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="error-msg">Failed to load files</div>';
        }
    } catch (e) {
        console.error('Failed to load files:', e);
        document.getElementById('fileList').innerHTML = '<div class="error-msg">Error loading files</div>';
    }
}

async function downloadFile(patientId, fileId, fileName) {
    try {
        const response = await fetch('/doctor/api/patients/' + patientId + '/files/' + fileId);
        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
        } else {
            showMessage('Failed to download file', true);
        }
    } catch (e) {
        console.error('Download failed:', e);
        showMessage('Error downloading file', true);
    }
}

async function deleteFile(patientId, fileId, fileName) {
    if (!confirm('Are you sure you want to delete "' + fileName + '"? This action cannot be undone.')) {
        return;
    }

    try {
        const response = await fetch('/doctor/api/patients/' + patientId + '/files/' + fileId, {
            method: 'DELETE'
        });

        if (response.ok) {
            showMessage('File deleted successfully', false);
            const patientName = document.getElementById('selectedPatientName').textContent;
            selectPatient(patientId, patientName);
        } else {
            const error = await response.json();
            showMessage(error.error || 'Failed to delete file', true);
        }
    } catch (e) {
        console.error('Delete failed:', e);
        showMessage('Error deleting file', true);
    }
}

function openChangeRequestModal(fileId, fileName) {
    selectedFileId = fileId;
    selectedFileName = fileName;
    document.getElementById('modalFileName').textContent = fileName;
    document.getElementById('changeDescription').value = '';
    document.getElementById('changeRequestModal').classList.add('active');
}

function closeModal() {
    document.getElementById('changeRequestModal').classList.remove('active');
    selectedFileId = null;
    selectedFileName = null;
}

async function submitChangeRequest() {
    const description = document.getElementById('changeDescription').value.trim();
    if (!description) {
        alert('Please enter a description of the requested changes');
        return;
    }

    try {
        const response = await fetch('/doctor/api/patients/' + selectedPatientId + '/files/' + selectedFileId + '/change-request', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ description: description })
        });

        if (response.ok) {
            closeModal();
            showMessage('Change request submitted successfully', false);
            loadChangeRequests();
        } else {
            const error = await response.json();
            showMessage(error.error || 'Failed to submit change request', true);
        }
    } catch (e) {
        console.error('Submit failed:', e);
        showMessage('Error submitting change request', true);
    }
}

async function loadChangeRequests() {
    try {
        const response = await fetch('/doctor/api/change-requests');
        const container = document.getElementById('changeRequestList');

        if (response.ok) {
            const requests = await response.json();
            if (requests.length === 0) {
                container.innerHTML = '<div class="empty-state">No change requests yet</div>';
                return;
            }

            let html = '';
            for (let i = 0; i < requests.length; i++) {
                const r = requests[i];
                html += '<div class="file-item">';
                html += '<div>';
                html += '<strong>' + escapeHtml(r.fileName) + '</strong>';
                html += '<div style="font-size: 0.85em; color: #666;">';
                html += 'Status: ' + r.status + ' - ' + formatDate(r.createdAt);
                html += '</div>';
                html += '<div style="font-size: 0.85em; margin-top: 5px;">' + escapeHtml(r.description) + '</div>';
                html += '</div>';
                html += '</div>';
            }
            container.innerHTML = html;
        } else {
            container.innerHTML = '<div class="error-msg">Failed to load change requests</div>';
        }
    } catch (e) {
        console.error('Failed to load change requests:', e);
        document.getElementById('changeRequestList').innerHTML = '<div class="error-msg">Error loading change requests</div>';
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function formatDate(dateStr) {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleDateString();
}
