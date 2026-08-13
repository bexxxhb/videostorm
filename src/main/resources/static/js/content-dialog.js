// Wires the "Plot" and "Raw data" links to one shared <dialog>. Each payload travels in the link's
// data attribute as base64 of UTF-8 bytes; we decode it in a UTF-8-aware way (atob is Latin-1 only)
// and insert it with textContent, so nothing in the payload can be interpreted as markup.
(function () {
    var dialog = document.getElementById("content-dialog");
    if (!dialog) {
        return;
    }

    var titleEl = dialog.querySelector(".content-dialog__title");
    var bodyEl = dialog.querySelector(".content-dialog__body");

    function decodeUtf8Base64(base64) {
        var bytes = Uint8Array.from(atob(base64), function (char) {
            return char.charCodeAt(0);
        });
        return new TextDecoder().decode(bytes);
    }

    // Wires one class of links to the shared dialog. `dataAttr` names the attribute carrying the
    // base64 payload; `code` renders it monospace with whitespace preserved (raw .nfo XML) and widens
    // the dialog, rather than showing it as wrapped prose (the plot).
    function wire(linkSelector, dataAttr, code) {
        document.querySelectorAll(linkSelector).forEach(function (link) {
            link.addEventListener("click", function (event) {
                event.preventDefault();
                titleEl.textContent = link.getAttribute("data-title") || "";
                bodyEl.textContent = decodeUtf8Base64(link.getAttribute(dataAttr) || "");
                bodyEl.classList.toggle("content-dialog__body--code", code);
                dialog.classList.toggle("content-dialog--wide", code);
                dialog.showModal();
            });
        });
    }

    wire(".plot-link", "data-plot", false);
    wire(".rawnfo-link", "data-rawnfo", true);

    dialog.querySelector(".content-dialog__close").addEventListener("click", function () {
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
