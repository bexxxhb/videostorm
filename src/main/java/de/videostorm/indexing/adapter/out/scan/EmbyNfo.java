package de.videostorm.indexing.adapter.out.scan;

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
 * The XML technology shared by the Emby {@code .nfo} readers: secure parsing plus the field
 * extractors that movies and shows have in common ({@code <ratings>}, {@code <genre>},
 * {@code <uniqueid>}, and the text/number helpers). Keeping JAXP/DOM here means the domain only ever
 * sees the resulting value objects, and the two type-specific readers stay thin.
 *
 * <p>Field extraction is deliberately forgiving: a missing element becomes {@code null} and a number
 * that does not parse is dropped to {@code null}, so one bad field never discards the rest of the
 * entry. The reader supports DTDs — Emby occasionally writes an internal subset — but external entity
 * resolution is switched off, so a crafted {@code .nfo} can neither pull in an external DTD nor read a
 * file off the host through an entity reference.
 */
final class EmbyNfo {

    private EmbyNfo() {
    }

    /** Parses {@code xml} and returns its root, rejecting a non-well-formed file or a wrong root. */
    static Element documentRoot(String xml, String expectedRoot) {
        Element root;
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
        } catch (Exception e) {
            throw new NfoParseException("Could not parse .nfo as XML", e);
        }
        if (root == null || !expectedRoot.equals(root.getNodeName())) {
            // Well-formed XML with the wrong root is as useless as an absent file; the scan treats
            // this exactly as one, deriving the title from the folder instead.
            throw new NfoParseException("Root element of .nfo is not <" + expectedRoot + ">", null);
        }
        return root;
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

    static List<ParsedRating> ratings(Element root) {
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

    static List<String> genres(Element root) {
        List<String> genres = new ArrayList<>();
        for (Element genre : children(root, "genre")) {
            String value = textContent(genre);
            if (value != null) {
                genres.add(value);
            }
        }
        return genres;
    }

    static String uniqueId(Element root, String type) {
        for (Element id : children(root, "uniqueid")) {
            if (type.equalsIgnoreCase(id.getAttribute("type"))) {
                return textContent(id);
            }
        }
        return null;
    }

    static Integer integer(Element root, String tag) {
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

    static String text(Element parent, String tag) {
        return textContent(firstChild(parent, tag));
    }

    static String textContent(Element element) {
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

    static Element firstChild(Element parent, String tag) {
        List<Element> children = children(parent, tag);
        return children.isEmpty() ? null : children.get(0);
    }

    static List<Element> children(Element parent, String tag) {
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
}
