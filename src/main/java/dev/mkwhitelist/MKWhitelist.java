package dev.mkwhitelist;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.entities.Guild;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;

public class MKWhitelist extends JavaPlugin {

    private static final long LINK_CODE_LIFETIME_MILLIS = 10 * 60 * 1000;
    private static final String CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private boolean onlineMode;
    private volatile String botToken;
    private volatile String guildId;
    private volatile JDA jda;
    private volatile boolean discordConnected = false;
    private DatabaseManager databaseManager;
    // Codes are created on the async pre-login thread and redeemed on JDA's thread, so this map must be thread-safe
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();
    // Bumped on every connect/disconnect, so a slow connection attempt that has since been replaced knows to shut itself down
    private final AtomicInteger connectionAttempt = new AtomicInteger();
    private final Object discordLock = new Object();


    @Override
    public void onEnable(){
        this.onlineMode = Bukkit.getOnlineMode();
        getLogger().info("Server online-mode detected: " + onlineMode);
        if (!onlineMode){
            getLogger().warning("This server is in offline mode, so Minecraft accounts are not verified by Mojang. Unless you're behind a proxy (Velocity/BungeeCord) that handles login, anyone can join using a linked player's name.");
        }

        saveDefaultConfig();
        this.databaseManager = new DatabaseManager(getDataFolder() + "/linked_accounts.db");

        // Registered even when the config is broken, so nobody slips in while setup is incomplete
        // and admins can fix config.yml and run /mkwhitelist reload without restarting
        getServer().getPluginManager().registerEvents(new PreLoginListener(this), this);
        registerCommand("mkwhitelist", "Manage MKWhitelist", new AdminCommand(this));

        loadDiscordSettings();
        if (validateConfig()){
            connectToDiscord();
        } else {
            getLogger().severe("Discord setup is not complete, the plugin will not allow any players to join until setup is complete");
        }

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                checkPlayer(player);
            }
        }, 20L * 90, 20L * 90);

        // Throw away codes nobody redeemed so they don't pile up in memory
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::purgeExpiredLinkCodes, 20L * 60, 20L * 60);
    }

    private void loadDiscordSettings(){
        this.botToken = getConfig().getString("discord.bot-token");
        this.guildId = getConfig().getString("discord.guild-id");
    }

    private void connectToDiscord(){
        int attempt = connectionAttempt.incrementAndGet();
        // Copied so a reload while we're still connecting can't change them halfway through
        String token = botToken;
        String targetGuildId = guildId;
        AtomicBoolean finished = new AtomicBoolean(false);

        Thread connectionThread = new Thread(() -> {
            JDA newJda = null;
            boolean connected = false;
            try {
                newJda = JDABuilder.createDefault(token)
                        .enableIntents(GatewayIntent.GUILD_MEMBERS)
                        .setMemberCachePolicy(MemberCachePolicy.ALL)
                        .addEventListeners(new LinkCommandListener(this))
                        .build();
                if (!claimJda(attempt, newJda)) {
                    return;
                }
                newJda.awaitReady();

                Guild guild = newJda.getGuildById(targetGuildId);
                if (guild == null) {
                    getLogger().severe("Connected to Discord, but this bot is not a member of the server with ID " + targetGuildId + ". Invite the bot using the OAuth2 URL from the Developer Portal, then run /mkwhitelist reload.");
                    return;
                }

                guild.loadMembers().get();
                guild.updateCommands().addCommands(
                        Commands.slash("link", "Link your Minecraft account using the code")
                                .addOption(OptionType.STRING, "code", "The code shown when you tried to join the Minecraft server", true),
                        Commands.slash("unlink", "Unlink your Minecraft account from your Discord account")
                ).queue();

                synchronized (discordLock) {
                    if (attempt == connectionAttempt.get()) {
                        this.discordConnected = true;
                        connected = true;
                    }
                }
                if (connected) {
                    getLogger().info("Discord bot connected successfully.");
                }
            }   catch (Exception e){
                // A replaced attempt gets shut down on purpose, so its errors aren't worth reporting
                if (attempt == connectionAttempt.get()) {
                    getLogger().severe("Failed to connect Discord bot: " + e.getMessage());
                }
            }   finally {
                finished.set(true);
                if (!connected && newJda != null) {
                    newJda.shutdownNow();
                }
            }
        });

        connectionThread.setDaemon(true);
        connectionThread.start();

        // Check back later instead of freezing the server while we wait
        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
            if (!finished.get() && attempt == connectionAttempt.get()) {
                getLogger().severe("Discord connection timed out after 20 seconds. This usually means: (1) the 'Server Members Intent' is enabled in your code but NOT toggled on in the Discord Developer Portal, or (2) there's no internet connection. Check the Bot page in the Developer Portal and make sure 'Server Member Intent' is enabled.");
            }
        }, 20L * 20);
    }

    // Makes newJda the current bot, unless a newer connect/disconnect has replaced this attempt
    private boolean claimJda(int attempt, JDA newJda){
        synchronized (discordLock) {
            if (attempt != connectionAttempt.get()) {
                return false;
            }
            this.jda = newJda;
            return true;
        }
    }

    private void disconnectFromDiscord(){
        synchronized (discordLock) {
            connectionAttempt.incrementAndGet();
            discordConnected = false;
            if (jda != null) {
                jda.shutdownNow();
                jda = null;
            }
        }
    }

    /**
     * Re-reads config.yml and reconnects the bot if the Discord settings changed or it isn't connected.
     * Returns false if the config is invalid, in which case the bot is left disconnected.
     */
    public boolean reloadDiscordSettings(){
        String oldToken = botToken;
        String oldGuildId = guildId;

        reloadConfig();
        loadDiscordSettings();

        if (!validateConfig()) {
            disconnectFromDiscord();
            return false;
        }

        boolean settingsChanged = !botToken.equals(oldToken) || !guildId.equals(oldGuildId);
        if (settingsChanged || !discordConnected) {
            disconnectFromDiscord();
            connectToDiscord();
        }
        return true;
    }

    public String getBotToken(){
        return botToken;
    }

    public String getGuildId(){
        return guildId;
    }

    @Override
    public void onDisable(){
        disconnectFromDiscord();
        if (databaseManager != null) {
            databaseManager.close();
        }
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

    // The Discord server we gate on, or null if the bot isn't connected or isn't in it
    public Guild getGuild(){
        JDA currentJda = jda;
        return currentJda == null ? null : currentJda.getGuildById(guildId);
    }

    public boolean isMemberofGuild(String discordUserId){
        Guild guild = getGuild();

        if (guild == null){
            getLogger().severe("Could not find Discord guild with ID: " + guildId + ". Check your config.yml.");
            return false;
        }

        return guild.getMemberById(discordUserId) != null;
    }

    private void checkPlayer(Player player) {
        // While the bot is down or reconnecting we can't verify anyone, so leave online players alone.
        // New joins are still refused by PreLoginListener until it's back.
        if (!discordConnected) {
            return;
        }

        String discordId = databaseManager.getLinkedDiscordId(player.getUniqueId().toString());

        if (discordId == null) {
            player.kick(Component.text("Your account is no longer linked. Please link again to continue playing."));
            return;
        }

        if (!isMemberofGuild(discordId)) {
            player.kick(Component.text("You are no longer a member of our Discord server."));
        }
    }

    // Safe to call from any thread, the kick itself always happens on the main server thread
    public void kickIfOnline(String minecraftUuid, String message){
        getServer().getScheduler().runTask(this, () -> {
            Player player = getServer().getPlayer(UUID.fromString(minecraftUuid));
            if (player != null) {
                player.kick(Component.text(message));
            }
        });
    }


    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public int getPendingLinkCount(){
        return pendingLinks.size();
    }

    public String generateLinkCode(String minecraftUuid){
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().getMinecraftUuid().equals(minecraftUuid));

        PendingLink pendingLink = new PendingLink(minecraftUuid, System.currentTimeMillis());
        String code;
        // putIfAbsent so a duplicate code (very unlikely) can never overwrite someone else's pending link
        do {
            code = generateRandomCode();
        } while (pendingLinks.putIfAbsent(code, pendingLink) != null);
        return code;
    }

    private String generateRandomCode(){
        StringBuilder code = new StringBuilder();

        for (int i=0; i<6; i++){
            int index = RANDOM.nextInt(CODE_CHARACTERS.length());
            code.append(CODE_CHARACTERS.charAt(index));
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
        // remove() looks up and consumes the code in one step, so the same code can't be redeemed twice
        PendingLink pendingLink = pendingLinks.remove(code.trim().toUpperCase());

        if (pendingLink == null || isExpired(pendingLink)){
            return null;
        }

        return pendingLink.getMinecraftUuid();
    }

    private boolean isExpired(PendingLink pendingLink){
        return System.currentTimeMillis() - pendingLink.getCreatedAt() > LINK_CODE_LIFETIME_MILLIS;
    }

    private void purgeExpiredLinkCodes(){
        pendingLinks.values().removeIf(this::isExpired);
    }

}
