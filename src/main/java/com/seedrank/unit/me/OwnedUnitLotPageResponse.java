package com.seedrank.unit.me;

import java.util.List;

record OwnedUnitLotPageResponse(
        List<OwnedUnitLotItemResponse> items,
        String nextCursor,
        boolean hasNext,
        String nonMonetaryNotice) {
}
