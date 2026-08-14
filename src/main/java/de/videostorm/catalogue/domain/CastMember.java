package de.videostorm.catalogue.domain;

import java.util.Optional;

/**
 * One performer in a title's cast, read on demand for the "Actors" layer. {@code name} is always
 * present (a nameless {@code <actor>} is never stored); {@code role} and {@code thumbUrl} are empty
 * when the source file omitted them. The list is read top-billed first.
 */
public record CastMember(String name, Optional<String> role, Optional<String> thumbUrl) {
}
