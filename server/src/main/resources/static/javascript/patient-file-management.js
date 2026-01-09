function renderFile(file) {
    // Create div
    const div = document.createElement("div");
    div.classList.add("file-item", "centered-container",  "gap-25", "key-row", "space-between", "fill-width");

    // Store ID
    div.dataset.fileId = file.id;
    div.dataset.fek = file.fek;
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

        if (e.target.closest(".file-download-button")) {
            console.log("Download clicked for file:", fileId);
            // TODO: Download File
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

    input.onchange = async () => {
        const file = input.files[0];
        if (!file) return;

        input.value = "";

        const plaintextFek = crypto.getRandomValues(new Uint8Array(32));
        const fek = await encrypt(plaintextFek);

        const fileBytes = new Uint8Array(await file.arrayBuffer());
        const data = await encrypt(fileBytes, plaintextFek);
        const filename = await encrypt(stringToUint8Array(file.name), plaintextFek);


        const existingFile = document.getElementById("files-box").querySelector(`[data-filename="${CSS.escape(file.name)}"]`);
        const id = existingFile ? existingFile.dataset.fileId : 0;

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
    }

    input.click();
}
