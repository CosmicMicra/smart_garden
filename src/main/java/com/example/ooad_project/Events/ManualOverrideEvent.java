package com.example.ooad_project.Events;

public class ManualOverrideEvent {
    public enum Type { WATER, PESTICIDE, HEATER }
    private final Type type;
    private final int value;

    public ManualOverrideEvent(Type type, int value) {
        this.type = type;
        this.value = value;
    }

    public Type getType() { return type; }
    public int getValue() { return value; }
}
