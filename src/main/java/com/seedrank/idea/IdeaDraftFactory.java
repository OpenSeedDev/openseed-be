package com.seedrank.idea;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class IdeaDraftFactory {
    private final Clock clock;

    IdeaDraftFactory(Clock clock) {
        this.clock = clock;
    }

    public Idea create(
            UUID authorId,
            String title,
            String category,
            String summary,
            String problem,
            String targetCustomer,
            String solution,
            String businessModel) {
        Instant now = clock.instant();
        return Idea.draft(
                authorId,
                title,
                category,
                summary,
                problem,
                targetCustomer,
                solution,
                businessModel,
                now);
    }

    public Idea createFromAi(
            UUID authorId,
            UUID sourceAiJobId,
            int sourceAiCandidateNumber,
            String title,
            String category,
            String summary,
            String problem,
            String targetCustomer,
            String solution,
            String businessModel) {
        return Idea.draftFromAi(
                authorId,
                sourceAiJobId,
                sourceAiCandidateNumber,
                title,
                category,
                summary,
                problem,
                targetCustomer,
                solution,
                businessModel,
                clock.instant());
    }
}
