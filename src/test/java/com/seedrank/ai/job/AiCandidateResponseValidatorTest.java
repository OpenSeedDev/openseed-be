package com.seedrank.ai.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class AiCandidateResponseValidatorTest {

    private final AiCandidateResponseValidator validator = new AiCandidateResponseValidator();

    @Test
    void normalizesAProblemAnalysisAndExactlyFiveDistinctCandidates() {
        String normalized = validator.validateAndNormalize(validResponse());

        assertThat(normalized).contains("\"problemAnalysis\":\"재고 폐기 문제\"");
        assertThat(normalized).contains("\"title\":\"후보 1\"");
        assertThat(normalized).doesNotContain("  후보 1  ");
    }

    @Test
    void rejectsWrongCountMissingFieldsOversizedFieldsDuplicatesAndInvalidJson() {
        assertInvalid(validResponse().replace(candidate(5), ""));
        assertInvalid(validResponse().replace("\"summary\":\"요약 1\"", "\"summary\":\" \""));
        assertInvalid(validResponse().replace("\"title\":\"  후보 1  \"", "\"title\":\"" + "가".repeat(101) + "\""));
        assertInvalid(validResponse().replace("\"title\":\"후보 2\"", "\"title\":\" 후보 1 \""));
        assertInvalid("not-json");
    }

    private void assertInvalid(String raw) {
        assertThatThrownBy(() -> validator.validateAndNormalize(raw))
                .isInstanceOf(InvalidAiCandidateResponseException.class);
    }

    static String validResponse() {
        return "{\"problemAnalysis\":\"  재고 폐기 문제  \",\"candidates\":["
                + IntStream.rangeClosed(1, 5).mapToObj(AiCandidateResponseValidatorTest::candidate)
                        .reduce((left, right) -> left + "," + right).orElseThrow()
                + "]}";
    }

    private static String candidate(int number) {
        String title = number == 1 ? "  후보 1  " : "후보 " + number;
        return "{\"title\":\"%s\",\"category\":\"커머스\",\"summary\":\"요약 %d\","
                .formatted(title, number)
                + "\"problem\":\"문제 %d\",\"targetCustomer\":\"고객 %d\","
                        .formatted(number, number)
                + "\"solution\":\"해결 %d\",\"businessModel\":\"수익 %d\"}".formatted(number, number);
    }
}
