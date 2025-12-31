// Show the confirmation menu for the given message and callback
function showConfirm(message, okText, cancelText, onConfirm) {

    const overlay = document.getElementById("confirm-overlay");
    const msg = document.getElementById("confirm-message");
    const okBtn = document.getElementById("confirm-button");
    const cancelBtn = document.getElementById("cancel-button");

    msg.textContent = message;
    okBtn.textContent = okText;
    if(cancelText === "" || cancelText === null) {
        cancelBtn.classList.add("hidden");
    }
    else {
        cancelBtn.textContent = cancelText;
        cancelBtn.classList.remove("hidden");
    }

    overlay.style.display = "flex";

    const cleanup = () => {
        overlay.style.display = "none";
        okBtn.onclick = null;
        cancelBtn.onclick = null;
    };

    okBtn.onclick = () => {
        cleanup();
        onConfirm();
    };

    cancelBtn.onclick = cleanup;
}
