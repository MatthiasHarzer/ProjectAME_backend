package server;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.jetbrains.annotations.NotNull;

public class Server extends WebSocketServer {
    public static String __version = "0.2.3";   //Server Version (random)
    public static int PORT = 5555;              //Server Port
    private DatabaseHandler database;           //Database Handler

    /**
     * Startet Server an geg. Port
     *
     * @param port Server Port
     */
    public Server(int port) {
        super(new InetSocketAddress(port));
        log("ChatServer started at " + getAddress());

        try {
            database = new DatabaseHandler();       //Verbindung mit dem DatabaseHandler (Speicher Nachricht in Datenbank)
        } catch (ClassNotFoundException | SQLException e) {
            log(e.getMessage() + " @server.Server.init while trying to connect to DatabaseHandler");
        }
    }


    /**
     * Verarbeitet eine (als Map kodierte) Nachricht von conn (Autor)
     *
     * @param data String Map mit Nachricht-Daten
     * @param conn Autorverbindung
     * @throws Throwable Error
     */
    private void processMessage(@NotNull HashMap<String, String> data, WebSocket conn) throws Throwable {
        if (!isValidMessage(data, new String[]{"type", "content"})) {                       //Überprüft, ob die wichtigsten Komponenten der Nachricht gegeben sind, wenn nicht, abbrechen
            sendMessageToConn(conn, mapBlueprint("error", "Invalid message"));  //Antwort zurückschicken (dass die Nachricht nicht valide ist)
            return;
        }
        String time = System.currentTimeMillis() + "";                                      //Zeitpunkt, an dem die Nachricht den Server erreicht hat

        log("Received type '" + data.get("type") + "' with content '" + data.get("content") + "' from " + User.getUserByConnection(conn).getName() + "@" + User.getUserByConnection(conn).getIp() + " (User " + User.getUserByConnection(conn).getId() + ")");

        switch (data.get("type")) {                                                         //Die Nachricht wird nach Typ gefiltert
            case "connect": //Die Nachricht ist eine Anfrage, sich mit dem Server zu verbinden
                String id = database.newConnection(conn, data.get("content"));  //Der User wird dem DatabaseHandler mitgeteilt und ein ID wird generiert..
                sendMessageToConn(conn, mapBlueprint("connect_id", id));   //..welche an den User zurückgeschickt wird.
                break;

            case "connect_with_id": //Die Nachricht ist eine Anfrage, sich mit dem Server zu verbinden, wobei vom User eine ID bereitgestellt, wodurch der Server den User "wieder erkennt"
                if (isValidMessage(data, new String[]{"id"})) { //Wenn sich der User mit einer ID verbinden möchte, muss die auch im Datensatz vorhanden sein, dies wird hier überprüft
                    database.newConnection(conn, data.get("content"), data.get("id"));  //Der User wird dem DatabaseHandler mitgeteilt, wobei die gegebende ID zur wiedererkennung verwendet wird
                    sendMessageToConn(conn, mapBlueprint("connect_id", data.get("id")));    //Eigentlich unnötig: Die ID wird an den User zurückgeschickt
                } else {
                    sendMessageToConn(conn, mapBlueprint("error", "Invalid message with 'connect_with_id'"));   //Wurde keine ID angegeben, wird eine Fehlernachricht an den User geschickt
                }
                break;

            case "message": //Die Nachricht ist eine Chat-Nachricht
                database.newMessage(conn, data.get("content"), time);   //Dem DatabaseHandler wird die neue Nachricht mitgeteilt, welcher diese in einer Datenbank speichert
                User author = User.getUserByConnection(conn);           //Es werden informationen über den Autor der nachricht gesammelt
                sendMessageToUsers(User.getUsers(), textMessageMapBlueprint(data.get("content"), author.getName(), time));
                //Die Chat-Nachricht wird an alle User weiter geleitet, sammt Name des Autors und Uhrzeit (sowie Autor User ID, eigentlich unnötig)
                break;

            case "request_message_history": //Die Nachricht ist eine Anfrage, vergangene Nachricht zu bekommen
                if (isValidMessage(data, new String[]{"from", "to"})) { //Es wird überprüft ob alle Daten für die anfrage vorliegen (from = start Zeitpunkt, to = end Zeitpunk is ms)
                    List<Map<String, String>> messages = database.getAllMessages(Long.parseLong(data.get("from")), Long.parseLong(data.get("to"))); //Es werden alle Nachrichten aus der Datenbank geladen,
                    HashMap<String, String> map = mapBlueprint("message_history", objectToString(messages));    //...konvertiert,
                    sendMessageToConn(conn, map);                                                                    //..und an die User zurückgeschickt

                    log("Sending " + messages.size() + " messages to " + User.getUserByConnection(conn).getName() + "@" + User.getUserByConnection(conn).getIp());  //log
                } else {
                    sendMessageToConn(conn, mapBlueprint("error", "Invalid message with 'request_message_history'"));   //Es wurde kein Zeitraum angegeben -> Error message zurück
                }
                break;

        }

        //Bei einer Verbindungs-Anfrage, wird zusätzlich eine "Willkommens" Nachricht an alle anderen User geschick
        switch (data.get("type")) {
            case "connect":
            case "connect_with_id":
                HashMap<String, String> map = mapBlueprint("user_join", User.getUserByConnection(conn).getId());
                map.put("name", User.getUserByConnection(conn).getName());
                map.put("id", User.getUserByConnection(conn).getId());
                map.put("ip", User.getUserByConnection(conn).getIp());
                map.put("time", time);
                sendMessageToUsers(User.getUsers(), map);
                break;
        }
    }

