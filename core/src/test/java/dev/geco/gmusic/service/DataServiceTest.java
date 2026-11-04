package dev.geco.gmusic.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DataServiceTest {

    private Connection connection;
    private DataService dataService;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        dataService = new DataService(connection, "sqlite");
        dataService.execute("CREATE TABLE fav (uuid TEXT, songId TEXT)");
        dataService.execute("INSERT INTO fav (uuid, songId) VALUES (?, ?)", "u", "a");
        dataService.execute("INSERT INTO fav (uuid, songId) VALUES (?, ?)", "u", "b");
    }

    @AfterEach
    void tearDown() { dataService.close(); }

    private int count() throws SQLException {
        try(ResultSet rs = dataService.executeAndGet("SELECT COUNT(*) AS c FROM fav WHERE uuid = ?", "u")) {
            rs.next();
            return rs.getInt("c");
        }
    }

    @Test
    void failedSaveKeepsPreviousFavorites() throws SQLException {
        assertThrows(SQLException.class, () -> dataService.transaction(() -> {
            dataService.execute("DELETE FROM fav WHERE uuid = ?", "u");
            dataService.execute("INSERT INTO fav (uuid, songId) VALUES (?, ?)", "u", "c");
            throw new SQLException("simulated failure in the middle of saving");
        }));
        // 途中で失敗しても、消したはずの前の 2 件が残る
        assertEquals(2, count());
        assertTrue(connection.getAutoCommit());
    }

    @Test
    void successfulSaveReplacesFavorites() throws SQLException {
        dataService.transaction(() -> {
            dataService.execute("DELETE FROM fav WHERE uuid = ?", "u");
            dataService.execute("INSERT INTO fav (uuid, songId) VALUES (?, ?)", "u", "c");
        });
        assertEquals(1, count());
    }

    @Test
    void closingResultSetClosesStatement() throws SQLException {
        ResultSet rs = dataService.executeAndGet("SELECT * FROM fav");
        Statement statement = rs.getStatement();
        rs.close();
        assertTrue(statement.isClosed());
    }

}
