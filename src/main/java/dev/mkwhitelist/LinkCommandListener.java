package dev.mkwhitelist;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class LinkCommandListener extends ListenerAdapter{

    private final MKWhitelist plugin;

    public LinkCommandListener(MKWhitelist plugin){
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
        if (event.getName().equals("link")){
            handleLink(event);
        } else if (event.getName().equals("unlink")){
            handleUnlink(event);
        }
    }

    private void handleLink(SlashCommandInteractionEvent event){
        String code = event.getOption("code").getAsString();
        String discordId = event.getUser().getId();
        String minecraftUuid = plugin.redeemLinkCode(code);

        if (minecraftUuid == null){
            event.reply("Invalid or expired code, please try joining the Minecraft server again to get a new code.").setEphemeral(true).queue();
            return;
        }

        plugin.getDatabaseManager().linkAccount(minecraftUuid, discordId);
        event.reply("Your Minecraft account has been linked successfully! You can now join the server!").setEphemeral(true).queue();
    }

    private void handleUnlink(SlashCommandInteractionEvent event){
        String discordId = event.getUser().getId();
        List<String> unlinkedUuids = plugin.getDatabaseManager().unlinkDiscordAccount(discordId);

        if (unlinkedUuids.isEmpty()){
            event.reply("You don't have a Minecraft account linked.").setEphemeral(true).queue();
            return;
        }

        for (String minecraftUuid : unlinkedUuids){
            plugin.kickIfOnline(minecraftUuid, "Your Discord account was unlinked. Please link again to continue playing.");
        }
        event.reply("Your Minecraft account has been unlinked. You'll need to link again before you can rejoin the server.").setEphemeral(true).queue();
    }
}
