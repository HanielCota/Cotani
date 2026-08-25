package com.cotani.dialog.internal;

import com.cotani.api.InternalApi;
import com.cotani.dialog.api.CancelReason;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Objects;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@InternalApi
public final class DialogChatListener implements Listener {

    private final DefaultDialogService dialogService;

    public DialogChatListener(DefaultDialogService dialogService) {
        this.dialogService = Objects.requireNonNull(dialogService, "dialogService");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPaperChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        var activePrompt = dialogService.getActivePrompt(player.getUniqueId());
        if (activePrompt instanceof DefaultChatPrompt<?> chatPrompt) {
            event.setCancelled(true);
            String rawText = PlainTextComponentSerializer.plainText().serialize(event.message());
            chatPrompt.handleInput(player, rawText);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        dialogService.cancelPrompt(event.getPlayer().getUniqueId(), CancelReason.PLAYER_QUIT);
    }
}
