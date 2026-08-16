package de.videostorm.catalogue.application;

import de.videostorm.catalogue.application.port.in.ShowCastQuery;
import de.videostorm.catalogue.application.port.out.ShowRepository;
import de.videostorm.catalogue.domain.CastMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class FetchShowCastService implements ShowCastQuery {

    private final ShowRepository showRepository;

    @Override
    public Optional<List<CastMember>> castFor(long id) {
        return showRepository.findCast(id);
    }
}
