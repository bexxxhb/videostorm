package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.domain.CastMember;

/**
 * One actor as the "Actors" layer consumes it: JSON built client-side into a block. {@code role} and
 * {@code thumbUrl} serialize as JSON {@code null} when absent (which the client treats the same as a
 * missing field), and the client renders a "no image" placeholder for a null {@code thumbUrl}.
 *
 * <p>Stored TMDB thumbs are {@code http://image.tmdb.org/...}; served over HTTP they would be blocked
 * as mixed content when the app is served over HTTPS, so each is rewritten to {@code https://}.
 */
record ActorResponse(String name, String role, String thumbUrl) {

    static ActorResponse from(CastMember member) {
        return new ActorResponse(
                member.name(),
                member.role().orElse(null),
                member.thumbUrl().map(ActorResponse::toHttps).orElse(null));
    }

    private static String toHttps(String url) {
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
    }
}
