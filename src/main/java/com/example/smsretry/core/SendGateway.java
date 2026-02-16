// core/SendGateway.java
package com.example.smsretry.core;

import com.example.smsretry.model.Message;

public interface SendGateway {
    boolean send(Message message);
}