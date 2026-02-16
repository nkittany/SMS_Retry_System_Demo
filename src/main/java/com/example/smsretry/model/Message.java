// model/Message.java
package com.example.smsretry.model;

public record Message(String messageId, String phone, String body) {
}