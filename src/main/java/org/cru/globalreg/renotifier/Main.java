package org.cru.globalreg.renotifier;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DefaultSubscriber;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.davidmoten.rx.jdbc.Database;
import org.davidmoten.rx.jdbc.pool.NonBlockingConnectionPool;
import org.davidmoten.rx.jdbc.pool.Pools;

public class Main {
    @Parameter(names = "--username")
    String username;

    @Parameter(names = "--password", password = true)
    String password;

    @Parameter(names = "--database-host")
    String databaseHost;

    @Parameter(names = "--database-name")
    String databaseName;

    @Parameter(names = "--triggeredBy")
    String triggeredBy;

    @Parameter(names = "--entity-type")
    String entityType;

    @Parameter(names = "--subscription-url")
    String subscriptionUrl;

    Database database;

    public static void main( String[] args ) throws IOException, InterruptedException {
        Main main = new Main();
        JCommander.newBuilder()
            .addObject(main)
            .build()
            .parse(args);
        main.run();
    }

    void run() throws IOException, InterruptedException {
        System.out.println( "Hello World!" );
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
        final Flowable<UUID> uuidFlowable = flowable
            .doOnNext(x -> System.out.println("emitted on " + Thread.currentThread().getName()));
//        uuidFlowable
//            .subscribe();

//        flowable.subscribe(s -> System.out.println(s));

        ExecutorService service = Executors.newCachedThreadPool();

        uuidFlowable.subscribe(new DefaultSubscriber<UUID>() {

            AtomicInteger level = new AtomicInteger(0);

            @Override
            protected void onStart() {
                request(10);
            }

            @Override
            public void onNext(UUID uuid) {
                level.incrementAndGet();
                service.submit(() -> {
                    try {
                        Thread.sleep(50);
                        System.out.println(level.getAndDecrement() + " on " + Thread.currentThread().getName());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    request(1);
                });
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onComplete() {
            }
        });

//        uuidFlowable.blockingSubscribe();

//        flowable.blockingForEach(System.out::println);
        count(flowable);

        Thread.sleep(5000);

        service.shutdown();
        service.awaitTermination(5, TimeUnit.SECONDS);

        database.close();
    }

    private void count(Flowable<UUID> flowable) {
        final Single<Long> count = flowable
            .count();

        final Long count1 = count.blockingGet();

        System.out.printf("count: " + count1);
    }
}
