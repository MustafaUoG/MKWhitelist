package dev.mkwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class MKWhitelist extends JavaPlugin {

    private boolean onlineMode;

    @Override
    public void onEnable(){
        //startup logic will be written here
        this.onlineMode = Bukkit.getOnlineMode();
        getLogger().info("Server online-mode detected: " + onlineMode);

        getServer().getPluginManager().registerEvents(new PreLoginListener(this), this);

    }

    @Override
    public void onDisable(){
        //shutdown logic will be written here
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }
}
