package dev.mkwhitelist;

public class PendingLink {

    private final String minecraftUuid;
    private final long createdAt;

    public PendingLink(String minecraftUuid, long createdAt){
        this.minecraftUuid = minecraftUuid;
        this.createdAt = createdAt;
    }

    public String getMinecraftUuid(){
        return minecraftUuid;
    }

    public long getCreatedAt(){
        return createdAt;
    }
}
