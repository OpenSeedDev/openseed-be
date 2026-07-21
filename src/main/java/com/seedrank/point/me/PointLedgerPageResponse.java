package com.seedrank.point.me;

import java.util.List;

record PointLedgerPageResponse(
        List<PointLedgerItemResponse> items,
        String nextCursor,
        boolean hasNext) {
}
