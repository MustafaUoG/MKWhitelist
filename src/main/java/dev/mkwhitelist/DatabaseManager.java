package dev.mkwhitelist;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;


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

}