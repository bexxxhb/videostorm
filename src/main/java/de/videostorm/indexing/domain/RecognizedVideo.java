package de.videostorm.indexing.domain;

import java.util.Locale;
import java.util.Set;

/**
 * The file extensions the catalogue treats as a feature video: {@code mkv}, {@code wmv},
 * {@code avi} and {@code mp4}. A folder is a movie only if it holds at least one of these.
 */
public final class RecognizedVideo {

    private static final Set<String> EXTENSIONS = Set.of("mkv", "wmv", "avi", "mp4");

    private RecognizedVideo() {
    }

    public static boolean isVideoFile(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXTENSIONS.contains(extension);
    }
}
