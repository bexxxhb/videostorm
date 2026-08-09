package de.videostorm.maintenance.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The gated maintenance shell. The re-index triggers are rendered inert until indexing itself
 * is built.
 */
@Controller
public class MaintenanceController {

    @GetMapping("/maintenance")
    public String maintenance(HttpServletRequest request, Model model) {
        CsrfViewAttributes.exposeTo(request, model);

        return "maintenance";
    }
}
