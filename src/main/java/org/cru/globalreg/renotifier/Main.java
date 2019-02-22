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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Parameter(names = "--triggeredBy", required = true)
    String triggeredBy;

    @Parameter(names = "--entity-type", required = true)
    String entityType;

    @Parameter(names = "--subscription-url", required = true)
    String subscriptionUrl;

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

        String query = Files.readString(workingPath.resolve(Path.of("src/main/sql/query.sql")));

        final Flowable<UUID> flowable = database.select(query).getAs(UUID.class);

        Counter counter = new Counter();
        final Flowable<UUID> uuidFlowable = flowable.doOnNext(counter);

        Set<CompletableFuture<Optional<UUID>>> futures = new HashSet<>();
        uuidFlowable.blockingSubscribe(new DefaultSubscriber<UUID>() {

            AtomicInteger level = new AtomicInteger(0);

            @Override
            protected void onStart() {
                request(maxConcurrentRequests);
            }

            @Override
            public void onNext(UUID uuid) {
                level.incrementAndGet();
                final CompletableFuture<Optional<UUID>> future =
                    sender.sendGrUpdateNotification(uuid)
                        .exceptionally(throwable -> {
                            LOG.error("http request failed", throwable);
                            return Optional.of(uuid);
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

    private void count(Flowable<UUID> flowable) {
        final Single<Long> count = flowable
            .count();

        final Long count1 = count.blockingGet();

        System.out.printf("count: " + count1);
    }
}
