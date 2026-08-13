package de.videostorm.catalogue.application;

import de.videostorm.catalogue.application.port.in.MovieRawNfoQuery;
import de.videostorm.catalogue.application.port.out.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
class FetchMovieRawNfoService implements MovieRawNfoQuery {

    private final MovieRepository movieRepository;

    @Override
    public Optional<String> rawNfoFor(long id) {
        // Present exactly when raw_nfo is non-null — the same predicate the listing's hasRawNfo flag
        // uses — so a rendered "Raw data" link never resolves to a 404.
        return movieRepository.findRawNfo(id);
    }
}
