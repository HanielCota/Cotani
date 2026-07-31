package com.cotani.task.impl.chain;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class CompletionFailure {
    private CompletionFailure() {}

    static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;

        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
