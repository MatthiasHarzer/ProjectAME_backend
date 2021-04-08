package server;

import org.java_websocket.WebSocket;

import java.sql.*;
import java.util.*;


public class DatabaseHandler {
    private final String filePath = System.getProperty("user.dir");
    private final String databaseName = "database.db";
    private final String databaseURL = "jdbc:sqlite:" + filePath + "/db/" + databaseName;
    private Connection sqliteconn;
    private Server server;


    //✔
    public DatabaseHandler(Server server) throws ClassNotFoundException, SQLException {
        this.server = server;
        User.createDummyUser();
//        Chat.creatPublicChat();

        Class.forName("org.sqlite.JDBC");
        sqliteconn = DriverManager.getConnection(databaseURL);
        log("Connected to SQLite");
    }

    public List<Map<String, String>> getAllMessages(String chat, long from, long to) {
        if (from > to) {
            long t = to;
            to = from;
            from = t;
        }
        String sql = "SELECT id,content,author,author_id,time "
                + "FROM " + chat + " WHERE time BETWEEN ? AND ?";
        List<Map<String, String>> messages = new ArrayList<>();

//        System.out.println("from: " + from + " to:" + to);

        try (PreparedStatement pstmt = sqliteconn.prepareStatement(sql)) {


            //
            pstmt.setLong(1, from);
            pstmt.setLong(2, to);
            ResultSet rs = pstmt.executeQuery();


            // loop through the result set
            while (rs.next()) {

                Map<String, String> m = new HashMap<>();
//                System.out.println(rs.getString("id"));
                m.put("id", rs.getString("id"));
                m.put("content", rs.getString("content"));
                m.put("author", rs.getString("author"));
                m.put("author_id", rs.getString("author_id"));
                m.put("time", rs.getString("time"));
                messages.add(m);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
//        System.out.println("------");
        return messages;
    }

    //✔
    public void newMessage(WebSocket conn, String content, String time) {
        String sql = "INSERT INTO public(id,content,author,author_id,time) VALUES(?,?,?,?,?)";

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

    //✔ -> newConnection
    public String newConnection(WebSocket conn, String name) {
        return newConnection(conn, name, Util.generateUniqueString(10, User.getUser_ids()));
    }

    //✔
    public String newConnection(WebSocket conn, String name, String id) {
        return User.createNewUser(conn, name, id).getId();
    }

    public void connectionClose(WebSocket conn) {
        User.removeUser(conn);
    }

    //✔
    private List<String> getAllMessageIDs() {
        List<String> ids = new ArrayList<>();

        String sql = "SELECT id FROM public";
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

    //✔
    public void checkTable(String chatTable) {
        String userTable = "CREATE TABLE IF NOT EXISTS " + chatTable + " (\n"
                + "     id text PRIMARY KEY, \n"
                + "     content text,\n"
                + "     author text,\n"
                + "     author_id text,\n"
                + "     time text\n"
                + ");";
        executeSQL(userTable);
    }

    //✔
    private void executeSQL(String sql) {
        try {
            Statement stmt = sqliteconn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            log(e.getMessage() + "@server.DatabaseHandler.executeSQL SQLException");
        }
    }

    //✔
    private void log(String s) {
        Util.log(s);
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


}
