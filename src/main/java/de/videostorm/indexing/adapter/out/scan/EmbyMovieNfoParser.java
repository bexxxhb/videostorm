package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.domain.ParsedMovie;
import de.videostorm.indexing.domain.ParsedRating;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an Emby movie {@code .nfo} into a {@link ParsedMovie}. This is an adapter concern: it lives
 * beside the filesystem scan and keeps the XML technology (JAXP/DOM) out of the domain, which only
 * ever sees the resulting value objects.
 *
 * <p>Field extraction is deliberately forgiving: a missing element becomes {@code null} and a number
 * that does not parse is dropped to {@code null}, so one bad field never discards the rest of the
 * entry. The reader supports DTDs — Emby occasionally writes an internal subset — but external entity
 * resolution is switched off, so a crafted {@code .nfo} can neither pull in an external DTD nor read a
 * file off the host through an entity reference.
 */
class EmbyMovieNfoParser {

    ParsedMovie parse(String xml) {
        Element root = documentRoot(xml);
        return new ParsedMovie(
                text(root, "title"),
                text(root, "originaltitle"),
                integer(root, "year"),
                ratings(root),
                genres(root),
                integer(root, "runtime"),
                text(root, "plot"),
                setName(root),
                collectionId(root),
                uniqueId(root, "imdb"),
                uniqueId(root, "tvdb"),
                uniqueId(root, "tmdb"));
    }

    private static Element documentRoot(String xml) {
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        } catch (Exception e) {
            throw new NfoParseException("Could not parse .nfo as XML", e);
        }
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setXIncludeAware(false);
        // DTDs stay allowed (disallow-doctype-decl is left false), but nothing external is ever
        // fetched or resolved: no external DTD subset, no external general or parameter entities.
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private List<ParsedRating> ratings(Element root) {
        List<ParsedRating> ratings = new ArrayList<>();
        for (Element ratings0 : children(root, "ratings")) {
            for (Element rating : children(ratings0, "rating")) {
                ratings.add(new ParsedRating(
                        attribute(rating, "name"),
                        decimal(text(rating, "value")),
                        decimal(rating.getAttribute("max")),
                        integer(text(rating, "votes")),
                        Boolean.parseBoolean(rating.getAttribute("default"))));
            }
        }
        return ratings;
    }

    private List<String> genres(Element root) {
        List<String> genres = new ArrayList<>();
        for (Element genre : children(root, "genre")) {
            String value = textContent(genre);
            if (value != null) {
                genres.add(value);
            }
        }
        return genres;
    }

    private static String setName(Element root) {
        Element set = firstChild(root, "set");
        if (set == null) {
            return null;
        }
        String named = text(set, "name");
        return named != null ? named : textContent(set);
    }

    private static String collectionId(Element root) {
        Element set = firstChild(root, "set");
        return set == null ? null : text(set, "tmdbcolid");
    }

    private static String uniqueId(Element root, String type) {
        for (Element id : children(root, "uniqueid")) {
            if (type.equalsIgnoreCase(id.getAttribute("type"))) {
                return textContent(id);
            }
        }
        return null;
    }

    private static Integer integer(Element root, String tag) {
        return integer(text(root, tag));
    }

    private static Integer integer(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String attribute(Element element, String name) {
        String value = element.getAttribute(name);
        return value.isBlank() ? null : value.trim();
    }

    private static String text(Element parent, String tag) {
        return textContent(firstChild(parent, tag));
    }

    private static String textContent(Element element) {
        if (element == null) {
            return null;
        }
        String value = element.getTextContent();
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Element firstChild(Element parent, String tag) {
        List<Element> children = children(parent, tag);
        return children.isEmpty() ? null : children.get(0);
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> matches = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tag)) {
                matches.add((Element) node);
            }
        }
        return matches;
    }

    /** Thrown when a {@code .nfo} is not well-formed XML; a later ticket treats this as absent. */
    static class NfoParseException extends RuntimeException {
        NfoParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
