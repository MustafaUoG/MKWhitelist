package dev.mkwhitelist;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;


public class DatabaseManager {

    private Connection connection;

    public DatabaseManager(String databasePath) {
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            createTable();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS linked_accounts (" +
                "minecraft_uuid TEXT PRIMARY KEY," +
                "discord_id TEXT NOT NULL" +
                ");";

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }


    public void linkAccount(String minecraftUuid, String discordId) {
        String sql = "INSERT OR REPLACE INTO linked_accounts (minecraft_uuid, discord_id) VALUES (?,?);";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, minecraftUuid);
            statement.setString(2, discordId);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getLinkedDiscordId(String minecraftUuid) {
        String sql = "SELECT discord_id FROM linked_accounts WHERE minecraft_uuid = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, minecraftUuid);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getString("discord_id");
            } else {
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Returns true if the player was linked (and is now unlinked)
    public boolean unlinkAccount(String minecraftUuid) {
        String sql = "DELETE FROM linked_accounts WHERE minecraft_uuid = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, minecraftUuid);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Unlinks every Minecraft account linked to this Discord user and returns their UUIDs
    public List<String> unlinkDiscordAccount(String discordId) {
        List<String> minecraftUuids = new ArrayList<>();
        String selectSql = "SELECT minecraft_uuid FROM linked_accounts WHERE discord_id = ?;";
        String deleteSql = "DELETE FROM linked_accounts WHERE discord_id = ?;";

        try (PreparedStatement select = connection.prepareStatement(selectSql);
             PreparedStatement delete = connection.prepareStatement(deleteSql)) {
            select.setString(1, discordId);
            ResultSet resultSet = select.executeQuery();
            while (resultSet.next()) {
                minecraftUuids.add(resultSet.getString("minecraft_uuid"));
            }

            delete.setString(1, discordId);
            delete.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return minecraftUuids;
    }

    public int countLinkedAccounts() {
        String sql = "SELECT COUNT(*) FROM linked_accounts;";

        try (Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(sql);
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}