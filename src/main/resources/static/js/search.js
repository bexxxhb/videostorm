// Auto-searches the movies/shows listing as the user types, replacing only the #results container
// (pagination + table) instead of doing a full-page GET submit. Once the input reaches MIN_LENGTH
// characters, every further edit re-searches automatically — even one that drops the length back
// below MIN_LENGTH — for the rest of the page's lifetime (issue #57). The clear button and the
// "Search" button both search immediately, with no minimum length, via the same code path.
(function () {
    var MIN_LENGTH = 3;
    var DEBOUNCE_MS = 300;

    document.querySelectorAll(".search").forEach(function (form) {
        var input = form.querySelector(".search__input");
        var clearButton = form.querySelector(".search__clear");
        var results = document.getElementById("results");
        if (!input || !results) {
            return;
        }

        var debounceTimer = null;
        var hasReachedMinLength = false;

        function search() {
            var params = new URLSearchParams(new FormData(form));
            fetch(form.action + "?" + params.toString(), {
                headers: {"X-Requested-With": "XMLHttpRequest"}
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("status " + response.status);
                    }
                    return response.text();
                })
                .then(function (html) {
                    // Trusted, self-generated HTML from our own fragment endpoint, not user input.
                    results.innerHTML = html;
                })
                .catch(function () {
                    // Leave the current results in place; the "Search" button remains as a retry path.
                });
        }

        input.addEventListener("input", function () {
            if (input.value.length >= MIN_LENGTH) {
                hasReachedMinLength = true;
            }
            if (!hasReachedMinLength) {
                return;
            }
            window.clearTimeout(debounceTimer);
            debounceTimer = window.setTimeout(search, DEBOUNCE_MS);
        });

        if (clearButton) {
            clearButton.addEventListener("click", function () {
                window.clearTimeout(debounceTimer);
                input.value = "";
                input.focus();
                search();
            });
        }

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            window.clearTimeout(debounceTimer);
            search();
        });
    });
})();
