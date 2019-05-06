package org.cru.globalreg.renotifier;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SenderInterfaceTest {

    Sender sender;

    @BeforeEach
    void setUp() {
        sender = new Sender(
            URI.create("http://prodauth.aws.cru.org:4502/bin/crugive/designation-updates"),
            "designation",
            "siebel"
        );
    }

    @Test
    void sendGrUpdateNotification() throws ExecutionException, InterruptedException {
        final PersonRecord record = new PersonRecord(
            UUID.fromString("0b10bffe-df5a-11e5-9c2e-1228c4aab4b9"),
            Optional.empty()
        );
        final Optional<UUID> optionalBadUuid = sender.sendGrUpdateNotification(record).get();

        if (optionalBadUuid.isPresent()) {
            throw new AssertionError("failure to update " + optionalBadUuid.get());
        }
    }
}
