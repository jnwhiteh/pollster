package com.example.api;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class AddServiceRequestTest {
    @Test
    public void testCanParseRequest() throws Exception {
        String body = "{\"name\": \"bing\", \"url\": \"https://www.bing.com\"}";
        JsonObject obj= new JsonObject(body);
        AddServiceRequest req = obj.mapTo(AddServiceRequest.class);
        assertEquals("bing", req.name);
        assertEquals("https://www.bing.com", req.url);
    }
}