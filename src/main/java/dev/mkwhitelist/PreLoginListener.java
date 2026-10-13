package dev.mkwhitelist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        if (!plugin.isDiscordConnected()){
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Server setup incomplete. Please contact an admin."));
            return;
        }

        String discordId = plugin.getDatabaseManager().getLinkedDiscordId(playerUUID.toString());

        if (discordId == null){
            String code = plugin.generateLinkCode(playerUUID.toString());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("You need to link your Discord Account first!\n" + "Join our discord server and run ")
                    .append(Component.text("/link " + code, NamedTextColor.GOLD)));
            return;
        }

        if (!plugin.isMemberofGuild(discordId)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("You must be a member of our Discord server to join!"));
            return;
        }



        plugin.getLogger().info("Player " + playerUUID + " has passed the whitelist check.");
    }
}
