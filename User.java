package server;

import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class User {
    private static List<String> user_ids = new ArrayList<>();
    private static List<User> users = new ArrayList<>();
    private static User dummyuser;
    private String id;
    private String ip;
    private String sharecode;
    private String name;
    private WebSocket connection;

    public static List<User> getUsers() {
        return users;
    }

    public static void removeUser(WebSocket connection) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getConnection().equals(connection)) {
                users.remove(i);
                break;
            }
        }
    }

    public static User getUserById(String id) {
        for (User u : users) {
            if (u.getId().equals(id)) {
                return u;
            }
        }
        return User.dummyuser;
    }

    public static User getUserByConnection(WebSocket connection) {
        for (User u : users) {
            if (u.getConnection().equals(connection)) {
                return u;
            }
        }
        return User.dummyuser;
    }


    public static List<String> getUser_ids() {
        return user_ids;
    }


    public String getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public String getName() {
        return name;
    }

    public WebSocket getConnection() {
        return connection;
    }

    public String getSharecode() {
        return sharecode;
    }

    private User(WebSocket connection, String name, String id) {
        this.id = id;
        this.sharecode = this.id;
        this.name = name;
        this.connection = connection;
        try {
            this.ip = connection.getRemoteSocketAddress().getAddress().getHostAddress();
        } catch (NullPointerException e) {
            this.ip = "0.0.0.0";
            log(e.getMessage() + " @server.User.__init__ NullPointerException");
        }
        user_ids.add(id);
        users.add(this);
    }

    private User(String name, String id) {
        this.id = id;
        this.sharecode = this.id;
        this.name = name;
        this.connection = null;
    }

    public static User createNewUser(WebSocket connection, String name, String id) {
        //Check if a user with that connection already exists
        for (User u : users) {
            if (u.getConnection().equals(connection)) {
                return u;
            }
        }
        return new User(connection, name, id);
    }

    @Override
    public String toString() {
        return "User: " + id
                + "\n|--ID: " + id
                + "\n|--IP: " + ip
                + "\n|--NAME: " + name
                + "\n|--SHARECODE: " + sharecode
                + "\n+--CONNECTION: " + connection
                + "\n";
    }

    private void log(String s) {
        Util.log(s);
    }

    public static void createDummyUser() {
        if (dummyuser == null) {
            dummyuser = new User("undefined", "undefined");
        }
    }

}
