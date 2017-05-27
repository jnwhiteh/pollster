package com.example;

import com.example.api.AddServiceRequest;
import com.example.api.AddServiceResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

class PollsterAPIVerticle extends AbstractVerticle {
    private final Logger logger = LoggerFactory.getLogger(PollsterAPIVerticle.class);
    private final DeadlineStore store = new DeadlineStore();
    private final String storageFilename = "services.json";
    private final DateTimeFormatter hourMinuteFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd hh:mm")
            .withZone(ZoneId.systemDefault());

    private HttpClient client;

    public void start() throws Exception {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        router.get("/service").handler(this::handleServerList);
        router.post("/service").handler(this::handleAddServer);
        router.delete("/service/:id").handler(this::handleDeleteServer);

        router.route("/*").handler(StaticHandler.create());

        server.requestHandler(router::accept).listen(8080);

        // every second invoke the heap-check
        vertx.setPeriodic(1000, this::handleTimer);
        // every minute flush the current db status to disk
        vertx.setPeriodic(60000, timerId -> {
            writeStorageToFile();
        });

        HttpClientOptions options = new HttpClientOptions()
                .setLogActivity(true)
                .setMaxRedirects(5);

        client = vertx.createHttpClient(options);

        loadStorageFromFile();
    }

    private void loadStorageFromFile() {
        FileSystem fs = vertx.fileSystem();
        fs.readFile(storageFilename, res -> {
            if (res.succeeded()) {
                Buffer buf = res.result();
                int loaded = store.loadFromBuffer(buf);
                logger.info("Read {0} entries from storage", loaded);
            } else {
                // something went wrong, but log and continue
                logger.error("Failed when reading storage file", res.cause());

                store.add("google-ssl", "https://google.com/");
                store.add("google", "http://google.com/");
            }
        });
    }

    private void writeStorageToFile() {
        FileSystem fs = vertx.fileSystem();
        Buffer buf = store.getBuffer();
        fs.writeFile(storageFilename, buf, result -> {
            if (result.succeeded()) {
                logger.info("Flushed {0} entries to {1}", store.size(), storageFilename);
            } else {
                logger.error("Failed when writing to storage", result.cause());
            }
        });
    }

    public void stop() throws Exception {
        writeStorageToFile();
    }

    private void handleTimer(Long timerId) {
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

            String status = response.statusMessage();
            String lastCheck = hourMinuteFormatter.format(Instant.now());
            store.updateService(entry.id, status, lastCheck);
        }).exceptionHandler(entry.exceptionHandler)
                .setFollowRedirects(true).setTimeout(2000).end();
    }

    class HttpExceptionHandler implements Handler<Throwable> {
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

    private void handleAddServer(RoutingContext routingContext) {
        AddServiceRequest req = routingContext.getBodyAsJson().mapTo(AddServiceRequest.class);

        String id = store.add(req.getName(), req.getUrl());
        writeStorageToFile();

        // Return the new ID in the response
        AddServiceResponse res = new AddServiceResponse(id);
        routingContext
                .response()
                .end(JsonObject.mapFrom(res).encodePrettily());
    }

    private void handleServerList(RoutingContext routingContext) {
        Buffer buf = store.getBuffer();
        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "application/json");
        response.end(buf);
    }

    private void handleDeleteServer(RoutingContext routingContext) {
        String serverId = routingContext.request().getParam("id");
        Boolean removed = store.remove(serverId);

        Integer status = removed ? 204 : 404;
        routingContext
                .response()
                .setStatusCode(status)
                .end();
    }
}
