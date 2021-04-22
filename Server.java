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
    public static String __version = "0.2.2";
    public static int PORT = 5555;
    private DatabaseHandler database;


    //✔
    public Server(int port) {
        super(new InetSocketAddress(port));
        log("ChatServer started at " + getAddress());

        try {
            database = new DatabaseHandler(this);
            database.checkTable();      //Check for public table
        } catch (ClassNotFoundException | SQLException e) {
            log(e.getMessage() + " @server.Server.init while trying to connect to DatabaseHandler");
        }
    }


    //process the incoming message
    private void processMessage(@NotNull HashMap<String, String> data, WebSocket conn) throws Throwable {
        if (!isValidMessage(data, new String[]{"type", "content"})) {
            sendMessageToConn(conn, mapBlueprint("error", "Invalid message"));
            return;
        }
        String time = System.currentTimeMillis() + "";
        log("Received type '" + data.get("type") + "' with content '" + data.get("content") + "' from " + User.getUserByConnection(conn).getName() + "@" + User.getUserByConnection(conn).getIp() + " (User " + User.getUserByConnection(conn).getId() + ")");

        switch (data.get("type")) {
            case "connect":
                //if it is an init message, tell it the database handler and return the generated id to connection
                String id = database.newConnection(conn, data.get("content"));
                sendMessageToConn(conn, mapBlueprint("connect_id", id));
                break;

            case "connect_with_id":
                //if it is an init message, tell it the database handler and return the generated id to connection
                if (isValidMessage(data, new String[]{"id"})) {
                    database.newConnection(conn, data.get("content"), data.get("id"));
                    sendMessageToConn(conn, mapBlueprint("connect_id", data.get("id")));
                } else {
                    sendMessageToConn(conn, mapBlueprint("error", "Invalid message with 'connect_with_id'"));
                }
                break;

            case "message":
                //send the message to everyone connected
                database.newMessage(conn, data.get("content"), time);
                User author = User.getUserByConnection(conn);
                sendMessageToUsers(User.getUsers(), textMessageMapBlueprint("message", data.get("content"), author.getName(), time, author.getId()));
//                sendMessageToChat();
//                sendMessageToUsers(User.getUsers(), textMessageMapBlueprint("message", data.get("content"), User.getUserByConnection(conn).getName(), time, User.getUserByConnection(conn).getId()));
                break;

            case "request_message_history":
                if (isValidMessage(data, new String[]{"from", "to"})) {
                    List<Map<String, String>> messages = database.getAllMessages(Long.parseLong(data.get("from")), Long.parseLong(data.get("to")));
                    System.out.println("Sending " + messages.size() + " messages");
                    HashMap<String, String> map = mapBlueprint("message_history", objectToString(messages));
                    sendMessageToConn(conn, map);
                } else {
                    sendMessageToConn(conn, mapBlueprint("error", "Invalid message with 'request_message_history'"));
                }
                break;

        }
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
//        database.printUsers();
    }


    private void sendMessageToUsers(List<User> users, HashMap<String, String> map) throws IOException {
        sendMessageToUsers(users, map, null);
    }

    //✔
    // Send a message-map to a list of users
    private void sendMessageToUsers(List<User> users, HashMap<String, String> map, WebSocket exceptConnection) throws IOException {
        for (User u : users) {
            if (!u.getConnection().equals(exceptConnection)) {
                sendMessageToConn(u.getConnection(), map);
            }
        }
    }

    //✔
    private void sendMessageToConn(WebSocket conn, HashMap<String, String> map) throws IOException {
        try {
            conn.send(objectToString(map));
        } catch (WebsocketNotConnectedException e) {
            //A websocket that just disconnects can't receive any messages -> Exception, ignore
        }

    }

    private HashMap<String, String> mapBlueprint(String type, String content) {
        HashMap<String, String> m = new HashMap<>();
        m.put("type", type);
        m.put("content", content);
        return m;
    }

    private HashMap<String, String> textMessageMapBlueprint(String type, String content, String name, String time, String chatID, String userID) {
        HashMap<String, String> m = mapBlueprint(type, content);
        m.put("time", time);
        m.put("name", name);
        m.put("id", chatID);
        m.put("user_id", userID);
        return m;
    }

    private HashMap<String, String> textMessageMapBlueprint(String type, String content, String name, String time, String userID) {
        return textMessageMapBlueprint(type, content, name, time, "", userID);
    }


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

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
//        broadcast(conn + " has left the room!");
//        System.out.println(conn + " has left the room!");

        try {
            log(User.getUserByConnection(conn).getName() + "@" + User.getUserByConnection(conn).getIp() + " has left the room!");
        } catch (NullPointerException e) {
            log(conn + " has left the room!");
        }
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
        database.connectionClose(conn);


    }

    @Override
    public void onMessage(WebSocket conn, String message) {
//        broadcast(message);
//        System.out.println(conn + ": " + message);

//        log(conn.getRemoteSocketAddress().getAddress().getHostAddress() + ": " + message);

        try {
            HashMap<String, String> messageData = stringToMap(message);
            processMessage(messageData, conn);
        } catch (IOException e) {
            log(e.getMessage() + " @server.Server.onMessage IOException");
        } catch (ClassNotFoundException e) {
            log(e.getMessage() + " @server.Server.onMessage ClassNotFoundException");
        } catch (IllegalArgumentException e) {
            log(e.getMessage() + " @server.Server.onMessage IllegalArgumentException");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

//    @Override
//    public void onMessage(WebSocket conn, ByteBuffer message) {
////        broadcast(message.array());
////        System.out.println(conn + ": " + message);
//        log(conn.getRemoteSocketAddress().getAddress().getHostAddress() + ": " + message);
//    }

    //✔
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        try {
            sendMessageToConn(conn, mapBlueprint("broadcast", "Welcome to the Server!"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        log(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected!");

//        databas.newConnection(conn.getRemoteSocketAddress().getAddress().getHostAddress(), );
    }

    //✔
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
        if (conn != null) {
            log(ex.getMessage() + " @server.Server.onError");
        }
    }

    //✔
    @Override
    public void onStart() {
//        System.out.println("Server started!");
        log("Server started ");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public static void main(String[] args) throws InterruptedException, IOException {

        Server s = new Server(PORT);
        s.start();
//        System.out.println("ChatServer started on port: " + s.getPort());

        BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));
        label:
        while (true) {
            String in = sysin.readLine();
//            s.broadcast(in);
            switch (in) {
                case "exit":
                    s.stop(1000);
                    break label;
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

    //Converts a map to a string
    private String objectToString(Object o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    //Converts a string to a map
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

    //log the message to the logfile and console inf format [dd-MM-yyyy hh:mm:ss] <message>
    public void log(String s) {
        Util.log(s);
    }

}
