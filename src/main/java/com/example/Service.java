package com.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Handler;

public class Service {
    @JsonProperty
    String id;

    @JsonProperty
    String name;

    @JsonProperty
    String url;

    @JsonProperty
    String status;

    @JsonProperty
    String lastCheck;

    protected Handler<Throwable> exceptionHandler;

    public Service(String id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.status = "UNKNOWN";
        this.lastCheck = "1970-01-01 00:00";
    }

    public Service(
            @JsonProperty("id")
            String id,
            @JsonProperty("name")
            String name,
            @JsonProperty("url")
            String url,
            @JsonProperty("status")
            String status,
            @JsonProperty("lastCheck")
            String lastCheck) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.status = status;
        this.lastCheck = lastCheck;
    }
}
