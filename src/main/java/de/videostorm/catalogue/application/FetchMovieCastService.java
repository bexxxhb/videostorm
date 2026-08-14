package de.videostorm.catalogue.application;

import de.videostorm.catalogue.application.port.in.MovieCastQuery;
import de.videostorm.catalogue.application.port.out.MovieRepository;
import de.videostorm.catalogue.domain.CastMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class FetchMovieCastService implements MovieCastQuery {

    private final MovieRepository movieRepository;

    @Override
    public Optional<List<CastMember>> castFor(long id) {
        return movieRepository.findCast(id);
    }
}
