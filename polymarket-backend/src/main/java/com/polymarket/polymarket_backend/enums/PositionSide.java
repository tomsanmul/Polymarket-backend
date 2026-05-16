package com.polymarket.polymarket_backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PositionSide {
    YES("YES"),
    NO("NO");

    private final String value;

    PositionSide(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PositionSide fromString(String value) {
        return PositionSide.valueOf(value.toUpperCase());
    }
}
