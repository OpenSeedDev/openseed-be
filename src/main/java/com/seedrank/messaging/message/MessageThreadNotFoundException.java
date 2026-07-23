package com.seedrank.messaging.message;

public class MessageThreadNotFoundException extends RuntimeException {
    public MessageThreadNotFoundException() {
        super("문의 스레드를 찾을 수 없습니다.");
    }
}
