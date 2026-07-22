package com.seedrank.messaging.message;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.messaging.thread.MessageThreadRepository;

@Service
class MessageService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AccessTokenAuthenticator authenticator;
    private final MessageThreadRepository threads;
    private final MessageRepository messages;
    private final Clock clock;

    MessageService(
            AccessTokenAuthenticator authenticator,
            MessageThreadRepository threads,
            MessageRepository messages,
            Clock clock) {
        this.authenticator = authenticator;
        this.threads = threads;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    MessageResponse send(String authorization, UUID threadId, String content) {
        var principal = authenticator.authenticate(authorization);
        var thread = threads.findParticipantThreadForUpdate(threadId, principal.userId())
                .orElseThrow(MessageThreadNotFoundException::new);
        var sentAt = clock.instant();
        var message = messages.save(Message.send(thread, principal.userId(), content, sentAt));
        thread.touch(sentAt);
        return MessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    MessagePageResponse list(String authorization, UUID threadId, String encodedCursor, int size) {
        var principal = authenticator.authenticate(authorization);
        threads.findParticipantThread(threadId, principal.userId())
                .orElseThrow(MessageThreadNotFoundException::new);
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        MessageCursor cursor = MessageCursor.decode(encodedCursor);
        var pageRequest = PageRequest.of(0, size + 1);
        List<Message> found = cursor == null
                ? messages.findFirstPage(threadId, pageRequest)
                : messages.findPageAfter(threadId, cursor.sentAt(), cursor.id(), pageRequest);
        boolean hasNext = found.size() > size;
        List<Message> page = hasNext ? found.subList(0, size) : found;
        String nextCursor = hasNext
                ? new MessageCursor(page.getLast().sentAt(), page.getLast().id()).encode()
                : null;
        return new MessagePageResponse(page.stream().map(MessageResponse::from).toList(), nextCursor, hasNext);
    }
}
