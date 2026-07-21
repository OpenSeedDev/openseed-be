package com.seedrank.feedback.list;

import java.util.List;

record FeedbackPageResponse(
        List<FeedbackItemResponse> items,
        String nextCursor,
        boolean hasNext) {
}
