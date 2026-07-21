package com.seedrank.messaging.thread;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.auth.login.AccessTokenAuthenticator;
import com.seedrank.company.profile.CompanyProfileRepository;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.member.User;

@Service
class MessageThreadService {
    private final AccessTokenAuthenticator authenticator;
    private final CompanyProfileRepository companyProfiles;
    private final IdeaRepository ideas;
    private final MessageThreadRepository threads;
    private final Clock clock;

    MessageThreadService(
            AccessTokenAuthenticator authenticator,
            CompanyProfileRepository companyProfiles,
            IdeaRepository ideas,
            MessageThreadRepository threads,
            Clock clock) {
        this.authenticator = authenticator;
        this.companyProfiles = companyProfiles;
        this.ideas = ideas;
        this.threads = threads;
        this.clock = clock;
    }

    @Transactional
    MessageThreadCreation create(String authorization, UUID ideaId) {
        var principal = authenticator.authenticate(authorization);
        if (principal.role() != User.Role.COMPANY) {
            throw new VerifiedCompanyRequiredException();
        }
        var company = companyProfiles.findByUserIdForUpdate(principal.userId())
                .filter(profile -> profile.getVerifiedAt() != null)
                .orElseThrow(VerifiedCompanyRequiredException::new);
        var idea = ideas.findByIdAndStatus(ideaId, IdeaStatus.PUBLISHED)
                .orElseThrow(MessageThreadIdeaNotFoundException::new);

        var existing = threads.findByIdeaIdAndCompanyProfileIdAndAuthorId(
                idea.id(), company.getId(), idea.authorId());
        if (existing.isPresent()) {
            return new MessageThreadCreation(MessageThreadResponse.from(existing.get()), false);
        }

        var created = threads.save(MessageThread.start(
                idea.id(), company.getId(), idea.authorId(), clock.instant()));
        return new MessageThreadCreation(MessageThreadResponse.from(created), true);
    }
}
