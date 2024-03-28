package com.lrenyi.template.core.nats.event;

import lombok.Data;

@Data
public class Event {
    public static String NATS_EVENT_SUBJECT = "NatsEventSubject";
    private String source;
    private String dst;
    private String eventName;
    private String jsonPayload;
}
