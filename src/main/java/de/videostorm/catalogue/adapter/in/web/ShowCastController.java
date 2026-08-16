package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.port.in.ShowCastQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves a single show's cast on demand, so the listing page never carries the performers for every
 * row. Returns a JSON array in billing order; 404 when the show is unknown, {@code []} when it has no
 * cast.
 */
@RestController
@RequiredArgsConstructor
public class ShowCastController {

    private final ShowCastQuery showCastQuery;

    @GetMapping(value = "/shows/{id}/actors", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ActorResponse>> actors(@PathVariable long id) {
        return showCastQuery.castFor(id)
                .map(cast -> ResponseEntity.ok(cast.stream().map(ActorResponse::from).toList()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
