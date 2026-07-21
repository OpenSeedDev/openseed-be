package com.seedrank.messaging.thread;

public class MessageThreadIdeaNotFoundException extends RuntimeException {
    public MessageThreadIdeaNotFoundException() {
        super("아이디어를 찾을 수 없습니다.");
    }
}
