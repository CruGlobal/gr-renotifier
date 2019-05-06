package org.cru.globalreg.renotifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sender {

    private static final Logger LOG = LoggerFactory.getLogger(Sender.class);

    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final URI uri;
    private final String entityType;
    private final String triggeredBy;

    public Sender(URI uri, String entityType, String triggeredBy) {
        this.uri = uri;
        this.entityType = entityType;
        this.triggeredBy = triggeredBy;
    }


    public CompletableFuture<Optional<UUID>> sendGrUpdateNotification(PersonRecord record) {
        String body = buildBody(record);
        HttpRequest post = HttpRequest.newBuilder()
            .uri(uri)
            .POST(BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMinutes(1))
            .build();

        final CompletableFuture<HttpResponse<String>> future = client.sendAsync(
            post,
            BodyHandlers.ofString()
        );

        return future.thenApply(response -> {
            final int code = response.statusCode();
            if (code < 200 || code >= 300) {
                LOG.error(
                    "unsuccessful response from the subscription resource: {} \n  {}",
                    code,
                    response.body());
                return Optional.of(record.id);
            } else {
                LOG.debug("successful response");
            }
            return Optional.empty();
        });
    }

    private String buildBody(PersonRecord record) {
        // poor man's json for now
        String template = "{\n" +
            "  \"action\": \"{{action}}\",\n" +
            "  \"id\": \"{{id}}\",\n" +
            "  \"client_integration_id\": null,\n" +
            "  \"triggered_by\": \"{{triggeredBy}}\",\n" +
            "  \"entity_type\": \"{{entityType}}\"\n" +
            "}";

        return template
            .replace("{{id}}", record.id.toString())
            .replace("{{action}}", determineAction(record))
            .replace("{{triggeredBy}}", triggeredBy)
            .replace("{{entityType}}", entityType);
    }

    private String determineAction(PersonRecord record) {
        return record.deletedAt.map(ignoredDate -> "deleted").orElse("updated");
    }
}
