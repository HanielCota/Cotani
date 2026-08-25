package com.cotani.job.internal;

import com.cotani.job.api.JobExecutionId;
import com.cotani.job.api.JobId;
import com.cotani.job.api.JobRequest;
import com.cotani.job.api.JobRetryPolicy;
import com.cotani.job.api.JobSchedule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

@com.cotani.api.InternalApi
@SuppressWarnings("ArrayRecordComponent")
record JobEnvelope(
        JobId jobId,
        JobExecutionId executionId,
        String handlerName,
        byte[] payload,
        JobSchedule schedule,
        JobRetryPolicy retryPolicy,
        int attempt) {
    private static final int MAGIC = 0x434A4F42;
    private static final int VERSION = 2;

    JobEnvelope {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(handlerName, "handlerName");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        validateHandlerName(handlerName);
        if (payload.length > com.cotani.task.persistence.PersistentTask.MAX_PAYLOAD_BYTES / 2) {
            throw new IllegalArgumentException("payload is too large for persistent job metadata");
        }
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    static JobEnvelope from(JobId jobId, JobRequest request, int attempt) {
        return new JobEnvelope(
                jobId,
                JobExecutionId.random(),
                request.handlerName(),
                request.payload(),
                request.schedule(),
                request.retryPolicy(),
                attempt);
    }

    JobEnvelope nextAttempt(int attempt) {
        return new JobEnvelope(jobId, executionId, handlerName, payload, schedule, retryPolicy, attempt);
    }

    JobEnvelope nextOccurrence() {
        return new JobEnvelope(jobId, JobExecutionId.random(), handlerName, payload, schedule, retryPolicy, 1);
    }

    byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeLong(jobId.value().getMostSignificantBits());
                output.writeLong(jobId.value().getLeastSignificantBits());
                output.writeLong(executionId.value().getMostSignificantBits());
                output.writeLong(executionId.value().getLeastSignificantBits());
                output.writeUTF(handlerName);
                output.writeInt(attempt);
                output.writeUTF(retryPolicy.initialBackoff().toString());
                output.writeUTF(retryPolicy.maximumBackoff().toString());
                output.writeDouble(retryPolicy.multiplier());
                output.writeInt(retryPolicy.maxAttempts());
                switch (schedule) {
                    case JobSchedule.Once once -> {
                        output.writeByte(0);
                        output.writeUTF(once.delay().toString());
                        output.writeUTF("");
                    }
                    case JobSchedule.Recurring recurring -> {
                        output.writeByte(1);
                        output.writeUTF(recurring.initialDelay().toString());
                        output.writeUTF(recurring.interval().toString());
                    }
                }
                output.writeInt(payload.length);
                output.write(payload);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode job metadata", exception);
        }
    }

    static JobEnvelope decode(byte[] encoded) {
        return decode(encoded, JobExecutionId.random());
    }

    static JobEnvelope decode(byte[] encoded, JobExecutionId legacyExecutionId) {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(legacyExecutionId, "legacyExecutionId");
        try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Unsupported job metadata version");
            }
            var version = input.readInt();
            if (version != 1 && version != VERSION) {
                throw new IllegalArgumentException("Unsupported job metadata version: " + version);
            }
            var jobId = new JobId(new java.util.UUID(input.readLong(), input.readLong()));
            var executionId = version == 1
                    ? legacyExecutionId
                    : new JobExecutionId(new java.util.UUID(input.readLong(), input.readLong()));
            var handlerName = input.readUTF();
            var attempt = input.readInt();
            var initialBackoff = Duration.parse(input.readUTF());
            var maximumBackoff = Duration.parse(input.readUTF());
            var multiplier = input.readDouble();
            var maxAttempts = input.readInt();
            var scheduleType = input.readByte();
            var firstDelay = Duration.parse(input.readUTF());
            var interval = input.readUTF();
            var schedule =
                    switch (scheduleType) {
                        case 0 -> new JobSchedule.Once(firstDelay);
                        case 1 -> new JobSchedule.Recurring(firstDelay, Duration.parse(interval));
                        default -> throw new IllegalArgumentException("Unknown job schedule type: " + scheduleType);
                    };
            var payloadLength = input.readInt();
            if (payloadLength < 0 || payloadLength > encoded.length) {
                throw new IllegalArgumentException("Invalid job payload length");
            }
            var payload = input.readNBytes(payloadLength);
            if (payload.length != payloadLength || input.available() != 0) {
                throw new IllegalArgumentException("Malformed job metadata");
            }
            return new JobEnvelope(
                    jobId,
                    executionId,
                    handlerName,
                    payload,
                    schedule,
                    new JobRetryPolicy(maxAttempts, initialBackoff, maximumBackoff, multiplier),
                    attempt);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Malformed job metadata", exception);
        }
    }

    private static void validateHandlerName(String name) {
        if (name.isBlank() || name.length() > com.cotani.job.api.JobRequest.MAX_HANDLER_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid job handler name");
        }
        if (name.indexOf(':') >= 0 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid job handler name");
        }
    }
}
