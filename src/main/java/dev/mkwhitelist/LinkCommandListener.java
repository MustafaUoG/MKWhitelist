package dev.mkwhitelist;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class LinkCommandListener extends ListenerAdapter{

    private final MKWhitelist plugin;

    public LinkCommandListener(MKWhitelist plugin){
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
        if (!event.getName().equals("link")){
            return;
        }

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
}
