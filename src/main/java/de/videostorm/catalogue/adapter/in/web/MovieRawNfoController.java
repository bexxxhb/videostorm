package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.port.in.MovieRawNfoQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves a single movie's raw {@code .nfo} on demand, so the listing page never has to carry the
 * text for every row. Served as plain text (verbatim, never interpreted) and 404 when absent.
 */
@RestController
@RequiredArgsConstructor
public class MovieRawNfoController {

    private final MovieRawNfoQuery movieRawNfoQuery;

    @GetMapping(value = "/movies/{id}/nfo", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> rawNfo(@PathVariable long id) {
        return movieRawNfoQuery.rawNfoFor(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
