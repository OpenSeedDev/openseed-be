package com.seedrank.ai.job;

public record AiJobManualForm(
        String title,
        String category,
        String summary,
        String problem,
        String targetCustomer,
        String solution,
        String businessModel) {

    static AiJobManualForm empty() {
        return new AiJobManualForm("", "", "", "", "", "", "");
    }
}
