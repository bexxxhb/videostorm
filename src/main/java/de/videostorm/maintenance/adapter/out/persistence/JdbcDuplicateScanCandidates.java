package de.videostorm.maintenance.adapter.out.persistence;

import de.videostorm.maintenance.application.port.out.DuplicateScanCandidates;
import de.videostorm.maintenance.domain.ScanCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads the live catalogue's movies down to the three attributes the scan needs. A plain projection
 * over the {@code movie} table — the same table the indexing write path owns — kept read-only here so
 * a scan never carries a whole movie aggregate just to compare an id and a title.
 */
@Repository
@RequiredArgsConstructor
class JdbcDuplicateScanCandidates implements DuplicateScanCandidates {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ScanCandidate> all() {
        return jdbcTemplate.query(
                "SELECT imdb_id, original_title, source_path FROM movie",
                (rs, rowNum) -> ScanCandidate.of(
                        rs.getString("imdb_id"), rs.getString("original_title"), rs.getString("source_path")));
    }
}
