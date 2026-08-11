package de.videostorm.indexing.adapter.out.scan;

/**
 * Thrown when a {@code .nfo} is not well-formed XML, or is well-formed with the wrong root element.
 * The scan treats either case exactly as an absent file, deriving the title from the folder instead.
 */
class NfoParseException extends RuntimeException {

    NfoParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
