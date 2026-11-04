package dev.geco.gmusic.service;

import dev.geco.gmusic.GMusicMain;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class DataService {

    protected final int MAX_RETRIES = 3;

    private final GMusicMain gMusicMain;
    private Connection connection;
    private String type = null;
    private String host = null;
    private String port = null;
    private String database = null;
    private String user = null;
    private String password = null;
    private int retries = 0;

    public DataService(GMusicMain gMusicMain) {
        this.gMusicMain = gMusicMain;
    }

    /** 試験用: 既に開いた接続を使う。 */
    DataService(Connection connection, String type) {
        this.gMusicMain = null;
        this.connection = connection;
        this.type = type;
    }

    public synchronized boolean connect() {
        if(isConnected()) return true;
        File dataFile = new File(gMusicMain.getDataFolder(), "data/data.yml");
        if(!dataFile.exists()) gMusicMain.saveResource("data/data.yml", false);
        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        type = dataConfig.getString("Database.type", "sqlite").toLowerCase();
        host = dataConfig.getString("Database.host", "");
        port = dataConfig.getString("Database.port", "");
        database = dataConfig.getString("Database.database", "");
        user = dataConfig.getString("Database.user", "");
        password = dataConfig.getString("Database.password", "");
        return reconnect();
    }

    public boolean isConnected() {
        try {
            if(connection != null && !connection.isClosed() && connection.isValid(5)) return true;
        } catch(SQLException ignored) { }
        return false;
    }

    private boolean reconnect() {
        try {
            if(type.equals("sqlite")) Class.forName("org.sqlite.JDBC");
            connection = getConnection(false);
            if(connection != null) {
                if(!type.equals("sqlite")) connection = getConnection(true);
                if(connection != null) {
                    retries = 0;
                    return true;
                }
            }
        } catch(Throwable e) { gMusicMain.getLogger().log(Level.SEVERE, "Could not connect to database!", e); }
        if(retries == MAX_RETRIES) return false;
        retries++;
        return reconnect();
    }

    private Connection getConnection(boolean withDatabase) throws SQLException {
        switch(type) {
            case "mysql":
                return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + (withDatabase ? "/" + database : "") + "?createDatabaseIfNotExist=true&useUnicode=true", user, password);
            case "sqlite":
                return DriverManager.getConnection("jdbc:sqlite:" + new File(gMusicMain.getDataFolder(), "data/data.db").getPath());
        }
        return null;
    }

    /*
     * 接続は 1 本を共有するため、メインスレッドと非同期保存の両方から使えるよう
     * すべての入口を synchronized にする。
     */
    public synchronized void execute(String query, Object... parameters) throws SQLException {
        ensureConnection();
        try(PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            for(int i = 1; i <= parameters.length; i++) preparedStatement.setObject(i, parameters[i - 1]);
            preparedStatement.executeUpdate();
        }
    }

    public synchronized ResultSet executeAndGet(String query, Object... parameters) throws SQLException {
        ensureConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(query);
        for(int i = 1; i <= parameters.length; i++) preparedStatement.setObject(i, parameters[i - 1]);
        // ResultSet を閉じたときに PreparedStatement も閉じる（以前は文が閉じられず溜まっていた）
        preparedStatement.closeOnCompletion();
        return preparedStatement.executeQuery();
    }

    @FunctionalInterface
    public interface SqlWork { void run() throws SQLException; }

    /**
     * work の中の execute をひとつのトランザクションにまとめる。途中で失敗したら全部取り消す。
     * お気に入りの「全部消してから入れ直す」保存が途中で止まっても、消えたままにならない。
     */
    public synchronized void transaction(SqlWork work) throws SQLException {
        ensureConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            work.run();
            connection.commit();
        } catch(SQLException | RuntimeException e) {
            try { connection.rollback(); } catch(SQLException ignored) { }
            throw e;
        } finally {
            try { connection.setAutoCommit(autoCommit); } catch(SQLException ignored) { }
        }
    }

    private void ensureConnection() throws SQLException {
        if(isConnected()) return;
        if(reconnect()) return;
        if(!reconnect()) throw new SQLException("Failed to reconnect to the " + type + " database.");
    }

    public synchronized void close() { try { if(connection != null && !connection.isClosed()) connection.close(); } catch(SQLException ignored) { } }

}