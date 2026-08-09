package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.MoviePage;
import de.videostorm.catalogue.application.port.in.ListMoviesQuery;
import de.videostorm.catalogue.domain.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Public, unauthenticated entry point to the catalogue.
 *
 * <p>The listing is served entirely from the database — never from the filesystem.
 */
@Controller
@RequiredArgsConstructor
public class MovieListingController {

    private final ListMoviesQuery listMoviesQuery;

    @GetMapping({"/", "/movies"})
    public String listMovies(@RequestParam(name = "page", defaultValue = "1") int page, Model model) {
        MoviePage moviePage = listMoviesQuery.list(page);

        List<Movie> movies = moviePage.movies();
        List<MovieRow> rows = IntStream.range(0, movies.size())
                .mapToObj(index -> MovieRow.from(movies.get(index), index))
                .toList();

        model.addAttribute("movies", rows);
        model.addAttribute("pageNumber", moviePage.pageNumber());
        model.addAttribute("totalPages", moviePage.totalPages());
        model.addAttribute("totalElements", moviePage.totalElements());
        model.addAttribute("pagination", PaginationLinks.from(moviePage));

        return "movies";
    }
}
