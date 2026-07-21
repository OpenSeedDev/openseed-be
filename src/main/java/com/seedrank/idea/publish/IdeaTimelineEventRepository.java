package com.seedrank.idea.publish;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdeaTimelineEventRepository extends JpaRepository<IdeaTimelineEvent, UUID> {
}
