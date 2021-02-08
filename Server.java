package server;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Collections;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Server extends WebSocketServer {
    private String filePath;
    private FileWriter logFileWriter;
    private DatabaseHandler database;


    public Server(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
        init();
    }

    public Server(InetSocketAddress address) {
        super(address);
        init();
    }

    public Server(int port, Draft_6455 draft) {
        super(new InetSocketAddress(port), Collections.<Draft>singletonList(draft));
        init();
    }


    public void init() {
        filePath = System.getProperty("user.dir");

        try {
            File logFile = new File(filePath + "\\server_log.txt");
            if (logFile.createNewFile()) {
//                System.out.println("File created: " + logFile.getName());
            } else {
//                System.out.println("File already exists.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        log("ChatServer started at " + getAddress());

        try {
            database = new DatabaseHandler(this);
        } catch (ClassNotFoundException | SQLException e) {
            log(e.getMessage() + " @server.Server.init while trying to connect to DatabaseHandler");
        }

    }

    //process the incoming message
    private void processMessage(HashMap<String, String> data, WebSocket conn) throws IOException {
        log("Received type '" + data.get("type") + "' with content '" + data.get("content") + "'");
        switch (data.get("type")) {
            case "connect":
                //if it is an init message, tell it the database handler and return the generated id to connection
                String id = database.newConnection(conn, data.get("content"));
                sendMessageToConn(conn, "id", id);
                break;
            case "connectwithid":
                //if it is an init message, tell it the database handler and return the generated id to connection
                database.newConnection(conn, data.get("content"), data.get("id"));
                break;

            case "message":
                //send the message to everyone connected
                String time = System.currentTimeMillis() + "";
                database.newMessage(conn, data.get("content"), time);
                sendMessageToAll(conn, data.get("content"), time);
                break;
        }
//        database.printUsers();
    }

    private void sendMessageToAll(WebSocket authorConn, String message, String time) throws IOException {
        try {
            log("Trying to send message '" + message + "' from " + database.getUserByConn(authorConn).get("name"));
        } catch (NullPointerException e) {
            log("Trying to send message '" + message + "' from " + authorConn);
        }
        for (WebSocket conn : database.getAllConnections()) {
            try {
                sendMessageToConn(conn, "message", message, (String) database.getUserByConn(authorConn).get("name"), time);

            } catch (NullPointerException e) {
                log("A nullpointer exception occurred sending message '" + message + "' to " + authorConn);
                log(e.getMessage());
            }

        }
    }

    private void sendMessageToConn(WebSocket conn, String type, String content) throws IOException {
        sendMessageToConn(conn, type, content, "", "");
    }

    private void sendMessageToConn(WebSocket conn, String type, String content, String name, String ts) throws IOException {
        Map<String, String> m = new HashMap<String, String>();
        m.put("type", type);
        m.put("content", content);
        m.put("name", name);
        m.put("time", ts);
        conn.send(mapToString(m));
    }


    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
//        broadcast(conn + " has left the room!");
//        System.out.println(conn + " has left the room!");

        try {
            log(database.getUserByConn(conn).get("name") + "@" + database.getUserByConn(conn).get("ip") + " has left the room!");
        } catch (NullPointerException e) {
            log(conn + " has left the room!");
        }
        database.connectionClose(conn);

    }

    @Override
    public void onMessage(WebSocket conn, String message) {
//        broadcast(message);
//        System.out.println(conn + ": " + message);

//        log(conn.getRemoteSocketAddress().getAddress().getHostAddress() + ": " + message);

        try {
            Map<String, String> messageData = stringToMap(message);
            processMessage((HashMap<String, String>) messageData, conn);
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
            sendMessageToConn(conn, "broadcast", "Welcome to the Server!");
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
            // some errors like port binding failed may not be assignable to a specific websocket
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
    private String mapToString(Map o) throws IOException {
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
        LocalDateTime time = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        String formattedDate = time.format(formatter);


        String logmsg = "[" + formattedDate + "] " + s;

        System.out.println(logmsg);

        try {
            logFileWriter = new FileWriter(filePath + "\\server_log.txt", true);
            logFileWriter.write(logmsg + "\n");
            logFileWriter.close();

//            PrintWriter out = new PrintWriter(new OutputStreamWriter(
//                    new BufferedOutputStream(new FileOutputStream(filePath + "\\server_log.txt")), "UTF-8"), true);
//
//            out.println(logmsg);
//
//            out.flush();
//            out.close();

        } catch (IOException e) {
            System.out.println("An error occurred writing to the log file!");
            e.printStackTrace();
        }

    }

}