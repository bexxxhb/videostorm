package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The gated maintenance shell. A type's re-index trigger is offered only when that type has
 * source paths configured; with none configured the trigger is disabled and explained rather
 * than dangling as a button that cannot possibly work. The configured path values themselves are
 * never placed in the model.
 */
@Controller
public class MaintenanceController {

    private final SourcePaths sourcePaths;

    public MaintenanceController(SourcePaths sourcePaths) {
        this.sourcePaths = sourcePaths;
    }

    @GetMapping("/maintenance")
    public String maintenance(HttpServletRequest request, Model model) {
        CsrfViewAttributes.exposeTo(request, model);
        model.addAttribute("moviesConfigured", sourcePaths.hasPathsFor(SourceType.MOVIES));
        model.addAttribute("showsConfigured", sourcePaths.hasPathsFor(SourceType.SHOWS));

        return "maintenance";
    }
}
