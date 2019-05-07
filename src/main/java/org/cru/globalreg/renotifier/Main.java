package org.cru.globalreg.renotifier;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DefaultSubscriber;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.davidmoten.rx.jdbc.Database;
import org.davidmoten.rx.jdbc.pool.NonBlockingConnectionPool;
import org.davidmoten.rx.jdbc.pool.Pools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    @Parameter(names = "--username", required = true)
    String username;

    @Parameter(names = "--password", password = true)
    String password;

    @Parameter(names = "--database-host", required = true)
    String databaseHost;

    @Parameter(names = "--database-name", required = true)
    String databaseName;

    @Parameter(names = "--triggered-by", required = true)
    String triggeredBy;

    @Parameter(names = "--entity-type", required = true)
    String entityType;

    @Parameter(names = "--owned-by", required = true)
    String ownedBy;

    @Parameter(names = "--updated-after", required = true)
    String updatedAfter;

    @Parameter(names = "--subscription-url", required = true)
    String subscriptionUrl;

    @Parameter(names = "--limit")
    int limit = -1;

    @Parameter(names = "--max-concurrent-requests")
    private int maxConcurrentRequests = 50;

    Database database;

    Sender sender;

    public static void main( String[] args ) throws IOException, InterruptedException, URISyntaxException {
        Main main = new Main();
        JCommander.newBuilder()
            .addObject(main)
            .build()
            .parse(args);
        if (main.password == null) {
            main.password = System.getenv("PASSWORD");
            Objects.requireNonNull(main.password, "need a password");
        }
        main.run();
    }

    void run() throws IOException, InterruptedException, URISyntaxException {
        sender = new Sender(new URI(subscriptionUrl), entityType, triggeredBy);

        final Path workingPath = Paths.get(".").toAbsolutePath();
        System.out.println( "Working Directory: " + workingPath);
        final String url = String.format("jdbc:postgresql://%s:5432/%s", databaseHost, databaseName);
        NonBlockingConnectionPool pool = Pools.nonBlocking() //
            .url(url) //
            .user(username)
            .password(password)
            .maxPoolSize(10) //
            .scheduler(Schedulers.io())
            .build();
        database = Database.from(pool);

        String query = sql();

        final Flowable<PersonRecord> flowable = database
            .select(query)
            .parameter("owned_by", ownedBy)
            .parameter("updated_after", updatedAfter)
            .get((rs -> new PersonRecord(
                rs.getObject(1, UUID.class),
                Optional.ofNullable(rs.getObject(2, LocalDateTime.class))
            )));

        Counter counter = new Counter();
        final Flowable<PersonRecord> recordFlowable = flowable.doOnNext(counter);

        Set<CompletableFuture<Optional<UUID>>> futures = new HashSet<>();
        recordFlowable.blockingSubscribe(new DefaultSubscriber<PersonRecord>() {

            AtomicInteger level = new AtomicInteger(0);

            @Override
            protected void onStart() {
                request(maxConcurrentRequests);
            }

            @Override
            public void onNext(PersonRecord record) {
                level.incrementAndGet();
                final CompletableFuture<Optional<UUID>> future =
                    sender.sendGrUpdateNotification(record)
                        .exceptionally(throwable -> {
                            LOG.error("http request failed", throwable);
                            return Optional.of(record.id);
                        })
                        .thenApply(optionalId -> {
                            if (optionalId.isPresent()) {
                                LOG.error("Could not sync id {}", optionalId.get());
                            }
                            request(1);
                            return optionalId;
                        });
                futures.add(future);
            }

            @Override
            public void onError(Throwable t) {
                LOG.error("Error reading records", t);
            }

            @Override
            public void onComplete() {
                counter.complete();
            }
        });

        LOG.info("Waiting to finish current http requests...");

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .join();

        LOG.info("Done");
        database.close();
    }

    private String sql() {
        String template = "select _id, _deleted_at\n" +
            "from {{entityType}}\n" +
            "where _system_id = uuid(:owned_by)\n" +
            "and _updated_at > :updated_after::timestamp\n" +
            "order by _updated_at asc";
        if (limit >= 0) {
            template = template + " limit " + limit;
        }

        return template.replace("{{entityType}}", entityType);
    }

    private void count(Flowable<UUID> flowable) {
        final Single<Long> count = flowable
            .count();

        final Long count1 = count.blockingGet();

        System.out.printf("count: " + count1);
    }

}
