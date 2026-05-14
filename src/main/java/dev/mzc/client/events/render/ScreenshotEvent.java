package dev.mzc.client.events.render;

import dev.mzc.client.events.Cancellable;
import dev.mzc.client.events.EventType;

public class ScreenshotEvent extends Cancellable {
    private final EventType type;

    public ScreenshotEvent(EventType type) {
        this.type = type;
    }

    public EventType getType() {
        return type;
    }

    public boolean isPre() {
        return type == EventType.PRE;
    }

    public boolean isPost() {
        return type == EventType.POST;
    }
}