    /**
     * Sendet eine Nachricht an eine Liste von Users, mit dem Inhalt der HashMap map
     *
     * @param users Liste der User
     * @param map   HashMap mit Inhalt der Nachricht
     * @throws IOException Error
     */
    private void sendMessageToUsers(List<User> users, HashMap<String, String> map) throws IOException {
        for (User u : users) {
            sendMessageToConn(u.getConnection(), map);
        }
    }


    /**
     * Sendet eine Nachricht an die entsprechende Connection, mit dem Inhalt der HashMap map
     *
     * @param conn Ziel Verbindung (User)
     * @param map  Nachricht Inhalt
     * @throws IOException Error
     */
    private void sendMessageToConn(WebSocket conn, HashMap<String, String> map) throws IOException {
        try {
            conn.send(objectToString(map));
        } catch (WebsocketNotConnectedException e) {
            //A websocket that just disconnects can't receive any messages -> Exception, ignore
        }

    }

    /**
     * Generiert eine HashMap für eine Nachricht mit geg. Typ und Inhalt
     *
     * @param type    Typ der Nachricht (z.B. message = Chat-Nachricht, message_history = Alte Nachrichten, usw)
     * @param content Inhalt der Nachricht (z.B. Text, List-String der message_history, usw)
     * @return HashMap<Strinng, String> mit entsprechenden Inhalt
     */
    private HashMap<String, String> mapBlueprint(String type, String content) {
        HashMap<String, String> m = new HashMap<>();
        m.put("type", type);
        m.put("content", content);
        return m;
    }

    /**
     * Generiert eine HashMap für eine Chat-Nachricht mit geg. Typ und Inhalt
     *
     * @param content Text-Inhalt der Nachricht
     * @param name    Name des Autors
     * @param time    Uhrzeit der Nachricht (in ms)
     * @return HashMap<Strinng, String> mit entsprechenden Inhalt
     */
    private HashMap<String, String> textMessageMapBlueprint(String content, String name, String time) {
        HashMap<String, String> m = mapBlueprint("message", content);
        m.put("time", time);
        m.put("name", name);
        return m;
    }


