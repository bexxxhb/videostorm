package de.videostorm.maintenance.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The detection rules: duplicates are found when the IMDb id matches exactly OR the original title
 * matches after lowercasing and trimming; the two criteria are unioned so one movie can land in more
 * than one group; a movie takes part only in the criteria it has an attribute for, and is excluded
 * entirely only when it has neither; and results are grouped per shared value, not pairwise.
 */
class DuplicateScannerTest {

    private final DuplicateScanner scanner = new DuplicateScanner();

    @Test
    void groupsMoviesSharingAnExactImdbId() {
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of("tt0111161", "Shawshank", "/a"),
                ScanCandidate.of("tt0111161", "Die Verurteilten", "/b"),
                ScanCandidate.of("tt0068646", "Godfather", "/c")));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.criterion()).isEqualTo(DuplicateCriterion.IMDB_ID);
            assertThat(group.sharedValue()).isEqualTo("tt0111161");
            assertThat(group.members()).hasSize(2);
        });
    }

    @Test
    void groupsMoviesSharingAnOriginalTitleAfterLowercasingAndTrimming() {
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of(null, "  The Matrix ", "/a"),
                ScanCandidate.of(null, "THE MATRIX", "/b")));

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.criterion()).isEqualTo(DuplicateCriterion.ORIGINAL_TITLE);
            assertThat(group.sharedValue()).isEqualTo("the matrix");
            assertThat(group.members()).hasSize(2);
        });
    }

    @Test
    void unionsTheTwoCriteriaSoAMovieCanAppearInMoreThanOneGroup() {
        // Movie B shares an IMDb id with A and an original title with C — it belongs to both groups.
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of("tt1", "Alpha", "/a"),
                ScanCandidate.of("tt1", "Beta", "/b"),
                ScanCandidate.of("tt9", "beta", "/c")));

        assertThat(groups).hasSize(2);
        DuplicateGroup imdbGroup = groupFor(groups, DuplicateCriterion.IMDB_ID, "tt1");
        DuplicateGroup titleGroup = groupFor(groups, DuplicateCriterion.ORIGINAL_TITLE, "beta");
        assertThat(imdbGroup.members()).hasSize(2);
        assertThat(titleGroup.members()).extracting(member -> member.filePath().orElseThrow())
                .containsExactly("/b", "/c");
    }

    @Test
    void collectsEveryMovieSharingAValueIntoOneGroupRatherThanPairs() {
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of("tt1", null, "/a"),
                ScanCandidate.of("tt1", null, "/b"),
                ScanCandidate.of("tt1", null, "/c")));

        assertThat(groups).singleElement().satisfies(group ->
                assertThat(group.members()).hasSize(3));
    }

    @Test
    void excludesAMovieOnlyWhenBothAttributesAreAbsent() {
        // The value-less movie joins no group; the others still pair on the one attribute each shares.
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of(null, null, "/none"),
                ScanCandidate.of("tt1", null, "/imdbA"),
                ScanCandidate.of("tt1", null, "/imdbB")));

        assertThat(groups).singleElement().satisfies(group ->
                assertThat(group.members()).extracting(member -> member.filePath().orElseThrow())
                        .containsExactly("/imdbA", "/imdbB"));
    }

    @Test
    void matchesOnTheOnlyAttributeAMoviePresentsWhenTheOtherIsMissing() {
        // A has only a title, B has only an id, C carries both — A pairs with C on title, B with C on id.
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of(null, "Solaris", "/a"),
                ScanCandidate.of("tt5", null, "/b"),
                ScanCandidate.of("tt5", "solaris", "/c")));

        assertThat(groupFor(groups, DuplicateCriterion.IMDB_ID, "tt5").members())
                .extracting(member -> member.filePath().orElseThrow()).containsExactly("/b", "/c");
        assertThat(groupFor(groups, DuplicateCriterion.ORIGINAL_TITLE, "solaris").members())
                .extracting(member -> member.filePath().orElseThrow()).containsExactly("/a", "/c");
    }

    @Test
    void doesNotGroupValuesCarriedByOnlyOneMovie() {
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of("tt1", "Alpha", "/a"),
                ScanCandidate.of("tt2", "Beta", "/b")));

        assertThat(groups).isEmpty();
    }

    @Test
    void treatsBlankAttributesAsAbsentRatherThanAsharedEmptyValue() {
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of("   ", "  ", "/a"),
                ScanCandidate.of("", "", "/b")));

        assertThat(groups).isEmpty();
    }

    @Test
    void keepsTheMembersRawAttributesAsTheSnapshotToDisplay() {
        List<DuplicateGroup> groups = scanner.scan(List.of(
                ScanCandidate.of("tt1", "The Matrix", "/a"),
                ScanCandidate.of("tt1", "Matrix", "/b")));

        assertThat(groups).singleElement().satisfies(group ->
                assertThat(group.members()).first().satisfies(member -> {
                    assertThat(member.imdbId()).contains("tt1");
                    assertThat(member.originalTitle()).contains("The Matrix");
                    assertThat(member.filePath()).contains("/a");
                }));
    }

    private static DuplicateGroup groupFor(List<DuplicateGroup> groups, DuplicateCriterion criterion, String value) {
        return groups.stream()
                .filter(group -> group.criterion() == criterion && group.sharedValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + criterion + " group for " + value));
    }
}
