package de.videostorm.indexing.domain;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The title a folder gets when its metadata offers none: the folder's own name, with a trailing media
 * extension stripped so a folder named after its file ({@code The Matrix.mkv}) reads as {@code The
 * Matrix}. Only recognised media extensions are removed, so a name like {@code Amelie.2001} or a
 * {@code (2014)} suffix survives untouched — the year is never taken from a filename.
 */
public final class DerivedTitle {

    // The recognised video extensions (kept in one place, in RecognizedVideo) plus the sidecar .nfo.
    private static final Set<String> STRIPPABLE_EXTENSIONS = strippableExtensions();

    private DerivedTitle() {
    }

    /** The effective title: the metadata title where present, else the folder-derived fallback. */
    public static String resolve(String metadataTitle, String folderName) {
        return metadataTitle != null ? metadataTitle : fromFolderName(folderName);
    }

    public static String fromFolderName(String folderName) {
        int dot = folderName.lastIndexOf('.');
        if (dot > 0) {
            String extension = folderName.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (STRIPPABLE_EXTENSIONS.contains(extension)) {
                return folderName.substring(0, dot);
            }
        }
        return folderName;
    }

    private static Set<String> strippableExtensions() {
        Set<String> extensions = new HashSet<>(RecognizedVideo.extensions());
        extensions.add("nfo");
        return Set.copyOf(extensions);
    }
}
