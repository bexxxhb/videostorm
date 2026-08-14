package de.videostorm.indexing.domain;

/**
 * One {@code <actor>} read from an Emby {@code .nfo}: the performer's name, the role they played, their
 * billing position and, where the file carries them, a portrait URL and the actor's TMDB person id. The
 * {@link #name} is the one field that must be present for an actor to count; {@link #role}, {@link #thumb}
 * and {@link #tmdbId} may be {@code null} when the file omits them. {@link #order} is the billing
 * position, taken from the file's {@code <order>} where present and otherwise the actor's document
 * position, so the cast can be shown top-billed first. Mirrors {@link ParsedRating}: a forgiving value
 * object the parser fills and the domain carries, one row per performer in the cast child table.
 */
public record ParsedActor(
        String name,
        String role,
        Integer order,
        String thumb,
        String tmdbId) {
}
