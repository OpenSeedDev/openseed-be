package com.seedrank.feedback.accept;

public class FeedbackAlreadyAcceptedException extends RuntimeException {
    public FeedbackAlreadyAcceptedException() {
        super("Feedback is already accepted");
    }
}
