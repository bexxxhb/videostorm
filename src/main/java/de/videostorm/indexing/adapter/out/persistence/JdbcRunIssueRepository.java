package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.indexing.application.port.out.RunIssueRepository;
import de.videostorm.indexing.domain.RunIssue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes run issue detail with {@link NamedParameterJdbcTemplate}, batched so a run's whole report
 * lands in one round trip. Recorded once as the run settles, in the same append-only spirit as the run
 * history itself.
 */
@Repository
class JdbcRunIssueRepository implements RunIssueRepository {

    private static final String INSERT_ISSUE = """
            INSERT INTO indexing_run_issue (run_id, issue_type, path, title, field)
            VALUES (:runId, :issueType, :path, :title, :field)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcRunIssueRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void record(long runId, List<RunIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = issues.stream()
                .map(issue -> new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("issueType", issue.type().name())
                        .addValue("path", issue.path())
                        .addValue("title", issue.title())
                        .addValue("field", issue.field()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_ISSUE, batch);
    }
}
