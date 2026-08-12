// Wires every ".plot-link" to one shared <dialog>. The plot travels in the link's data-plot
// attribute as base64 of UTF-8 bytes; we decode it in a UTF-8-aware way (atob is Latin-1 only)
// and insert it with textContent, so nothing in the plot can be interpreted as markup.
(function () {
    var dialog = document.getElementById("plot-dialog");
    if (!dialog) {
        return;
    }

    var titleEl = dialog.querySelector(".plot-dialog__title");
    var bodyEl = dialog.querySelector(".plot-dialog__body");

    function decodeUtf8Base64(base64) {
        var bytes = Uint8Array.from(atob(base64), function (char) {
            return char.charCodeAt(0);
        });
        return new TextDecoder().decode(bytes);
    }

    document.querySelectorAll(".plot-link").forEach(function (link) {
        link.addEventListener("click", function (event) {
            event.preventDefault();
            titleEl.textContent = link.getAttribute("data-title") || "";
            bodyEl.textContent = decodeUtf8Base64(link.getAttribute("data-plot") || "");
            dialog.showModal();
        });
    });

    dialog.querySelector(".plot-dialog__close").addEventListener("click", function () {
        dialog.close();
    });

    // A click that lands on the dialog element itself is a click on the backdrop, because the
    // panel (padding and all) covers the rest; Escape is handled natively by <dialog>.
    dialog.addEventListener("click", function (event) {
        if (event.target === dialog) {
            dialog.close();
        }
    });
})();
