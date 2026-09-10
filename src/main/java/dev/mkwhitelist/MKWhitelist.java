package dev.mkwhitelist;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import java.util.Map;
import java.util.HashMap;

public class MKWhitelist extends JavaPlugin {

    private boolean onlineMode;
    private String botToken;
    private String guildId;
    private JDA jda;
    private boolean discordConnected = false;
    private DatabaseManager databaseManager;
    private final Map<String, PendingLink> pendingLinks = new HashMap<>();

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
                guild.upsertCommand("link", "Link your Minecraft account using the code")
                        .addOption(OptionType.STRING, "code", "The code shown when you tried to join the Minecraft server", true)
                        .queue();
            }

            jda.addEventListener(new LinkCommandListener(this));

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

    public String generateLinkCode(String minecraftUuid){
        String code =  generateRandomCode();
        pendingLinks.put(code, new PendingLink(minecraftUuid, System.currentTimeMillis()));
        return code;
    }

    private String generateRandomCode(){
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        java.util.Random random = new java.util.Random();

        for (int i=0; i<6; i++){
            int index = random.nextInt(characters.length());
            code.append(characters.charAt(index));
        }

        return code.toString();
    }

    public String redeemLinkCode(String code) {
        PendingLink pendingLink = pendingLinks.get(code);

        if (pendingLink == null){
            return null;
        }
        long ageInMillis = System.currentTimeMillis() - pendingLink.getCreatedAt();
        long tenMinutesInMillis = 10*60*1000;

        if (ageInMillis > tenMinutesInMillis){
            pendingLinks.remove(code);
            return null;
        }

        pendingLinks.remove(code);
        return pendingLink.getMinecraftUuid();
    }

}

