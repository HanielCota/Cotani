package com.cotani.audit;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.audit.api.AuditAction;
import com.cotani.audit.api.AuditActor;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditSeverity;
import com.cotani.audit.api.AuditTarget;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryTest {
    @Test
    void rejectsDuplicateNormalizedDetailKeys() {
        var details = new HashMap<String, String>();
        details.put("reason", "first");
        details.put(" reason ", "second");

        assertThrows(IllegalArgumentException.class, () -> entry(details));
    }

    @Test
    void rejectsOversizedDetailValues() {
        var value = "x".repeat(AuditEntry.MAX_DETAIL_VALUE_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> entry(Map.of("reason", value)));
    }

    private static AuditEntry entry(Map<String, String> details) {
        return new AuditEntry(
                UUID.randomUUID(),
                Instant.EPOCH,
                AuditActor.system(),
                AuditAction.of("test"),
                AuditTarget.resource("test", "target"),
                AuditSeverity.INFO,
                details);
    }
}
