package com.example;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;

class DeadlineStore {
    private final Map<String,Service> entries;
    private final PriorityQueue<Service> deadlineHeap;

    DeadlineStore() {
        this.entries = new LinkedHashMap<>();
        this.deadlineHeap = new PriorityQueue<>(this::compareByDate);
    }

    /**
     * Comparator for services to ensure the least deadline time is at
     * the front of the priority queue
     */
    private int compareByDate(Service a, Service b) {
        return a.lastCheck.compareTo(b.lastCheck);
    }

    /**
     * Return the number of entries in the store
     */
    int size() {
        return this.entries.size();
    }

    /**
     * Add a service and return the newly allocated ID
     *
     * @param name, the user-input name of the service
     * @param url, the user-input URL to check
     * @return the newly allocated ID
     */
    String add(String name, String url) {
        String uuid = UUID.randomUUID().toString();
        Service entry = new Service(uuid, name, url);
        entries.put(uuid, entry);
        deadlineHeap.add(entry);
        return uuid;
    }

    /**
     * Remove an entry
     *
     * @param id, the ID of the service
     * @return true if the service entry was present and was removed
     */
    Boolean remove(String id) {
        if (entries.containsKey(id)) {
            entries.remove(id);
            deadlineHeap.remove(id);
            return true;
        }

        return false;
    }

    /**
     * Load the contents of from a JSON buffer containing an object with a
     * single field 'services' which contains an array of service objects.
     *
     * @return the number of entires loaded from the buffer
     */
    int loadFromBuffer(Buffer buf) {
        JsonObject wrapperObj = buf.toJsonObject();
        JsonArray arr = wrapperObj.getJsonArray("services");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject obj = arr.getJsonObject(i);
            Service entry = obj.mapTo(Service.class);
            entries.put(entry.id, entry);
            deadlineHeap.add(entry);
        }
        return arr.size();
    }

    /**
     * Returns a buffer containing the serialized contents of the store,
     * suitable for storage and being subsequently loaded.
     *
     * @return a buffer containing the JSON contents of the store
     */
    Buffer getBuffer() {
        // Fetch the list of entries from the map and pack into JSON
        List<Service> entryList = new ArrayList<>(entries.size());
        entryList.addAll(entries.values());
        JsonArray array = new JsonArray(entryList);

        // Wrap the array in an object
        JsonObject obj = new JsonObject();
        obj.put("services", array);

        return Buffer.buffer(obj.encodePrettily());
    }

    /**
     * Check to see if there is an expired service waiting to be checked
     *
     * @param currentTime the current time in 'yyyy-MM-dd hh:mm' format
     * @return true if there is an expired service in the store
     */
    Boolean hasExpiredService(String currentTime) {
        if (deadlineHeap.size() == 0) {
            return false;
        }

        // Find any entry that is 1 minute stale and return it
        Service top = deadlineHeap.peek();
        return top.lastCheck.compareTo(currentTime) < 0;
    }

    /**
     * Removes an expired entry from the heap and returns it. The service
     * will no longer be checked for
     *
     * @param currentTime
     * @return
     */
    Service popExpiredService(String currentTime) {
        if (deadlineHeap.size() == 0) {
            return null;
        }

        Service top = deadlineHeap.peek();
        if (top.lastCheck.compareTo(currentTime) < 0) {
            return deadlineHeap.poll();
        }

        return null;
    }

    /**
     * Update the status and lastChecked time of a given service. If it was
     * removed from the heap previously, this reinserts it to ensure proper
     * sorting.
     *
     * @param id
     * @param status
     * @param lastCheck
     */
    void updateService(String id, String status, String lastCheck) {
        if (entries.containsKey(id)) {
            Service entry = entries.get(id);
            entry.status = status;
            entry.lastCheck = lastCheck;

            // sanity check to make sure the heap is sorted properly
            if (deadlineHeap.contains(entry)) {
                deadlineHeap.remove(entry);
            }

            deadlineHeap.add(entry);
        }
    }
}
