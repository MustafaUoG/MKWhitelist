package dev.mkwhitelist;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

public class PreLoginListener implements Listener {

    private final MKWhitelist plugin;

    public PreLoginListener(MKWhitelist plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID playerUUID = event.getUniqueId();

        //checks for discord will go here, for now we will only log what we are seeing

        plugin.getLogger().info("Pre-Login check for UUID: " + playerUUID);
        plugin.getLogger().info("Server online-mode: " + plugin.isOnlineMode());
    }
}
