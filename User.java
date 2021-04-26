package server;

import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;

/**
 * User Objekt
 */
public class User {

    private static List<String> user_ids = new ArrayList<>();   //Liste aller UserIDs
    private static List<User> users = new ArrayList<>();        //Liste aller User
    private static User dummyuser;                              //Fallback user

    private final String id;                                    //User ID
    private String ip;                                          //User IP
    private final String name;                                  //User Name
    private final WebSocket connection;                         //User Connection
    public boolean exists;                                      //User exist-status

    //Gibt alle User zurück
    public static List<User> getUsers() {
        return users;
    }

    /**
     * Entfernt User mit connection
     *
     * @param connection User connection
     */
    public static void removeUser(WebSocket connection) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getConnection().equals(connection)) {
                users.remove(i);
                break;
            }
        }
    }

    /**
     * Ermittlet User mit geg. connection
     *
     * @param connection User connection
     * @return User
     */
    public static User getUserByConnection(WebSocket connection) {
        for (User u : users) {
            if (u.getConnection().equals(connection)) {
                return u;
            }
        }
        return User.dummyuser;  //Sollte kein User gefunden werden, wird der dummyuser zurückgegeben (error-vorbeuge)
    }


    //Gibt alle User IDs zurück
    public static List<String> getUser_ids() {
        return user_ids;
    }

    //Gibt User ID zurück
    public String getId() {
        return id;
    }

    //Gibt User IP zurück
    public String getIp() {
        return ip;
    }

    //Gibt User Name zurück
    public String getName() {
        return name;
    }

    //Gibt User Connection zurück
    public WebSocket getConnection() {
        return connection;
    }


    /**
     * Erstellt einen Normalen User
     *
     * @param connection Users connection
     * @param name       User Name
     * @param id         User ID
     */
    private User(WebSocket connection, String name, String id) {
        this.id = id;
        this.name = name;
        this.connection = connection;
        try {
            this.ip = connection.getRemoteSocketAddress().getAddress().getHostAddress();    //IP wird versucht zu ermittelt
        } catch (NullPointerException e) {                                                  //Wenn nicht, auch ok, nicht so wichtig
            this.ip = "0.0.0.0";
            log(e.getMessage() + " @server.User.__init__ NullPointerException");
        }
        user_ids.add(id);   //Fügt die user-ID der user_ids Liste hinzu
        users.add(this);    //Fügt den user der users Liste hinzu
        this.exists = true; //Ein "normaler" user existiert, der dummy user nicht
    }

    /**
     * Erstellt einen Dummyuser (wird verwendet wenn kein passender User ermittelt werden kann)
     *
     * @param name Dummy-Name
     * @param id   Dummy-ID
     * @param ip   Dummy-IP
     */
    private User(String name, String id, String ip) {
        this.id = id;
        this.name = name;
        this.connection = null; //irrelevant
        this.ip = ip;
        this.exists = false;    //Der dummy user existiert nicht
    }

    /**
     * Check zunächst, ob der User mit der ID bereits existiert und gibt diesen zurück oder erstellt einen komplett neuen
     *
     * @param connection User connection
     * @param name       User Name
     * @param id         User ID
     * @return Erstellter und ermittelter User
     */
    public static User createNewUser(WebSocket connection, String name, String id) {
        //Check if a user with that connection already exists
        for (User u : users) {
            if (u.getConnection().equals(connection)) {
                return u;
            }
        }
        return new User(connection, name, id);
    }

    //Schöne darstellung des User-Objekt
    @Override
    public String toString() {
        return "User: " + id
                + "\n|--ID: " + id
                + "\n|--IP: " + ip
                + "\n|--NAME: " + name
                + "\n+--CONNECTION: " + connection
                + "\n";
    }

    //log the message to the logfile and console in format [dd-MM-yyyy hh:mm:ss] <message>
    private void log(String s) {
        Util.log(s);
    }

    //Erstellt Dummyuser
    public static void createDummyUser() {
        if (dummyuser == null) {
            dummyuser = new User("undefined", "undefined", "undefined");
        }
    }

}
