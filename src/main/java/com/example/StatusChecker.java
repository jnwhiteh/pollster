package com.example;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class StatusChecker implements Handler<Long> {
    private final DeadlineStore store;
    private final DateTimeFormatter hourMinuteFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd hh:mm")
            .withZone(ZoneId.systemDefault());
    private HttpClient client;
    private Logger logger;

    StatusChecker(DeadlineStore store) {
        this.store = store;
    }

    void configure(HttpClient client, Logger logger) {
        this.client = client;
        this.logger = logger;
    }

    public void handle(Long timerId) {
        String currentTime = hourMinuteFormatter.format(Instant.now());

        Service entry = store.popExpiredService(currentTime);
        if (entry != null) {
            checkStatus(entry);
        }
    }

    private void checkStatus(final Service entry) {
        logger.info("Deadline for {0} expired, making request to {1}", entry.id, entry.url);

        if (entry.exceptionHandler == null) {
            entry.exceptionHandler = new HttpExceptionHandler(entry);
        }

        client.requestAbs(HttpMethod.GET, entry.url, response -> {
            logger.info("Got a response for {0} with status code {1} - {2}", entry.url, response.statusCode(), response.statusMessage());

            String status = response.statusCode() == 200 ? "UP" : "DOWN";
            String lastCheck = hourMinuteFormatter.format(Instant.now());
            store.updateService(entry.id, status, lastCheck);
        }).exceptionHandler(entry.exceptionHandler)
                .setFollowRedirects(true).setTimeout(2000).end();
    }


    private class HttpExceptionHandler implements Handler<Throwable> {
        private Service entry;

        HttpExceptionHandler(Service entry) {
            this.entry = entry;
        }

        public void handle(Throwable t) {
            logger.info("Exception for ID {0}", t, this.entry.id);
            String status = "DOWN";
            String lastCheck = hourMinuteFormatter.format(Instant.now());
            store.updateService(entry.id, status, lastCheck);
        }
    }
}
