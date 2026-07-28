package com.cotani.teleport.api;

import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TeleportService {
    CompletionStage<TeleportResult> teleport(TeleportRequest request);
}
