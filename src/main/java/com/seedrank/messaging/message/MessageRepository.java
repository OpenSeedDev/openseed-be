package com.seedrank.messaging.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("""
            select message from Message message
            where message.thread.id = :threadId
            order by message.sentAt asc, message.id asc
            """)
    List<Message> findFirstPage(@Param("threadId") UUID threadId, Pageable pageable);

    @Query("""
            select message from Message message
            where message.thread.id = :threadId
              and (message.sentAt > :cursorAt
                   or (message.sentAt = :cursorAt and message.id > :cursorId))
            order by message.sentAt asc, message.id asc
            """)
    List<Message> findPageAfter(
            @Param("threadId") UUID threadId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
