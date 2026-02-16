// store/StateUpdate.java
package com.example.smsretry.store;

import com.example.smsretry.model.MessageState;

public class StateUpdate {
    public enum Kind {
        PENDING, SUCCESS, FAILED
    }

    public final Kind kind;
    public final MessageState state;

    private StateUpdate(Kind kind, MessageState state) {
        this.kind = kind;
        this.state = state;
    }

    public static StateUpdate pending(MessageState s) {
        return new StateUpdate(Kind.PENDING, s);
    }

    public static StateUpdate success(MessageState s) {
        return new StateUpdate(Kind.SUCCESS, s);
    }

    public static StateUpdate failed(MessageState s) {
        return new StateUpdate(Kind.FAILED, s);
    }
}