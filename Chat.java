package server;

import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;

public class Chat {
    private User user_1;
    private User user_2;
    private static List<Chat> chats = new ArrayList<>();

    public Chat(User user_1, User user_2) {
        this.user_1 = user_1;
        this.user_2 = user_2;
        chats.add(this);
    }

    public static List<Chat> getChats() {
        return chats;
    }

    public static Chat getChatByUser(User user) {
        for (Chat c : chats) {
            if (c.user_1.equals(user) || c.user_2.equals(user)) {
                return c;
            }
        }
        return null;
    }

    public static User getPartnerUserByConnection(WebSocket myconnection) {
        for (Chat c : chats) {
            if (c.user_2.getConnection().equals(myconnection)) {
                return c.user_1;
            }
            if (c.user_1.getConnection().equals(myconnection)) {
                return c.user_2;
            }
        }
        return null;
    }
}
