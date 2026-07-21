package com.seedrank.feedback.list;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.feedback.Feedback;
import com.seedrank.feedback.FeedbackRepository;
import com.seedrank.idea.Idea;
import com.seedrank.idea.IdeaRepository;
import com.seedrank.idea.IdeaStatus;
import com.seedrank.idea.draft.IdeaDraftNotFoundException;

@Service
class FeedbackListService {
    private static final int MAX_PAGE_SIZE = 100;

    private final IdeaRepository ideas;
    private final FeedbackRepository feedbacks;

    FeedbackListService(IdeaRepository ideas, FeedbackRepository feedbacks) {
        this.ideas = ideas;
        this.feedbacks = feedbacks;
    }

    @Transactional(readOnly = true)
    FeedbackPageResponse get(UUID ideaId, String encodedCursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        Idea idea = ideas.findById(ideaId).orElseThrow(IdeaDraftNotFoundException::new);
        if (idea.status() != IdeaStatus.PUBLISHED) throw new IdeaDraftNotFoundException();

        FeedbackCursor cursor = FeedbackCursor.decode(encodedCursor);
        var pageRequest = PageRequest.of(0, size + 1);
        List<Feedback> found = cursor == null
                ? feedbacks.findFirstPage(ideaId, pageRequest)
                : cursor.accepted()
                        ? feedbacks.findPageAfterAccepted(ideaId, cursor.createdAt(), cursor.id(), pageRequest)
                        : feedbacks.findPageAfterUnaccepted(ideaId, cursor.createdAt(), cursor.id(), pageRequest);
        boolean hasNext = found.size() > size;
        List<Feedback> page = hasNext ? found.subList(0, size) : found;
        String nextCursor = hasNext
                ? cursor(page.getLast()).encode()
                : null;
        return new FeedbackPageResponse(page.stream().map(FeedbackItemResponse::from).toList(), nextCursor, hasNext);
    }

    private FeedbackCursor cursor(Feedback feedback) {
        return new FeedbackCursor(feedback.acceptedAt() != null, feedback.createdAt(), feedback.id());
    }
}
