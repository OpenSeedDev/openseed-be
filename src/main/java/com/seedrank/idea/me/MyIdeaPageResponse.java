package com.seedrank.idea.me;

import java.util.List;

record MyIdeaPageResponse(
        List<MyIdeaItemResponse> items,
        String nextCursor,
        boolean hasNext) {
}
