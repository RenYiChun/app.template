package com.lrenyi.template.core.nats;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Response {
    private boolean success;
    private String data;
}
