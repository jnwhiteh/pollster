package com.example.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AddServiceRequest {
    String name;
    String url;

    public AddServiceRequest(
            @JsonProperty("name")
                    String name,

            @JsonProperty("url")
                    String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }
}
