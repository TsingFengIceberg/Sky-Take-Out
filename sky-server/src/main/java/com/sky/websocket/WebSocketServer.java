package com.sky.websocket;

import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
// 🚨 重点：将固定路径改为动态路径 {sid}
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    private static Collection<Session> sessions = new CopyOnWriteArraySet<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        // 🚨 这里的 sid 就能接住你日志里那个 "kkg55yee9os"
        System.out.println("🚀 商家后台已连接 WebSocket，客户端ID：" + sid);
        sessions.add(session);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session, @PathParam("sid") String sid) {
        System.out.println("🔌 商家后台已断开 WebSocket：" + sid);
        sessions.remove(session);
    }

    /**
     * 群发消息
     */
    public void sendToAllClient(String message) {
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}