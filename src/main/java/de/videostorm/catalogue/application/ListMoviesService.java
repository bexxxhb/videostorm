package de.videostorm.catalogue.application;

import de.videostorm.catalogue.application.port.in.ListMoviesQuery;
import de.videostorm.catalogue.application.port.out.MovieRepository;
import de.videostorm.catalogue.domain.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ListMoviesService implements ListMoviesQuery {

    static final int PAGE_SIZE = 50;

    private final MovieRepository movieRepository;

    @Override
    public MoviePage list(int requestedPage) {
        long totalElements = movieRepository.count();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / PAGE_SIZE);
        int pageNumber = clamp(requestedPage, totalPages);
        List<Movie> movies = movieRepository.findPage(pageNumber, PAGE_SIZE);
        return new MoviePage(movies, pageNumber, totalPages, totalElements);
    }

    private static int clamp(int requestedPage, int totalPages) {
        if (requestedPage < 1) {
            return 1;
        }
        return Math.min(requestedPage, totalPages);
    }
}
