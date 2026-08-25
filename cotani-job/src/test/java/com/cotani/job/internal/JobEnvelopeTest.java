package com.cotani.job.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.job.api.JobId;
import com.cotani.job.api.JobRequest;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobEnvelopeTest {
    @Test
    void roundTripPreservesExecutionIdentity() {
        var jobId = JobId.random();
        var envelope = JobEnvelope.from(jobId, JobRequest.once("backup", new byte[] {1, 2}, Duration.ZERO), 1);

        var decoded = JobEnvelope.decode(envelope.encode());

        assertEquals(jobId, decoded.jobId());
        assertEquals(envelope.executionId(), decoded.executionId());
        assertEquals(envelope.payload().length, decoded.payload().length);
    }

    @Test
    void rejectsUnknownScheduleTypes() throws IOException {
        var encoded = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(encoded)) {
            output.writeInt(0x434A4F42);
            output.writeInt(2);
            writeUuid(output, UUID.randomUUID());
            writeUuid(output, UUID.randomUUID());
            output.writeUTF("backup");
            output.writeInt(1);
            output.writeUTF("PT1S");
            output.writeUTF("PT1M");
            output.writeDouble(2.0d);
            output.writeInt(3);
            output.writeByte(9);
            output.writeUTF("PT0S");
            output.writeUTF("");
            output.writeInt(0);
        }

        assertThrows(IllegalArgumentException.class, () -> JobEnvelope.decode(encoded.toByteArray()));
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }
}
