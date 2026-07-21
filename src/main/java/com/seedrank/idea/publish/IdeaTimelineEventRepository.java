package com.seedrank.idea.publish;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaTimelineEventRepository extends JpaRepository<IdeaTimelineEvent, UUID> {
    List<IdeaTimelineEvent> findByIdeaIdOrderByCreatedAtAscIdAsc(UUID ideaId);
}
