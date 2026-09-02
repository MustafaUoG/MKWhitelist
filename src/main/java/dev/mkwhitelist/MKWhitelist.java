package dev.mkwhitelist;

import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class MKWhitelist extends JavaPlugin {

    private boolean onlineMode;
    private String botToken;
    private String guildId;
    private JDA jda;
    private boolean discordConnected = false;
    private DatabaseManager databaseManager;


    @Override
    public void onEnable(){
        //startup logic will be written here
        this.onlineMode = Bukkit.getOnlineMode();
        getLogger().info("Server online-mode detected: " + onlineMode);

        saveDefaultConfig();
        this.botToken = getConfig().getString("discord.bot-token");
        this.guildId = getConfig().getString("discord.guild-id");

        this.databaseManager = new DatabaseManager(getDataFolder() + "/linked_accounts.db");

        try {
            this.jda = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .build();
            jda.awaitReady();

            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                guild.loadMembers().get();
            }

            this.discordConnected = true;
            getLogger().info("Discord bot connected successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to connect Discord bot: " + e.getMessage());
            //so that the discordConnected stays false
        }


        getServer().getPluginManager().registerEvents(new PreLoginListener(this), this);

    }

    public String getBotToken(){
        return botToken;
    }

    public String getGuildId(){
        return guildId;
    }

    @Override
    public void onDisable(){
        //shutdown logic will be written here
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }

    public JDA getJda() {
        return jda;
    }

    public boolean isDiscordConnected() {
        return discordConnected;
    }

    public boolean isMemberofGuild(String discordUserId){
        Guild guild = jda.getGuildById(guildId);

        if (guild == null){
            getLogger().severe("Could not find Discord guild with ID: " + guildId + ". Check your config.yml.");
            return false;
        }

        Member member = guild.getMemberById(discordUserId);
        return member != null;
    }
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}

