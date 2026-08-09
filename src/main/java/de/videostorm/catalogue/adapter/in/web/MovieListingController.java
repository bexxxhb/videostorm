package de.videostorm.catalogue.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public, unauthenticated entry point to the catalogue.
 *
 * <p>The listing is served entirely from the database — never from the filesystem. The read
 * path itself does not exist yet, so the table renders with no rows.
 */
@Controller
public class MovieListingController {

    @GetMapping({"/", "/movies"})
    public String listMovies() {
        return "movies";
    }
}
