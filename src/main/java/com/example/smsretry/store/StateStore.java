// store/StateStore.java
package com.example.smsretry.store;

import com.example.smsretry.model.MessageState;

import java.util.List;

public interface StateStore {
    void enqueue(StateUpdate update);

    List<MessageState> loadPendingAll(); // recovery
}