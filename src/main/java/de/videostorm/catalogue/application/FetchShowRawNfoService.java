package de.videostorm.catalogue.application;

import de.videostorm.catalogue.application.port.in.ShowRawNfoQuery;
import de.videostorm.catalogue.application.port.out.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
class FetchShowRawNfoService implements ShowRawNfoQuery {

    private final ShowRepository showRepository;

    @Override
    public Optional<String> rawNfoFor(long id) {
        // Present exactly when raw_nfo is non-null — the same predicate the listing's hasRawNfo flag
        // uses — so a rendered "Raw data" link never resolves to a 404.
        return showRepository.findRawNfo(id);
    }
}
