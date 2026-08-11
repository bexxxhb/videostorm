package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.domain.ParsedMovie;
import org.w3c.dom.Element;

/**
 * Reads an Emby movie {@code .nfo} into a {@link ParsedMovie}. This is an adapter concern: it lives
 * beside the filesystem scan and keeps the XML technology (JAXP/DOM) out of the domain, which only
 * ever sees the resulting value objects. The secure parsing and the extractors movies share with
 * shows live in {@link EmbyNfo}; only the movie-specific field mapping ({@code <set>}) is here.
 */
class EmbyMovieNfoParser {

    ParsedMovie parse(String xml) {
        Element root = EmbyNfo.documentRoot(xml, "movie");
        return new ParsedMovie(
                EmbyNfo.text(root, "title"),
                EmbyNfo.text(root, "originaltitle"),
                EmbyNfo.integer(root, "year"),
                EmbyNfo.ratings(root),
                EmbyNfo.genres(root),
                EmbyNfo.integer(root, "runtime"),
                EmbyNfo.text(root, "plot"),
                setName(root),
                collectionId(root),
                EmbyNfo.uniqueId(root, "imdb"),
                EmbyNfo.uniqueId(root, "tvdb"),
                EmbyNfo.uniqueId(root, "tmdb"));
    }

    private static String setName(Element root) {
        Element set = EmbyNfo.firstChild(root, "set");
        if (set == null) {
            return null;
        }
        String named = EmbyNfo.text(set, "name");
        return named != null ? named : EmbyNfo.textContent(set);
    }

    private static String collectionId(Element root) {
        Element set = EmbyNfo.firstChild(root, "set");
        return set == null ? null : EmbyNfo.text(set, "tmdbcolid");
    }
}
