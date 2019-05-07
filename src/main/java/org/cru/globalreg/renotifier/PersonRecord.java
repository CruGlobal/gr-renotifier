package org.cru.globalreg.renotifier;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class PersonRecord {
    public final UUID id;
    public final Optional<LocalDateTime> deletedAt;

    public PersonRecord(UUID id, Optional<LocalDateTime> deletedAt) {
        this.id = id;
        this.deletedAt = deletedAt;
    }
}
