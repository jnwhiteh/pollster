package com.example;

import com.example.api.AddServiceRequest;
import com.example.api.AddServiceResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

class PollsterAPIVerticle extends AbstractVerticle {
    private final Logger logger = LoggerFactory.getLogger(PollsterAPIVerticle.class);
    private final Map<String,ServerStatusEntry> entries = new HashMap<>();
    private final PriorityQueue<String> deadlineHeap = new PriorityQueue<>(this::compareEntries);
    private HttpClient client;
    private final String storageFilename = "services.json";

    private int compareEntries(String a, String b) {
        ServerStatusEntry aEntry = entries.get(a);
        ServerStatusEntry bEntry = entries.get(b);

        return aEntry.lastCheck.compareTo(bEntry.lastCheck);
    }

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
            flushToStorage();
        });

        HttpClientOptions options = new HttpClientOptions()
                .setLogActivity(true)
                .setMaxRedirects(5);

        client = vertx.createHttpClient(options);

        FileSystem fs = vertx.fileSystem();
        fs.readFile(storageFilename, res -> {
            if (res.succeeded()) {
                Buffer buf = res.result();

                JsonObject wrapperObj = buf.toJsonObject();
                JsonArray arr = wrapperObj.getJsonArray("services");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.getJsonObject(i);
                    ServerStatusEntry entry = obj.mapTo(ServerStatusEntry.class);
                    entries.put(entry.id, entry);
                    deadlineHeap.add(entry.id);
                }
                logger.info("Read {0} entries from storage", arr.size());
            } else {
                // something went wrong, but log and continue
                logger.error("Failed when reading storage file", res.cause());

                addEntry("google-ssl", "https://google.com/");
                addEntry("google", "http://google.com/");
            }
        });
    }

    private void flushToStorage() {
        FileSystem fs = vertx.fileSystem();

        List<ServerStatusEntry> entryList = new ArrayList<>(entries.size());
        entryList.addAll(entries.values());

        // Pack it into JSON for the response
        JsonArray array = new JsonArray(entryList);
        JsonObject obj = new JsonObject();
        obj.put("services", array);

        Buffer buf = Buffer.buffer(obj.encodePrettily());
        fs.writeFile(storageFilename, buf, result -> {
            if (result.succeeded()) {
                logger.info("Flushed {0} entries to {1}", entries.size(), storageFilename);
            } else {
                logger.error("Failed when writing to storage", result.cause());
            }
        });
    }
    public void stop() throws Exception {
        flushToStorage();
    }

    private String addEntry(String name, String url) {
        String uuid = UUID.randomUUID().toString();
        ServerStatusEntry entry = new ServerStatusEntry(uuid, name, url);
        entries.put(uuid, entry);
        deadlineHeap.add(uuid);
        return uuid;
    }

    private void handleTimer(Long timerId) {
        if (deadlineHeap.size() == 0) {
            return;
        }

        String deadline = DateTimeFormatter
                .ofPattern("yyyy-MM-dd hh:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now().minus(1, ChronoUnit.MINUTES));

        ServerStatusEntry topEntry = entries.get(deadlineHeap.peek());

        while (deadlineHeap.size() > 0 && entries.get(deadlineHeap.peek()).lastCheck.compareTo(deadline) <= 0) {
            final ServerStatusEntry entry = entries.get(deadlineHeap.poll());
            logger.info("Deadline for {0} has passed, was {1}", entry.url, entry.lastCheck);
            checkStatus(entry);
        }
    }

    private void checkStatus(final ServerStatusEntry entry) {
        logger.info("Making request to {0}", entry.url);

        if (entry.exceptionHandler == null) {
            entry.exceptionHandler = new HttpExceptionHandler(entry);
        }

        // TODO: exceptions fail silently here and probably shouldn't
        client.requestAbs(HttpMethod.GET, entry.url, response -> {
            logger.info("Got a response for {0} with status code {1} - {2}", entry.url, response.statusCode(), response.statusMessage());
            if (entries.containsKey(entry.id)) {
                String now = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm").withZone(ZoneId.systemDefault()).format(Instant.now());
                entry.lastCheck = now;
                entry.status = response.statusMessage();
                deadlineHeap.add(entry.id);
            }
        }).exceptionHandler(entry.exceptionHandler)
                .setFollowRedirects(true).setTimeout(2000).end();
    }

    class HttpExceptionHandler implements Handler<Throwable> {
        private ServerStatusEntry entry;

        public HttpExceptionHandler(ServerStatusEntry entry) {
            this.entry = entry;
        }

        public void handle(Throwable t) {
            logger.info("Exception for ID {0}", this.entry.id);
            logger.error("Failed to get status", t);

            // skip this interval, but call again in a bit
            if (entries.containsKey(entry.id)) {
                String now = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm").withZone(ZoneId.systemDefault()).format(Instant.now());
                entry.lastCheck = now;
                entry.status = "DOWN";
                deadlineHeap.add(entry.id);
            }
        }
    }

    private void handleAddServer(RoutingContext routingContext) {
        AddServiceRequest req = routingContext.getBodyAsJson().mapTo(AddServiceRequest.class);

        // Generate a new UUID and store the entry
        String id = addEntry(req.getName(), req.getUrl());
        flushToStorage();

        // Return the new ID in the response
        AddServiceResponse res = new AddServiceResponse(id);
        routingContext
                .response()
                .end(JsonObject.mapFrom(res).encodePrettily());
    }

    private void handleServerList(RoutingContext routingContext) {
        // Fetch a list of all the entries and sort them
        List<ServerStatusEntry> entryList = new ArrayList<>(entries.size());
        entryList.addAll(entries.values());

        // Pack it into JSON for the response
        JsonArray array = new JsonArray(entryList);
        JsonObject obj = new JsonObject();
        obj.put("services", array);

        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "application/json");
        response.end(obj.encodePrettily());
    }

    private void handleDeleteServer(RoutingContext routingContext) {
        String serverId = routingContext.request().getParam("id");
        Boolean present = entries.containsKey(serverId);

        if (present) {
            entries.remove(serverId);
            deadlineHeap.remove(serverId);
            flushToStorage();
        }

        Integer status = present ? 200 : 404;

        routingContext
                .response()
                .setStatusCode(status)
                .end();
    }
}
