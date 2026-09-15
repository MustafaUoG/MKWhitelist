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
    private volatile boolean jdaSetupComplete = false;


    @Override
    public void onEnable(){
        //startup logic will be written here
        this.onlineMode = Bukkit.getOnlineMode();
        getLogger().info("Server online-mode detected: " + onlineMode);

        saveDefaultConfig();
        this.botToken = getConfig().getString("discord.bot-token");
        this.guildId = getConfig().getString("discord.guild-id");
        if(!validateConfig()){
            getLogger().severe("Discord setup is not complete, the plugin will not allow any players to join until setup is complete");
            return;
        }

        this.databaseManager = new DatabaseManager(getDataFolder() + "/linked_accounts.db");

        connectToDiscord();

        getServer().getPluginManager().registerEvents(new PreLoginListener(this), this);

    }

    private void connectToDiscord(){
        Thread connectionThread = new Thread(() -> {
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
                            .addOption(OptionType.STRING,"code", "The code shown when you tried to join the Minecraft server", true)
                            .queue();
                }   else {
                    getLogger().severe("Connected to Discord, but this bot is not a member of the server with ID " + guildId + ". Make sure you've invited the bot using the OAuth2 URL from the Developer Portal.");
                }

                jda.addEventListener(new LinkCommandListener(this));

                this.discordConnected = true;
                getLogger().info("Discord bot connected successfully.");
            }   catch (Exception e){
                getLogger().severe("Failed to connect Discord bot: " + e.getMessage());
            }   finally {
                jdaSetupComplete = true;
            }
        });

        connectionThread.setDaemon(true);
        connectionThread.start();

        long timeoutMillis = 20000;
        long startTime = System.currentTimeMillis();

        while (!jdaSetupComplete && (System.currentTimeMillis() - startTime) < timeoutMillis) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }

        if (!jdaSetupComplete){
            getLogger().severe("Discord connection timed out after 20 seconds. This usually means: (1) the 'Server Members Intent' is enabled in your code but NOT toggled on in the Discord Developer Portal, or (2) there's no internet connection. Check the Bot page in the Developer Portal and make sure 'Server Member Intent' is enabled.");
        }
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

    private boolean validateConfig(){
        boolean valid = true;

        if (botToken == null || botToken.isBlank()){
            getLogger().severe("Your config.yml file is missing the bot token, please add it under discord.bot-token");
            valid = false;
        } else if(botToken.equals("PASTE_YOUR_BOT_TOKEN_HERE")){
            getLogger().severe("You have not replaced the placeholder bot token in the config.yml");
            valid = false;
        }

        if (guildId == null || guildId.isBlank()){
            getLogger().severe("Your config.yml file is missing the guild id, please add it under discord.guild-id");
            valid = false;
        } else if(guildId.equals("PASTE_YOUR_DISCORD_SERVER_ID_HERE")){
            getLogger().severe("You have not replaced the placeholder guild id in the config.yml file");
            valid = false;
        } else if(!guildId.matches("\\d+")){
            getLogger().severe("Your guild ID does not appear to be a valid Discord guild ID. Right-click your server icon in Discord (with Developer Mode enabled) and choose 'Copy Server ID'.");
            valid = false;
        }
        return valid;
    }

    public String redeemLinkCode(String code) {
        PendingLink pendingLink = pendingLinks.get(code.toUpperCase());

        if (pendingLink == null){
            return null;
        }
        long ageInMillis = System.currentTimeMillis() - pendingLink.getCreatedAt();
        long tenMinutesInMillis = 10*60*1000;

        if (ageInMillis > tenMinutesInMillis){
            pendingLinks.remove(code.toUpperCase());
            return null;
        }

        pendingLinks.remove(code.toUpperCase());
        return pendingLink.getMinecraftUuid();
    }

}

