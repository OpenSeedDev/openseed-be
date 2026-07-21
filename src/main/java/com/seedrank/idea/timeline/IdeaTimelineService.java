package com.seedrank.idea.timeline;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;
import com.seedrank.idea.publish.IdeaTimelineEventRepository;
import com.seedrank.member.User;
import com.seedrank.member.UserRepository;

@Service
class IdeaTimelineService {
    private final AccessTokenAuthenticator authenticator;
    private final IdeaRepository ideas;
    private final IdeaTimelineEventRepository timeline;
    private final UserRepository users;

    IdeaTimelineService(
            AccessTokenAuthenticator authenticator,
            IdeaRepository ideas,
            IdeaTimelineEventRepository timeline,
            UserRepository users) {
        this.authenticator = authenticator;
        this.ideas = ideas;
        this.timeline = timeline;
        this.users = users;
    }

    @Transactional(readOnly = true)
    IdeaTimelineResponse get(String authorization, UUID ideaId) {
        UUID viewerId = authorization == null ? null : authenticator.authenticate(authorization).userId();
        Idea idea = ideas.findById(ideaId).orElseThrow(IdeaDraftNotFoundException::new);
        boolean author = idea.authorId().equals(viewerId);
        if (idea.status() == IdeaStatus.DRAFT || (idea.status() == IdeaStatus.ARCHIVED && !author)) {
            throw new IdeaDraftNotFoundException();
        }

        var events = timeline.findByIdeaIdOrderByCreatedAtAscIdAsc(ideaId);
        var actors = users.findAllById(events.stream().map(event -> event.actorId()).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new IdeaTimelineResponse(events.stream()
                .map(event -> new IdeaTimelineEventResponse(
                        event.eventType(), actors.get(event.actorId()).getProfileId(), event.createdAt()))
                .toList());
    }
}
