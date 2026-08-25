package com.cotani.ranking.api;

/** Base exception for expected ranking-domain failures. */
public class RankingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RankingException(String message) {
        super(message);
    }
}
