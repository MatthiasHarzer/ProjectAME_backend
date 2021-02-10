package server;

import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;

public class Chat {
    private List<User> users = new ArrayList<>();
    private String id;
    private static List<Chat> chats = new ArrayList<>();

    private Chat(User user_1, User user_2, String id) {
        users.add(user_1);
        users.add(user_2);
        chats.add(this);
        this.id = id;

    }

    public List<User> getUsers() {
        return users;
    }

    public void addUser(User user) {
        users.add(user);
    }

    public String getID() {
        return id;
    }

    public static String newChat(User user_1, User user_2) {
        String id = Util.generateUniqueString(20, getChatIDs());
        new Chat(user_1, user_2, id);
        return id;
    }

    public static Chat getChatByID(String id) {
        for (Chat c : chats) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    public static List<Chat> getChats() {
        return chats;
    }

    public static List<String> getChatIDs() {
        List<String> ids = new ArrayList<>();
        for (Chat c : chats) {
            ids.add(c.id);
        }
        return ids;
    }

    public static Chat getChatByUser(User user) {
        for (Chat c : chats) {
            for (User u : c.users) {
                if (u.equals(user)) {
                    return c;
                }
            }
        }
        return null;
    }

    public static List<User> getPartnerUsersByConnection(WebSocket myconnection) {

        for (Chat c : chats) {
            List<User> partnerUsers = new ArrayList<>();
            for (User u : c.users) {
                if (!u.getConnection().equals(myconnection)) {
                    partnerUsers.add(u);
                }
            }
            if (c.users.contains(User.getUserByConnection(myconnection))) {
                return partnerUsers;
            }
        }
        return null;
    }


}
