package com.seedrank.idea.me;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;

@Service
class MyIdeaListService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;

    MyIdeaListService(AccessTokenAuthenticator authenticator, IdeaRepository ideas) {
        this.authenticator = authenticator;
        this.ideas = ideas;
    }

    @Transactional(readOnly = true)
    MyIdeaPageResponse get(String authorization, IdeaStatus status, String encodedCursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        var principal = authenticator.authenticate(authorization);
        MyIdeaCursor cursor = MyIdeaCursor.decode(encodedCursor);
        var pageRequest = PageRequest.of(0, size + 1);
        List<Idea> found = find(principal.userId(), status, cursor, pageRequest);
        boolean hasNext = found.size() > size;
        List<Idea> page = hasNext ? found.subList(0, size) : found;
        String nextCursor = hasNext
                ? new MyIdeaCursor(page.getLast().updatedAt(), page.getLast().id()).encode()
                : null;
        return new MyIdeaPageResponse(
                page.stream().map(MyIdeaItemResponse::from).toList(),
                nextCursor,
                hasNext);
    }

    private List<Idea> find(
            java.util.UUID authorId,
            IdeaStatus status,
            MyIdeaCursor cursor,
            PageRequest pageRequest) {
        if (status == null && cursor == null) {
            return ideas.findFirstPageByAuthor(authorId, pageRequest);
        }
        if (status == null) {
            return ideas.findPageAfterByAuthor(authorId, cursor.updatedAt(), cursor.id(), pageRequest);
        }
        if (cursor == null) {
            return ideas.findFirstPageByAuthorAndStatus(authorId, status, pageRequest);
        }
        return ideas.findPageAfterByAuthorAndStatus(
                authorId, status, cursor.updatedAt(), cursor.id(), pageRequest);
    }
}
