package com.seedrank.idea.timeline;

import java.time.Instant;

import com.seedrank.idea.publish.IdeaTimelineEvent;

record IdeaTimelineEventResponse(
        IdeaTimelineEvent.Type type,
        String actorProfileId,
        Instant occurredAt) {
}