    /**
     * Methode, die überprüft ob die geg. HashMap die geg. Keys enthält
     *
     * @param data HashMap zum überprüfen
     * @param args Array an zu überprüfenden Keys
     * @return Error
     */
    private boolean isValidMessage(@NotNull HashMap<String, String> data, String[] args) {
        for (String arg : args) {
            try {
                data.get(arg);
            } catch (NullPointerException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Wird aufgerufen, wenn die Verbindung zu einem User abbricht
     *
     * @param conn   Die Connection des Users
     * @param code   Disconnect-Code
     * @param reason Disconnect-Reason
     * @param remote IDK
     */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        try {
            log(User.getUserByConnection(conn).getName() + "@" + User.getUserByConnection(conn).getIp() + " has left the room!");
        } catch (NullPointerException e) {
            log(conn + " has left the room!");
        }

        //Schicke eine Disconnect-Nachricht an alle User
        HashMap<String, String> map = mapBlueprint("user_disconnect", User.getUserByConnection(conn).getId());
        map.put("name", User.getUserByConnection(conn).getName());
        map.put("id", User.getUserByConnection(conn).getId());
        map.put("ip", User.getUserByConnection(conn).getIp());
        map.put("time", System.currentTimeMillis() + "");
        try {
            sendMessageToUsers(User.getUsers(), map);
        } catch (IOException e) {
            e.printStackTrace();
        }

        database.connectionClose(conn);     //Teile dem DatabaseHandler mit, das eine User mit conn disconnected ist (löscht den User)
    }

    /**
     * Wird aufgerufen wenn eine neue Nachricht von einem User ankommt
     *
     * @param conn    Die Conncetion des Users
     * @param message Die rohe String Nachricht
     */
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            HashMap<String, String> messageData = stringToMap(message); //Konvertiert den String in eine HashMap
            processMessage(messageData, conn);                          //Verarbeite die Nachricht
        } catch (IOException e) {               //ERRORS (warum auch immer)
            log(e.getMessage() + " @server.Server.onMessage IOException");
        } catch (ClassNotFoundException e) {
            log(e.getMessage() + " @server.Server.onMessage ClassNotFoundException");
        } catch (IllegalArgumentException e) {
            log(e.getMessage() + " @server.Server.onMessage IllegalArgumentException");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    /**
     * Wird aufgerufen wenn eine neuer User sich mit dem Server verbindet
     *
     * @param conn      Connection des Users
     * @param handshake idk
     */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        try {
            sendMessageToConn(conn, mapBlueprint("broadcast", "Welcome to the Server!"));   //BROTcast an den User (welcome message)
        } catch (IOException e) {
            e.printStackTrace();
        }
        log(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected!");

    }

    /**
     * Any error ex with conn
     *
     * @param conn Problemconnection
     * @param ex   Error
     */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
        if (conn != null) {     //Nur wenn der User noch connected ist, wird der Error geloged
            log(ex.getMessage() + " @server.Server.onError");
        }
    }

    /**
     * Wird bei start des Servers aufgerufen
     */
    @Override
    public void onStart() {
//        System.out.println("Server started!");
        log("Server started ");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    /**
     * entry point
     *
     * @param args args
     * @throws InterruptedException Error
     * @throws IOException          Error
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        boolean running = true;
        Server s = new Server(PORT);    //Starte den Server an PORT
        s.start();                      //...


        BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));    //Input reader
        while (running) {
            //Lese Console-Input für debug und server-stop
            String in = sysin.readLine();
            switch (in) {
                case "exit":
                    s.stop(1000);
                    running = false;
                    break;
                case "users":
                    for (User user : User.getUsers()) {
                        System.out.println(user);
                    }
                    break;
                case "version":
                    System.out.println("Running v" + Server.__version);
                    break;
            }
        }

    }

    /**
     * Konvertiert eine HashMap (oder Object) zu einem String
     *
     * @param o Map
     * @return Map as String
     * @throws IOException Error
     */
    private String objectToString(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * Konvertiert einen Map-String zu eine HashMap
     *
     * @param s Map-String
     * @return HashMap from String
     * @throws IOException            Error
     * @throws ClassNotFoundException Error
     */
    private HashMap<String, String> stringToMap(String s) throws IOException,
            ClassNotFoundException {
        s = s.trim();
        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return (HashMap<String, String>) o;
    }

    //log the message to the logfile and console in format [dd-MM-yyyy hh:mm:ss] <message>
    public void log(String s) {
        Util.log(s);
    }

}
