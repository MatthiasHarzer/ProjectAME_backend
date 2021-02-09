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
    private DatabaseHandler database;


    public Server(int port) {
        super(new InetSocketAddress(port));
        init();
    }

    public Server(InetSocketAddress address) {
        super(address);
        init();
    }

    public Server(int port, Draft_6455 draft) {
        super(new InetSocketAddress(port), Collections.singletonList(draft));
        init();
    }


    public void init() {
        log("ChatServer started at " + getAddress());

        try {
            database = new DatabaseHandler(this);
        } catch (ClassNotFoundException | SQLException e) {
            log(e.getMessage() + " @server.Server.init while trying to connect to DatabaseHandler");
        }

    }

    //process the incoming message
    private void processMessage(@NotNull HashMap<String, String> data, WebSocket conn) throws IOException {
        if (!isValidMessage(data, new String[]{"type", "content"})) {
            sendMessageToConn(conn, mapBlueprint("error", "Invalid message"));
            return;
        }
        User requested_user;
        String time = System.currentTimeMillis() + "";
        log("Received type '" + data.get("type") + "' with content '" + data.get("content") + "' from " + User.getUserByConnection(conn).getName() + "@" + User.getUserByConnection(conn).getIp() + " (User " + User.getUserByConnection(conn).getId() + ")");
        switch (data.get("type")) {
            case "connect":
                //if it is an init message, tell it the database handler and return the generated id to connection
                String id = database.newConnection(conn, data.get("content"));
                sendMessageToConn(conn, mapBlueprint("id", id));
                break;
            case "connect_with_id":
                //if it is an init message, tell it the database handler and return the generated id to connection
                if (isValidMessage(data, new String[]{"id"})) {
                    database.newConnection(conn, data.get("content"), data.get("id"));
                } else {
                    sendMessageToConn(conn, mapBlueprint("error", "Invalid message with 'connect_with_id'"));
                }
                break;

            case "message":
                //send the message to everyone connected
                database.newMessage(conn, data.get("content"), time);
                sendMessageToAll(textMessageMapBlueprint("message", data.get("content"), User.getUserByConnection(conn).getName(), time), conn);
                break;

            case "request_message_history":
                if (isValidMessage(data, new String[]{"from", "to"})) {
                    List<Map<String, String>> messages = database.getAllMessages(Long.parseLong(data.get("from")), Long.parseLong(data.get("to")));
                    System.out.println(messages.size());
                    HashMap<String, String> map = mapBlueprint("message_history", objectToString(messages));
                    sendMessageToConn(conn, objectToString(map));
                } else {
                    sendMessageToConn(conn, mapBlueprint("error", "Invalid message with 'request_message_history'"));
                }
                break;
            case "request_private_chat":
                requested_user = User.getUserById(data.get("content"));
                if (requested_user.exists) {
                    sendMessageToConn(conn, mapBlueprint("new_private_chat", requested_user.getName()));
                    sendMessageToConn(requested_user.getConnection(), mapBlueprint("new_private_chat", User.getUserByConnection(conn).getName()));
                    new Chat(User.getUserByConnection(conn), requested_user);
                } else {
                    sendMessageToConn(conn, mapBlueprint("user_not_found", "User does not exist!"));
                }
                break;
            case "message_to_user":
                requested_user = User.getUserById(data.get("id"));
                if (requested_user.exists) {
                    sendMessageToConn(requested_user.getConnection(), textMessageMapBlueprint("message_from_user", data.get("content"), requested_user.getName(), time, requested_user.getId()));
                } else {
                    sendMessageToConn(conn, mapBlueprint("user_not_found", "Couldn't send message to " + data.get("id") + "."));
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
                sendMessageToAll(map);
                break;
        }
//        database.printUsers();
    }

    private void sendMessageToAll(HashMap<String, String> map, WebSocket exceptConnection) throws IOException {
        sendMessageToAll(objectToString(map), exceptConnection);
    }

    private void sendMessageToAll(String mapString, WebSocket exceptConnection) {
        for (User u : User.getUsers()) {
            if (u.getConnection().equals(exceptConnection)) {
                continue;
            }
            sendMessageToConn(u.getConnection(), mapString);

        }
    }

    private void sendMessageToAll(HashMap<String, String> map) throws IOException {
        sendMessageToAll(objectToString(map), null);
    }

    private void sendMessageToConn(WebSocket conn, HashMap<String, String> map) throws IOException {
        sendMessageToConn(conn, objectToString(map));
    }

    private void sendMessageToConn(WebSocket conn, String mapString) {
        try {
            conn.send(mapString);
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

    private HashMap<String, String> textMessageMapBlueprint(String type, String content, String name, String time, String id) {
        HashMap<String, String> m = mapBlueprint(type, content);
        m.put("time", time);
        m.put("name", name);
        m.put("id", id);
        return m;
    }

    private HashMap<String, String> textMessageMapBlueprint(String type, String content, String name, String time) {
        return textMessageMapBlueprint(type, content, name, time, "");
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
            sendMessageToAll(map);
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
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
//        broadcast(message.array());
//        System.out.println(conn + ": " + message);
        log(conn.getRemoteSocketAddress().getAddress().getHostAddress() + ": " + message);
    }

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

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
        if (conn != null) {
            log(ex.getMessage() + " @server.Server.onError");
        }
    }

    @Override
    public void onStart() {
//        System.out.println("Server started!");
        log("Server started ");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        int port = 5555; // 843 flash policy port

        Server s = new Server(port);
        s.start();
//        System.out.println("ChatServer started on port: " + s.getPort());

        BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String in = sysin.readLine();
//            s.broadcast(in);
            if (in.equals("exit")) {
                s.stop(1000);
                break;
            } else if (in.equals("users")) {
                s.database.printUsers();
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
