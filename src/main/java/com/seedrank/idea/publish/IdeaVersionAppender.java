package com.seedrank.idea.publish;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.seedrank.idea.Idea;

@Component
public class IdeaVersionAppender {
    private final IdeaVersionRepository versions;

    IdeaVersionAppender(IdeaVersionRepository versions) {
        this.versions = versions;
    }

    public void append(Idea idea, List<String> questions, Instant now) {
        int nextVersionNumber = versions.findMaxVersionNumber(idea.id()) + 1;
        versions.save(IdeaVersion.nextSnapshot(idea, questions, nextVersionNumber, now));
    }
}
