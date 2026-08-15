// Wires the "Plot", "Raw data" and "Actors" links to one shared <dialog>. The plot travels inline in
// the link's data-plot attribute as base64 of UTF-8 bytes (small, always present); the raw .nfo and the
// cast are fetched from the link's href on demand, so listing pages never carry every file or performer.
// Text is inserted with textContent and images only via <img src>, so nothing in a payload can be
// interpreted as markup.
(function () {
    var dialog = document.getElementById("content-dialog");
    if (!dialog) {
        return;
    }

    // Bundled "no image" placeholder, shown at the same fixed size as a real thumbnail so the grid
    // stays aligned when an actor carries no portrait.
    var PLACEHOLDER_IMAGE = "/img/no-actor.svg";

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

    // `mode` selects the body layout: "code" renders monospace with whitespace preserved (raw .nfo XML),
    // "actors" renders the cast as a grid of blocks, "duplicates" lists duplicate groups, and anything
    // else is wrapped prose (the plot). The wider dialog is shared by the modes that need the room.
    function open(title, mode) {
        requestToken++;
        titleEl.textContent = title || "";
        bodyEl.classList.toggle("content-dialog__body--code", mode === "code");
        bodyEl.classList.toggle("content-dialog__body--actors", mode === "actors");
        bodyEl.classList.toggle("content-dialog__body--duplicates", mode === "duplicates");
        dialog.classList.toggle("content-dialog--wide", mode === "code" || mode === "actors" || mode === "duplicates");
        dialog.showModal();
        return requestToken;
    }

    // Builds one block per actor: the portrait (or the placeholder), the name below it, and the role
    // below the name when present. Every text node goes in via textContent; the only attacker-influenced
    // attribute is the image src, which an <img> can never execute.
    function renderActors(actors) {
        bodyEl.textContent = "";
        if (!actors.length) {
            bodyEl.textContent = "No cast recorded.";
            return;
        }
        actors.forEach(function (actor) {
            var card = document.createElement("div");
            card.className = "actor-card";

            var image = document.createElement("img");
            image.className = "actor-card__image";
            image.src = actor.thumbUrl || PLACEHOLDER_IMAGE;
            image.alt = actor.name || "";
            card.appendChild(image);

            var name = document.createElement("div");
            name.className = "actor-card__name";
            name.textContent = actor.name || "";
            card.appendChild(name);

            if (actor.role) {
                var role = document.createElement("div");
                role.className = "actor-card__role";
                role.textContent = actor.role;
                card.appendChild(role);
            }

            bodyEl.appendChild(card);
        });
    }

    // Builds one block per duplicate group: a heading naming the criterion and the value the movies
    // share, then a row per member listing its IMDb id, original title and file path. A missing
    // attribute shows as a dash. Every value goes in via textContent, so nothing in a payload is markup.
    function renderDuplicateGroups(groups) {
        bodyEl.textContent = "";
        if (!groups.length) {
            bodyEl.textContent = "No duplicates found.";
            return;
        }
        groups.forEach(function (group) {
            var block = document.createElement("div");
            block.className = "dup-group";

            var heading = document.createElement("div");
            heading.className = "dup-group__heading";
            heading.textContent = group.criterion + ": " + group.sharedValue;
            block.appendChild(heading);

            group.members.forEach(function (member) {
                var row = document.createElement("div");
                row.className = "dup-member";
                [member.imdbId, member.originalTitle, member.filePath].forEach(function (value) {
                    var cell = document.createElement("div");
                    cell.className = "dup-member__cell";
                    cell.textContent = value || "—";
                    row.appendChild(cell);
                });
                block.appendChild(row);
            });

            bodyEl.appendChild(block);
        });
    }

    document.querySelectorAll(".plot-link").forEach(function (link) {
        link.addEventListener("click", function (event) {
            event.preventDefault();
            open(link.getAttribute("data-title"), "prose");
            bodyEl.textContent = decodeUtf8Base64(link.getAttribute("data-plot") || "");
        });
    });

    document.querySelectorAll(".actors-link").forEach(function (link) {
        link.addEventListener("click", function (event) {
            event.preventDefault();
            var token = open(link.getAttribute("data-title"), "actors");
            bodyEl.textContent = "Loading…";
            fetch(link.href, {headers: {"Accept": "application/json"}})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("status " + response.status);
                    }
                    return response.json();
                })
                .then(function (actors) {
                    if (token === requestToken) {
                        renderActors(actors);
                    }
                })
                .catch(function () {
                    if (token === requestToken) {
                        bodyEl.textContent = "Could not load the cast.";
                    }
                });
        });
    });

    document.querySelectorAll(".duplicates-link").forEach(function (link) {
        link.addEventListener("click", function (event) {
            event.preventDefault();
            var token = open(link.getAttribute("data-title"), "duplicates");
            bodyEl.textContent = "Loading…";
            fetch(link.href, {headers: {"Accept": "application/json"}})
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("status " + response.status);
                    }
                    return response.json();
                })
                .then(function (groups) {
                    if (token === requestToken) {
                        renderDuplicateGroups(groups);
                    }
                })
                .catch(function () {
                    if (token === requestToken) {
                        bodyEl.textContent = "Could not load the duplicates.";
                    }
                });
        });
    });

    document.querySelectorAll(".rawnfo-link").forEach(function (link) {
        link.addEventListener("click", function (event) {
            event.preventDefault();
            var token = open(link.getAttribute("data-title"), "code");
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
