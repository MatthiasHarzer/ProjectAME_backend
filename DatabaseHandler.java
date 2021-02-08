package server;

import org.java_websocket.WebSocket;

import java.sql.*;
import java.util.*;

import org.json.simple.JSONObject;

public class DatabaseHandler {
    private final String filePath = System.getProperty("user.dir");
    private final String databaseName = "database.db";
    private final String databseURL = "jdbc:sqlite:" + filePath + "\\db\\" + databaseName;
    private Connection sqliteconn = null;
    private Server server;

    private Map<String, JSONObject> users = new HashMap<>();


    public DatabaseHandler(Server server) throws ClassNotFoundException, SQLException {
        this.server = server;

        String url = "jdbc:sqlite:" + filePath + "/db/database.db";
        Class.forName("org.sqlite.JDBC");
        sqliteconn = DriverManager.getConnection(url);
        log("Connected to SQLite");
        checkTables();
    }


    public void printUsers() {
        System.out.println("\nUser:");
        for (String key : users.keySet()) {
            System.out.println(key);
            System.out.println("|--ip:" + users.get(key).get("ip"));
            System.out.println("|--name:" + users.get(key).get("name"));
            System.out.println("+--sharecode:" + users.get(key).get("sharecode"));
            System.out.println("");
        }
        if (users.size() == 0) {
            System.out.println("Users empty");
        }

    }

    private void writeToUsers(String type, String id) {
        if (type.equals("rm")) {
            users.remove(id);
        }
    }

    private void writeToUsers(String type, String id, WebSocket conn, String name, String sharecode) {
        switch (type) {
            case "rm":
                writeToUsers("rm", id);
                break;
            case "add":
                //Check if the user already exists
                if (getUserByConn(conn) == null) {
                    JSONObject m = new JSONObject();
                    m.put("ip", conn.getRemoteSocketAddress().getAddress().getHostAddress());
                    m.put("name", name);
                    m.put("sharecode", sharecode);
                    m.put("connection", conn);
                    users.put(id, m);
                    log("Added new user " + id + " with name " + name);
                }
                break;
        }
    }

    public WebSocket[] getAllConnections() {
        WebSocket[] conns = new WebSocket[users.size()];
        for (int i = 0; i < users.keySet().toArray().length; i++) {
            conns[i] = (WebSocket) users.get(users.keySet().toArray()[i]).get("connection");
        }
        return conns;
    }

    public JSONObject getUserByConn(WebSocket conn) {
        for (String key : users.keySet()) {
            if (users.get(key).get("connection").equals(conn)) {
                return (JSONObject) users.get(key);
            }
        }
        return null;
    }

    private String getIdByConn(WebSocket conn) {
        for (String key : users.keySet()) {
            if (users.get(key).get("connection").equals(conn)) {
                return key;
            }
        }
        return null;
    }

    public void newMessage(WebSocket conn, String content, String time) {
        String sql = "INSERT INTO messages(id,content,author,author_id,time) VALUES(?,?,?,?,?)";

        String mid = "";
        List<String> mids = getAllMessageIDs();
        do {
            mid = generateRandomString(32);
        } while (mids.contains(mid));
        String author = (String) getUserByConn(conn).get("name");
        String author_id = getIdByConn(conn);

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
        String sql = "INSERT INTO users (id, ip, name, sharecode) VALUES (?,?,?,?)";
        String myid = "";
        do {
            myid = generateRandomString(5);
        } while (users.containsKey(myid));
        return newConnection(conn, name, myid);
    }

    public String newConnection(WebSocket conn, String name, String id) {
        writeToUsers("add", id, conn, name, id);
        return id;
    }

    public void connectionClose(WebSocket conn) {
        writeToUsers("rm", getIdByConn(conn));
//        printUsers();
    }

//    public static void main(String[] args) throws ClassNotFoundException, UnknownHostException {
//
//        DatabaseHandler db = new DatabaseHandler(new Server(5555));
//        db.newConnection("127.0.0.0.0", "Matthias");
//        Scanner scan = new Scanner(System.in);
//
//        String s = scan.next();
//        db.connectionClose("127.0.0.0.0");
//    }

    public String generateRandomString(int targetStringLength) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();

        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        return generatedString;
    }

    private void log(String s) {
        this.server.log(s);
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
        return null;
    }
}
