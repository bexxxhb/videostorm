package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.application.port.out.LibraryScan;
import de.videostorm.indexing.domain.ScanReport;
import de.videostorm.sources.domain.SourceType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link LibraryScan} the run lifecycle sees: it holds one {@link SourceScan} per {@link
 * SourceType} and dispatches a run to the matching one. A type with no scanner reports {@link
 * ScanReport#none()}, mirroring the promotion side, so an unimplemented type is a no-op rather than a
 * failure. Because a scan is scoped to its own type's staging, re-indexing one type never touches the
 * other's.
 */
@Component
class RoutingLibraryScan implements LibraryScan {

    private final Map<SourceType, SourceScan> byType;

    RoutingLibraryScan(List<SourceScan> scans) {
        Map<SourceType, SourceScan> map = new EnumMap<>(SourceType.class);
        for (SourceScan scan : scans) {
            map.put(scan.type(), scan);
        }
        this.byType = map;
    }

    @Override
    public ScanReport scan(SourceType type) {
        SourceScan scan = byType.get(type);
        return scan == null ? ScanReport.none() : scan.scan();
    }
}
