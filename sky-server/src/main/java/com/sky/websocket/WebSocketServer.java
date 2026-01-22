package com.sky.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/{sid}") // sid 通常是商户ID或客户端标识
public class WebSocketServer {

    // 存放所有的客户端会话
    private static Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        System.out.println("客户端：" + sid + " 建立连接");
        sessionMap.put(sid, session);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        System.out.println("连接断开:" + sid);
        sessionMap.remove(sid);
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 群发消息（即你在 paySuccess 中调用的方法）
     * @param
     */
    public void sendToAllClient(String content) {
        Collection<Session> sessions = sessionMap.values();
        try {
            // 1. 构造一个简单的对象或 Map
            Map<String, Object> map = new HashMap<>();
            map.put("content", content);
            map.put("timestamp", System.currentTimeMillis());

            // 2. 将对象转为 JSON 字符串
            // 结果会变成: {"content":"这是来自服务端的消息...","timestamp":17000000000}
            String jsonMessage = objectMapper.writeValueAsString(map);

            for (Session session : sessions) {
                try {
                    // 3. 发送 JSON 字符串
                    session.getBasicRemote().sendText(jsonMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
