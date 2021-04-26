package server;

import org.java_websocket.WebSocket;

import java.sql.*;
import java.util.*;


public class DatabaseHandler {
    private final String filePath = System.getProperty("user.dir");         //Dateipfad zur Server-Datei
    private final String databaseName = "database.db";                      //Name der Datenbank
    private final String databaseURL = "jdbc:sqlite:" + filePath + "/db/" + databaseName;   //Dateipfad zu Datenbank
    private Connection sqliteconn;                                          //SQLite conncetion


    /**
     * Erstellt Dummyuserund und startet sqlite-Connection + checkt ob der nachrichten Table existiert
     *
     * @throws ClassNotFoundException Error
     * @throws SQLException           Error
     */
    public DatabaseHandler() throws ClassNotFoundException, SQLException {

        User.createDummyUser();     //Erstellt Dummyuser (wird als fallback verwendet)

        Class.forName("org.sqlite.JDBC");   //Braucht man aus GRÜNDEN
        sqliteconn = DriverManager.getConnection(databaseURL);  //Verbindet sich mit sqlite
        log("Connected to SQLite");

        checkTable();   //Überprüft den Nachrichten Table
    }

    /**
     * Sucht alle Nachrichten zwischen from und to
     *
     * @param from start-Wert (ms)
     * @param to   end-Wert (ms)
     * @return Liste an Nachrichten
     */
    public List<Map<String, String>> getAllMessages(long from, long to) {
        if (from > to) {    //Sollten die Werte vertauscht sein, werden diese einfach korrigiert
            long t = to;
            to = from;
            from = t;
        }
        String sql = "SELECT id,content,author,author_id,time " //SQL umd alle Nachrichten zwischen from und to zu filtern
                + "FROM public WHERE time BETWEEN ? AND ?";
        List<Map<String, String>> messages = new ArrayList<>();

        try (PreparedStatement pstmt = sqliteconn.prepareStatement(sql)) {  //Versuche SQL auzuführen
            pstmt.setLong(1, from);
            pstmt.setLong(2, to);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) { //Durch jeden Eintrag gehen, diesen in einer Map speichern und der messages Liste hinzufügen
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
        return messages;
    }

    /**
     * Erstellt einen neuen Datenbankeintrag der Chat-Nachricht
     *
     * @param conn    Autor-Connection -> User
     * @param content Inhalt der Nachricht
     * @param time    Uhrzeit der Nachricht (in ms)
     */
    public void newMessage(WebSocket conn, String content, String time) {
        String sql = "INSERT INTO public(id,content,author,author_id,time) VALUES(?,?,?,?,?)"; //SQL für neuen Eintrag

        String mid = Util.generateUniqueString(32, getAllMessageIDs()); //Eine unique Nachrichten-ID wird generiert

        String author = User.getUserByConnection(conn).getName();   //Sammle informationen über den Autor
        String author_id = User.getUserByConnection(conn).getId();

        try (PreparedStatement pstmt = sqliteconn.prepareStatement(sql)) {  //Führe SQL aus
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

    /**
     * Überläd newConnection(WebSocket conn, String name, String id) mit einer Unique ID
     *
     * @param conn Autor Connection
     * @param name Atuor Name
     * @return Generiertes User Objekt
     */
    public String newConnection(WebSocket conn, String name) {
        return newConnection(conn, name, Util.generateUniqueString(10, User.getUser_ids()));
    }

    /**
     * Es wird ein neues User Objekt erstellt mit geg. Connection, Name und ID
     *
     * @param conn Conncetion des Users
     * @param name Name des Users
     * @param id   Unique ID des Users
     * @return Generiertes User Objekt
     */
    public String newConnection(WebSocket conn, String name, String id) {
        return User.createNewUser(conn, name, id).getId();
    }

    /**
     * Entfernt den entsprechenden User wenn dieser disconnected
     *
     * @param conn User-Connection
     */
    public void connectionClose(WebSocket conn) {
        User.removeUser(conn);
    }

    /**
     * Gibt alle Nachrichten IDs zurück
     *
     * @return Liste an IDs
     */
    private List<String> getAllMessageIDs() {
        List<String> ids = new ArrayList<>();

        String sql = "SELECT id FROM public";   //public = Table name
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

    /**
     * Überprüft ob der public Table existiert und erstellt diesen ggf. (Dort werden alle Nachrichten gespeichert)
     */
    public void checkTable() {
        String userTable = "CREATE TABLE IF NOT EXISTS public (\n"
                + "     id text PRIMARY KEY, \n"
                + "     content text,\n"
                + "     author text,\n"
                + "     author_id text,\n"
                + "     time text\n"
                + ");";
        executeSQL(userTable);
    }

    /**
     * Führt SQL-Befehler aus
     *
     * @param sql sql-String to execute
     */
    private void executeSQL(String sql) {
        try {
            Statement stmt = sqliteconn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            log(e.getMessage() + "@server.DatabaseHandler.executeSQL SQLException");
        }
    }

    //log the message to the logfile and console in format [dd-MM-yyyy hh:mm:ss] <message>
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
