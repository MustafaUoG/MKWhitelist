package dev.mkwhitelist;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

// In-game /mkwhitelist command for server admins (permission: mkwhitelist.admin)
public class AdminCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of("status", "lookup", "unlink", "reload");

    private final MKWhitelist plugin;

    public AdminCommand(MKWhitelist plugin){
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args){
        CommandSender sender = source.getSender();

        if (args.length == 0){
            sendUsage(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> showStatus(sender);
            case "lookup" -> lookup(sender, args);
            case "unlink" -> unlink(sender, args);
            case "reload" -> reload(sender);
            default -> sendUsage(sender);
        }
    }

    private void showStatus(CommandSender sender){
        sender.sendMessage(Component.text("MKWhitelist status", NamedTextColor.GOLD));

        Guild guild = plugin.getGuild();
        if (plugin.isDiscordConnected() && guild != null){
            sender.sendMessage(Component.text(" Discord bot: connected to " + guild.getName(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(" Discord bot: not connected (nobody can join)", NamedTextColor.RED));
        }

        sender.sendMessage(Component.text(" Linked accounts: " + plugin.getDatabaseManager().countLinkedAccounts()));
        sender.sendMessage(Component.text(" Pending link codes: " + plugin.getPendingLinkCount()));
        sender.sendMessage(Component.text(" Server online-mode: " + plugin.isOnlineMode()));
    }

    private void lookup(CommandSender sender, String[] args){
        if (args.length < 2){
            sender.sendMessage(Component.text("Usage: /mkwhitelist lookup <player>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = findPlayer(sender, args[1]);
        if (target == null){
            return;
        }

        String discordId = plugin.getDatabaseManager().getLinkedDiscordId(target.getUniqueId().toString());
        if (discordId == null){
            sender.sendMessage(Component.text(target.getName() + " is not linked to a Discord account.", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text(target.getName() + " is linked to " + describeDiscordUser(discordId), NamedTextColor.GREEN));
    }

    private void unlink(CommandSender sender, String[] args){
        if (args.length < 2){
            sender.sendMessage(Component.text("Usage: /mkwhitelist unlink <player>", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = findPlayer(sender, args[1]);
        if (target == null){
            return;
        }

        String minecraftUuid = target.getUniqueId().toString();
        if (!plugin.getDatabaseManager().unlinkAccount(minecraftUuid)){
            sender.sendMessage(Component.text(target.getName() + " is not linked to a Discord account.", NamedTextColor.YELLOW));
            return;
        }

        if (target.isOnline()){
            plugin.kickIfOnline(minecraftUuid, "Your account was unlinked by an admin. Please link again to continue playing.");
            sender.sendMessage(Component.text("Unlinked " + target.getName() + " and kicked them from the server.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Unlinked " + target.getName() + ".", NamedTextColor.GREEN));
        }
    }

    private void reload(CommandSender sender){
        if (!plugin.reloadDiscordSettings()){
            sender.sendMessage(Component.text("config.yml has problems, check the console for details. Nobody can join until it's fixed.", NamedTextColor.RED));
            return;
        }

        if (plugin.isDiscordConnected()){
            sender.sendMessage(Component.text("Config reloaded. Discord settings are unchanged and the bot is still connected.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Config reloaded. Connecting to Discord, run /mkwhitelist status in a few seconds to check.", NamedTextColor.YELLOW));
        }
    }

    // Only finds players who have tried to join before, so we never trigger a slow Mojang lookup
    private OfflinePlayer findPlayer(CommandSender sender, String name){
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null){
            sender.sendMessage(Component.text("Couldn't find a player named " + name + ". They need to have tried joining this server at least once.", NamedTextColor.RED));
        }
        return target;
    }

    // e.g. "someone (123456789)", or just the ID if they can't be seen in the Discord server
    private String describeDiscordUser(String discordId){
        Guild guild = plugin.getGuild();
        if (!plugin.isDiscordConnected() || guild == null){
            return "Discord ID " + discordId;
        }

        Member member = guild.getMemberById(discordId);
        if (member == null){
            return "Discord ID " + discordId + " (not in the Discord server)";
        }
        return member.getUser().getName() + " (" + discordId + ")";
    }

    private void sendUsage(CommandSender sender){
        sender.sendMessage(Component.text("Usage: /mkwhitelist <status|lookup|unlink|reload> [player]", NamedTextColor.RED));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args){
        if (args.length <= 1){
            String typed = args.length == 0 ? "" : args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(subcommand -> subcommand.startsWith(typed)).toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("lookup") || args[0].equalsIgnoreCase("unlink"))){
            String typed = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(typed))
                    .toList();
        }

        return List.of();
    }

    @Override
    public String permission(){
        return "mkwhitelist.admin";
    }
}
