// Wires the "Plot" and "Raw data" links to one shared <dialog>. The plot travels inline in the link's
// data-plot attribute as base64 of UTF-8 bytes (small, always present); the raw .nfo is fetched from
// the link's href on demand, so listing pages never carry every file. Both are inserted with
// textContent, so nothing in the payload can be interpreted as markup.
(function () {
    var dialog = document.getElementById("content-dialog");
    if (!dialog) {
        return;
    }

    var titleEl = dialog.querySelector(".content-dialog__title");
    var bodyEl = dialog.querySelector(".content-dialog__body");
    // Guards against a slow fetch overwriting the body after the user has opened something else.
    var requestToken = 0;

    function decodeUtf8Base64(base64) {
        var bytes = Uint8Array.from(atob(base64), function (char) {
            return char.charCodeAt(0);
        });
        return new TextDecoder().decode(bytes);
    }

    // `code` renders the body monospace with whitespace preserved (raw .nfo XML) and widens the
    // dialog, rather than showing it as wrapped prose (the plot).
    function open(title, code) {
        requestToken++;
        titleEl.textContent = title || "";
        bodyEl.classList.toggle("content-dialog__body--code", code);
        dialog.classList.toggle("content-dialog--wide", code);
        dialog.showModal();
        return requestToken;
    }

    document.querySelectorAll(".plot-link").forEach(function (link) {
        link.addEventListener("click", function (event) {
            event.preventDefault();
            open(link.getAttribute("data-title"), false);
            bodyEl.textContent = decodeUtf8Base64(link.getAttribute("data-plot") || "");
        });
    });

    document.querySelectorAll(".rawnfo-link").forEach(function (link) {
        link.addEventListener("click", function (event) {
            event.preventDefault();
            var token = open(link.getAttribute("data-title"), true);
            bodyEl.textContent = "Loading…";
            fetch(link.href, {headers: {"Accept": "text/plain"}})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("status " + response.status);
                    }
                    return response.text();
                })
                .then(function (text) {
                    if (token === requestToken) {
                        bodyEl.textContent = text;
                    }
                })
                .catch(function () {
                    if (token === requestToken) {
                        bodyEl.textContent = "Could not load the raw .nfo data.";
                    }
                });
        });
    });

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
