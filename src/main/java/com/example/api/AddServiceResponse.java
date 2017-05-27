package com.example.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AddServiceResponse {
    @JsonProperty
    String id;

    public AddServiceResponse(String id) {
        this.id = id;
    }
}
