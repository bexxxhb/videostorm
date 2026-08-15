package de.videostorm.maintenance.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.maintenance.application.port.out.DuplicateScanCandidates;
import de.videostorm.maintenance.application.port.out.DuplicateScanRunStore;
import de.videostorm.maintenance.domain.DuplicateCriterion;
import de.videostorm.maintenance.domain.DuplicateGroup;
import de.videostorm.maintenance.domain.DuplicateMember;
import de.videostorm.maintenance.domain.DuplicateScanRun;
import de.videostorm.maintenance.domain.ScanCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The duplicate-scan persistence seam against a real database: a run with its groups and members
 * survives a save/read round trip, the history reads newest-first metadata without the groups, and the
 * candidate source projects the live {@code movie} table down to the three attributes the scan needs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DuplicateScanPersistenceIT extends PostgresIntegrationTestBase {

    private static final Instant EXECUTED = Instant.parse("2026-08-14T10:15:30Z");

    @Autowired
    private DuplicateScanRunStore store;

    @Autowired
    private DuplicateScanCandidates candidates;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM duplicate_scan_member");
        jdbcTemplate.update("DELETE FROM duplicate_scan_group");
        jdbcTemplate.update("DELETE FROM duplicate_scan_run");
        jdbcTemplate.update("DELETE FROM movie");
    }

    @Test
    void roundTripsARunWithItsGroupsAndMembers() {
        DuplicateGroup group = new DuplicateGroup(DuplicateCriterion.IMDB_ID, "tt1", List.of(
                new DuplicateMember(Optional.of("tt1"), Optional.of("The Matrix"), Optional.of("/a")),
                new DuplicateMember(Optional.of("tt1"), Optional.empty(), Optional.of("/b"))));
        DuplicateScanRun saved = store.save(
                new DuplicateScanRun(null, EXECUTED, Duration.ofMillis(42), List.of(group)));

        assertThat(saved.id()).isNotNull();

        Optional<DuplicateScanRun> reloaded = store.findById(saved.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().executedAt()).isEqualTo(EXECUTED);
        assertThat(reloaded.get().duration()).isEqualTo(Duration.ofMillis(42));
        assertThat(reloaded.get().groups()).singleElement().satisfies(reloadedGroup -> {
            assertThat(reloadedGroup.criterion()).isEqualTo(DuplicateCriterion.IMDB_ID);
            assertThat(reloadedGroup.sharedValue()).isEqualTo("tt1");
            assertThat(reloadedGroup.members()).hasSize(2);
            assertThat(reloadedGroup.members().get(1).originalTitle()).isEmpty();
        });
    }

    @Test
    void listsHistoryNewestFirstWithGroupCountsButNoGroups() {
        store.save(new DuplicateScanRun(null, EXECUTED, Duration.ofMillis(10), List.of()));
        store.save(new DuplicateScanRun(null, EXECUTED.plusSeconds(60), Duration.ofMillis(20), List.of(
                new DuplicateGroup(DuplicateCriterion.ORIGINAL_TITLE, "the matrix", List.of(
                        new DuplicateMember(Optional.empty(), Optional.of("The Matrix"), Optional.of("/a")),
                        new DuplicateMember(Optional.empty(), Optional.of("Matrix"), Optional.of("/b")))))));

        assertThat(store.history())
                .extracting(summary -> summary.executedAt(), summary -> summary.groupCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(EXECUTED.plusSeconds(60), 1),
                        org.assertj.core.groups.Tuple.tuple(EXECUTED, 0));
    }

    @Test
    void findByIdIsEmptyForAnUnknownRun() {
        assertThat(store.findById(999_999L)).isEmpty();
    }

    @Test
    void projectsMoviesToCandidatesTreatingBlanksAsAbsent() {
        insertMovie("tt0111161", "The Matrix", "/films/matrix.mkv");
        insertMovie(null, "   ", "/films/untitled.mkv");

        List<ScanCandidate> all = candidates.all();

        assertThat(all).hasSize(2);
        assertThat(all).anySatisfy(candidate -> {
            assertThat(candidate.imdbId()).contains("tt0111161");
            assertThat(candidate.originalTitle()).contains("The Matrix");
            assertThat(candidate.filePath()).contains("/films/matrix.mkv");
        });
        assertThat(all).anySatisfy(candidate -> {
            assertThat(candidate.imdbId()).isEmpty();
            assertThat(candidate.originalTitle()).isEmpty();
            assertThat(candidate.filePath()).contains("/films/untitled.mkv");
        });
    }

    private void insertMovie(String imdbId, String originalTitle, String sourcePath) {
        // A unique slug/normalized_title only satisfies the columns; the projection under test is what
        // matters, and movie de-duplication no longer constrains what rows may coexist.
        String unique = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO movie (title, original_title, year, normalized_title, genres, imdb_id, source_path, slug)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "Title", originalTitle, 0, "title-" + unique, "", imdbId, sourcePath, "slug-" + unique);
    }
}
