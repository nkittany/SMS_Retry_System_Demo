// util/Json.java
package com.example.smsretry.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }
}