package org.cru.globalreg.renotifier;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    Database database;

    public static void main( String[] args )
    {
        Main main = new Main();
        JCommander.newBuilder()
            .addObject(main)
            .build()
            .parse(args);
        main.run();
    }

    void run() {
        System.out.println( "Hello World!" );
        final Path workingPath = Paths.get(".").toAbsolutePath();
        System.out.println( "Working Directory: " + workingPath);
        final String url = String.format("jdbc:postgresql://%s:5432/%s", databaseHost, databaseName);
        NonBlockingConnectionPool pool = Pools.nonBlocking() //
            .url(url) //
            .user(username)
            .password(password)
            .maxPoolSize(10) //
            .build();
        database = Database.from(pool);

        database.close();
    }
}
