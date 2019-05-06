package org.cru.globalreg.renotifier;

import io.reactivex.functions.Consumer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Counter implements Consumer<PersonRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(Counter.class);
    final int secondsBetweenLogRecords = 10;
    AtomicInteger counter = new AtomicInteger(0);
    AtomicInteger previous = new AtomicInteger(0);
    AtomicReference<Instant> next = new AtomicReference<>(Instant.now().plusSeconds(secondsBetweenLogRecords));

    @Override
    public void accept(PersonRecord record) throws Exception {
        final int snapshotOfCounter = counter.incrementAndGet();
        Instant currentNow = Instant.now();
        Instant snapshotNext = next.get();
        if (currentNow.isAfter(snapshotNext)) {
            Instant newNext = snapshotNext.plusSeconds(secondsBetweenLogRecords);
            final Instant maybeDifferentSnapshotNext = next.compareAndExchange(snapshotNext, newNext);
            if (maybeDifferentSnapshotNext == snapshotNext) {
                int snapshotOfPrevious = previous.get();
                final int difference = snapshotOfCounter - snapshotOfPrevious;
                double rate = difference / (double) secondsBetweenLogRecords;
                LOG.info("read {} records total; recently {} at {} per second", snapshotOfCounter, difference, rate);
                previous.set(snapshotOfCounter);
            }
        }
    }

    public void complete() {
        Instant currentNow = Instant.now();
        Instant previousCheckpoint = next.get().minusSeconds(secondsBetweenLogRecords);
        final double finalCheckpointTime = Duration.between(previousCheckpoint, currentNow).toMillis() / 1000.0;
        final int snapshotOfCounter = counter.get();
        final int diff = snapshotOfCounter - previous.get();
        double rate = diff / finalCheckpointTime;
        LOG.info("completed; read {} records total; recently {} at {} per second", snapshotOfCounter, diff, rate);
    }

}
