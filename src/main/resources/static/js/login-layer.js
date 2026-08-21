// Opens the sign-in form in the shared dialog layer instead of navigating to /login when the
// Maintenance link is clicked while signed out. The link keeps its real href so a client without
// JavaScript (or a direct request) still falls through to Spring Security's saved-request flow
// and the standalone /login page.
(function () {
    var trigger = document.getElementById("login-layer-trigger");
    var dialog = document.getElementById("login-layer");
    if (!trigger || !dialog) {
        return;
    }

    trigger.addEventListener("click", function (event) {
        event.preventDefault();
        dialog.showModal();
    });

    // A failed submission round-trips through the standalone /login?error page (Spring
    // Security's default failure handler always lands there, regardless of where the form was
    // submitted from), so the error message renders inert in this dialog until re-opened here.
    if (dialog.hasAttribute("data-login-failed")) {
        dialog.showModal();
    }

    dialog.querySelector(".content-dialog__close").addEventListener("click", function () {
        dialog.close();
    });

    dialog.addEventListener("click", function (event) {
        if (event.target === dialog) {
            dialog.close();
        }
    });
})();
