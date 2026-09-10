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


        if (!plugin.isDiscordConnected()){
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Server setup incomplete. Please contact an admin.");
            return;
        }

        String discordId = plugin.getDatabaseManager().getLinkedDiscordId(playerUUID.toString());

        if (discordId == null){
            String code = plugin.generateLinkCode(playerUUID.toString());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "You need to link your Discord Account first!\n" + "Join our discord server and run /link" + code);
            return;
        }

        if (!plugin.isMemberofGuild(discordId)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "You must be a member of our Discord server to join!");
            return;
        }


        plugin.getLogger().info("Pre-Login check for UUID: " + playerUUID);
        plugin.getLogger().info("Server online-mode: " + plugin.isOnlineMode());
        plugin.getLogger().info("Player " + playerUUID + "has passed the whitelist check.");
    }
}
