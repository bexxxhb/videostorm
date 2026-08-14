package de.videostorm.catalogue.adapter.out.persistence;

import de.videostorm.catalogue.domain.CastMember;

import java.util.Optional;

/**
 * Spring Data projection over the {@code movie_actor} / {@code show_actor} read query — just the three
 * fields the "Actors" layer shows. {@code role} and {@code thumb} are {@code null} when the source file
 * omitted them.
 */
interface CastRow {

    String getName();

    String getRole();

    String getThumb();

    /** Maps this row to its domain form, collapsing a blank or null role/thumb to an empty Optional. */
    default CastMember toCastMember() {
        return new CastMember(getName(), blankToEmpty(getRole()), blankToEmpty(getThumb()));
    }

    private static Optional<String> blankToEmpty(String value) {
        return Optional.ofNullable(value).filter(text -> !text.isBlank());
    }
}
