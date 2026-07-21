package com.seedrank.point;

public enum ActivityReward {
    DAILY_FIRST_ACCESS(30, PointLedger.SourceType.DAILY_FIRST_ACCESS),
    IDEA_PUBLISHED(50, PointLedger.SourceType.IDEA_PUBLISHED),
    FEEDBACK_CREATED(20, PointLedger.SourceType.FEEDBACK_CREATED),
    FEEDBACK_ACCEPTED(100, PointLedger.SourceType.FEEDBACK_ACCEPTED);

    private final int amount;
    private final PointLedger.SourceType sourceType;

    ActivityReward(int amount, PointLedger.SourceType sourceType) {
        this.amount = amount;
        this.sourceType = sourceType;
    }

    int amount() {
        return amount;
    }

    PointLedger.SourceType sourceType() {
        return sourceType;
    }
}
