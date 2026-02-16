// model/MessageState.java
package com.example.smsretry.model;

public class MessageState {
    public String messageId;
    public int attemptCount; // 1..6 (attempt #1 happens in newMessage)
    public long arrivalAtMs; // epoch ms
    public long nextDueAtMs; // epoch ms
    public MessageStatus status; // PENDING/SUCCESS/FAILED
    public String lastError; // optional
    public String phone;
    public String body;

    public static MessageState fromMessage(Message m, long nowMs) {
        MessageState s = new MessageState();
        s.messageId = m.messageId();
        s.phone = m.phone();
        s.body = m.body();
        s.arrivalAtMs = nowMs;
        s.attemptCount = 0;
        s.status = MessageStatus.PENDING;
        s.nextDueAtMs = nowMs;
        return s;
    }

    public Message toMessage() {
        return new Message(messageId, phone, body);
    }
}