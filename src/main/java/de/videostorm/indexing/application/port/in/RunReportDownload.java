package de.videostorm.indexing.application.port.in;

/**
 * A run's issue detail packaged for download: the {@code filename} carries the run's timestamp so
 * successive downloads never overwrite each other, and {@code content} is the CSV bytes.
 */
public record RunReportDownload(String filename, byte[] content) {
}
