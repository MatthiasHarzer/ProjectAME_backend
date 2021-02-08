package server;

import org.java_websocket.WebSocket;

import java.sql.*;
import java.util.*;

import org.json.simple.JSONObject;

public class DatabaseHandler {
    private final String filePath = System.getProperty("user.dir");
    private final String databaseName = "database.db";
    private final String databaseURL = "jdbc:sqlite:" + filePath + "\\db\\" + databaseName;
    private Connection sqliteconn;
    private Server server;


    public DatabaseHandler(Server server) throws ClassNotFoundException, SQLException {
        this.server = server;
        User.createDummyUser();

        Class.forName("org.sqlite.JDBC");
        sqliteconn = DriverManager.getConnection(databaseURL);
        log("Connected to SQLite");
        checkTables();
    }


    public void printUsers() {
        System.out.println("\nUser:");
        for (User u : User.getUsers()) {
            System.out.println(u);
        }
        if (User.getUsers().size() == 0) {
            System.out.println("Users empty");
        }

    }


    public void newMessage(WebSocket conn, String content, String time) {
        String sql = "INSERT INTO messages(id,content,author,author_id,time) VALUES(?,?,?,?,?)";

        String mid = Util.generateUniqueString(32, getAllMessageIDs());

        String author = User.getUserByConnection(conn).getName();
        String author_id = User.getUserByConnection(conn).getId();

        try (PreparedStatement pstmt = sqliteconn.prepareStatement(sql)) {
            pstmt.setString(1, mid);
            pstmt.setString(2, content);
            pstmt.setString(3, author);
            pstmt.setString(4, author_id);
            pstmt.setString(5, time);
            pstmt.executeUpdate();
        } catch (SQLException | NullPointerException e) {
            log(e.getMessage() + " @server.DatabaseHandler.newMessage");
            e.printStackTrace();
        }
    }

    public String newConnection(WebSocket conn, String name) {
        return newConnection(conn, name, Util.generateUniqueString(10, User.getUser_ids()));
    }

    public String newConnection(WebSocket conn, String name, String id) {
        return User.createNewUser(conn, name, id).getId();
    }

    public void connectionClose(WebSocket conn) {
        User.removeUser(conn);
    }

    private void log(String s) {
        Util.log(s);
    }

    private void checkTables() {
        String userTable = "CREATE TABLE IF NOT EXISTS messages (\n"
                + "     id text PRIMARY KEY, \n"
                + "     content text,\n"
                + "     author text,\n"
                + "     author_id text,\n"
                + "     time text\n"
                + ");";
        executeSQL(userTable);
    }

    private void executeSQL(String sql) {
        try {
            Statement stmt = sqliteconn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            log(e.getMessage() + "@server.DatabaseHandler.executeSQL SQLException");
        }
    }

    private List<String> getAllMessageIDs() {
        List<String> ids = new ArrayList<>();

        String sql = "SELECT id FROM messages";
        try {
            Statement stmt = sqliteconn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            return ids;
        } catch (SQLException e) {
            log(e.getMessage() + " @server.DatabaseHandler.getALlMessageIDs SQLException");
        }
        return ids;
    }
}
