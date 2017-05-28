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
    private final StatusChecker statusChecker = new StatusChecker(store);

    private final String storageFilename = "services.json";

    public void start() throws Exception {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        router.get("/service").handler(this::handleServerList);
        router.post("/service").handler(this::handleAddServer);
        router.delete("/service/:id").handler(this::handleDeleteServer);

        router.route("/*").handler(StaticHandler.create());

        server.requestHandler(router::accept).listen(8080);

        // set up the HTTP client with some sensible defaults
        HttpClientOptions options = new HttpClientOptions()
                .setLogActivity(true)
                .setMaxRedirects(5);
        statusChecker.configure(vertx.createHttpClient(), logger);

        // every second invoke the heap-check
        vertx.setPeriodic(1000, statusChecker);

        // every minute flush the current db status to disk
        vertx.setPeriodic(60000, timerId -> {
            writeStorageToFile();
        });

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
