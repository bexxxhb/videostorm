package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.domain.ParsedShow;
import org.w3c.dom.Element;

/**
 * Reads an Emby show {@code .nfo} (a {@code <tvshow>} document) into a {@link ParsedShow}. Like the
 * movie reader it is an adapter concern, keeping the XML technology out of the domain; the secure
 * parsing and the extractors shared with movies live in {@link EmbyNfo}. A show carries no runtime,
 * set or collection, and its year is not read here — the raw {@code <premiered>} date is carried
 * through for the domain to derive the year from.
 */
class EmbyShowNfoParser {

    ParsedShow parse(String xml) {
        Element root = EmbyNfo.documentRoot(xml, "tvshow");
        return new ParsedShow(
                EmbyNfo.text(root, "title"),
                EmbyNfo.text(root, "originaltitle"),
                EmbyNfo.text(root, "premiered"),
                EmbyNfo.ratings(root),
                EmbyNfo.genres(root),
                EmbyNfo.text(root, "plot"),
                EmbyNfo.text(root, "status"),
                EmbyNfo.uniqueId(root, "imdb"),
                EmbyNfo.uniqueId(root, "tvdb"),
                EmbyNfo.uniqueId(root, "tmdb"));
    }
}
