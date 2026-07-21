package com.seedrank.idea.question;

import java.util.UUID;

public record ValidationQuestionResponse(UUID id, String question, int sortOrder) {

    public static ValidationQuestionResponse from(ValidationQuestion question) {
        return new ValidationQuestionResponse(question.id(), question.question(), question.sortOrder());
    }
}
