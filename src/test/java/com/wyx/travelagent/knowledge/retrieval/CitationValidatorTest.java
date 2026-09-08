package com.wyx.travelagent.knowledge.retrieval;

import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationValidatorTest {
    private final CitationValidator validator = new CitationValidator();
    private final List<KnowledgeReference> currentAllowed = List.of(reference(11), reference(22), reference(33));

    @Test
    void validIdsAreDeduplicatedAndReturnedInCanonicalRetrievalOrder() {
        CitationValidationResult result = validator.validate(List.of(33L, 11L, 33L), currentAllowed);

        assertThat(result.validReferences()).extracting(KnowledgeReference::chunkId)
                .containsExactly(11L, 33L);
        assertThat(result.rejectedChunkIds()).isEmpty();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void forgedOtherCityPreviousRequestAndExistingButNotTopFiveAreRejected() {
        CitationValidationResult result = validator.validate(List.of(11L, 44L, 55L, 66L), currentAllowed);

        assertThat(result.validReferences()).extracting(KnowledgeReference::chunkId).containsExactly(11L);
        assertThat(result.rejectedChunkIds()).containsExactly(44L, 55L, 66L);
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("INVALID_CITATION");
            assertThat(issue.message()).doesNotContain("44", "55", "66", "SQL", "杭州");
        });
    }

    @Test
    void nullNonPositiveDuplicatesAndEmptyWhitelistAreSafe() {
        CitationValidationResult invalidValues = validator.validate(
                java.util.Arrays.asList(null, -1L, 0L, 22L, 22L), currentAllowed);
        assertThat(invalidValues.validReferences()).extracting(KnowledgeReference::chunkId).containsExactly(22L);
        assertThat(invalidValues.invalidValueCount()).isEqualTo(3);
        assertThat(invalidValues.issues()).singleElement()
                .extracting(CitationIssue::code).isEqualTo("INVALID_CITATION");

        CitationValidationResult none = validator.validate(List.of(11L), List.of());
        assertThat(none.validReferences()).isEmpty();
        assertThat(none.rejectedChunkIds()).containsExactly(11L);
        assertThat(none.issues()).singleElement();
    }

    @Test
    void emptyOrNullRequestedListHasNoIssueAndCannotFabricateReferences() {
        assertThat(validator.validate(List.of(), currentAllowed).issues()).isEmpty();
        assertThat(validator.validate(null, currentAllowed).validReferences()).isEmpty();
    }

    private static KnowledgeReference reference(long chunkId) {
        return new KnowledgeReference(1L, chunkId, 0, "标题", "杭州", "发布者",
                URI.create("https://example.test/source"), LocalDate.of(2026, 9, 1),
                KnowledgeDocumentType.OFFICIAL_NOTICE, "正文", 0.9);
    }
}
