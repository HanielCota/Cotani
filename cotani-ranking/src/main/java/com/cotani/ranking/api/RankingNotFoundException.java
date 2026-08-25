package com.cotani.ranking.api;

import java.util.Objects;

/** Raised when a query references a ranking that has not been registered. */
public final class RankingNotFoundException extends RankingException {
    private static final long serialVersionUID = 1L;

    private final transient RankingId rankingId;

    public RankingNotFoundException(RankingId rankingId) {
        super("Ranking is not registered: "
                + Objects.requireNonNull(rankingId, "rankingId").value());
        this.rankingId = rankingId;
    }

    public RankingId rankingId() {
        return rankingId;
    }
}
