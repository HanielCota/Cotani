package com.cotani.quest.api.event;

import com.cotani.event.api.CotaniEvent;
import com.cotani.quest.api.QuestClaim;
import java.util.Objects;

/** Published after a quest claim is durably recorded. */
public record QuestClaimedEvent(QuestClaim claim) implements CotaniEvent {
    public QuestClaimedEvent {
        Objects.requireNonNull(claim, "claim");
    }
}
