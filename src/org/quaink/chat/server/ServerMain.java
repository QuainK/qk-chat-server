package org.quaink.chat.server;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    private static final List<Socket> socketList = new ArrayList<>(); // socket 连接列表
    private static final Map<Socket, InetAddress> userIP = new HashMap<>(); // 用户IP，通过 socket 对应
    private static final Map<Socket, String> userName = new HashMap<>(); // 用户名，通过 socket 对应
    private static final Map<Socket, String> userMsgToSend = new HashMap<>(); // 用户待发消息
    private static String serverMsgToSend = ""; // 服务器待发消息

    private ServerMain() {
        int port = 2333; // 端口
        ExecutorService myExecutorService; // 线程池
        ServerSocket server; // 服务器端socket
        Socket client; // 客户端socket
        try {
            server = new ServerSocket(port); // 实例化服务器 socket
            myExecutorService = Executors.newCachedThreadPool(); // 创建线程池
            refreshLog("服务器：开始运行，监听端口" + port); // 更新日志消息
            // 服务器一直等待用户socket连接
            while (true) {
                client = server.accept(); // 阻塞等待用户socket连接
                // 如果用户 socket 连接成功，程序继续往下执行
                socketList.add(client); // 用户 socket 添加到列表
                myExecutorService.execute(new Service(client)); // 为这个用户 socket 开启一个子线程
            }
        } catch (Exception e) {
            refreshLog("服务器：启动失败"); // 更新日志消息
            e.printStackTrace(); // 输出错误日志
        }
    }

    public static void main(String[] args) {
        new ServerMain();
    }

    /* 获取当前时间并返回格式化的字符串 */
    private static String getCurrentTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }

    /* 刷新日志 */
    private static void refreshLog(String newStringLine) {
        System.out.println("---" + getCurrentTimeString() + "--- " + newStringLine);
    }

    /* 获取用户连接状态，判断是否还在线 */
    private static boolean getUserConnectionState(String newJSONString) {
        try {
            JSONObject get_root = new JSONObject(newJSONString);
            return get_root.getBoolean("userConnectionState");
        } catch (JSONException e) {
            return false;
        }
    }

    /* 解析 JSON 并返回字符串  */
    private static String getJSONContent(String newJSONString) {
        try {
            JSONObject get_root = new JSONObject(newJSONString);
            return get_root.getString("msgContent");
        } catch (JSONException e) {
            e.printStackTrace();
            return "处理消息出错";
        }
    }

    /* 将信息封装成 JSON */
    private static String setJSONContent(String msgTime, String senderName, String newMsgToJSON) {
        try {
            JSONObject set_root = new JSONObject();
            set_root.put("msgTime", msgTime);
            set_root.put("msgSender", senderName);
            set_root.put("msgContent", newMsgToJSON);
            return set_root.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "处理消息出错";
        }
    }

    /* 为连接上服务端的每个客户端发送信息 */
    private void sendUserMsg(Socket userSocket) {
        refreshLog(userName.get(userSocket) + "：" + userMsgToSend.get(userSocket));
        for (Socket currentSocket : socketList) {
            try {
                PrintWriter pout = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(currentSocket.getOutputStream(),
                                StandardCharsets.UTF_8.name())), true);
                pout.println(setJSONContent(getCurrentTimeString(), userName.get(userSocket),
                        userMsgToSend.get(userSocket)));
                pout.flush();
            } catch (IOException e) {
                e.printStackTrace();
                refreshLog("服务器：消息发送失败，发送对象"
                        + userName.get(currentSocket) + "位于" + userIP.get(currentSocket));
            }
        }
    }

    /* 群发服务器消息 */
    private void sendServerMsg() {
        refreshLog("服务器：" + serverMsgToSend);
        for (Socket currentSocket : socketList) {
            try {
                PrintWriter pout = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(currentSocket.getOutputStream(),
                                StandardCharsets.UTF_8.name())), true);
                pout.println(setJSONContent(getCurrentTimeString(), "服务器", serverMsgToSend));
                pout.flush();
            } catch (IOException e) {
                e.printStackTrace();
                refreshLog("服务器：消息发送失败，发送对象"
                        + userName.get(currentSocket) + "位于" + userIP.get(currentSocket));
            }
        }
    }

    /* 服务器的主要业务逻辑，多线程 */
    class Service implements Runnable {
        private final Socket socket; // 用户 socket
        private BufferedReader in; // 输入流

        Service(Socket socket) {
            this.socket = socket;
            try {
                // 输入流
                in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                // IP地址
                userIP.put(this.socket, this.socket.getInetAddress());
                // 用户名
                // 用户第一次的输入流是用户名
                userName.put(this.socket, String.valueOf(in.readLine()));
                // 服务器准备发出消息，新用户连接
                serverMsgToSend = userName.get(this.socket) + "加入群聊，当前" + socketList.size() + "人在线";
                sendServerMsg(); // 发送消息
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* 线程运行方法 */
        @Override
        public void run() {
            String userMsgReceived;
            while (true) {
                try {
                    userMsgReceived = in.readLine(); // 不断等待用户的输入
                    if (getUserConnectionState(userMsgReceived)) {
                        userMsgToSend.put(socket, getJSONContent(userMsgReceived)); // 消息临时存储
                        sendUserMsg(socket);
                    } else {
                        try {
                            in.close();
                            socket.close();
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                        socketList.remove(socket);
                        serverMsgToSend = userName.get(socket) + "退出群聊，当前" + socketList.size() + "人在线";
                        sendServerMsg();
                        break;
                    }
                } catch (Exception e) {
                    try {
                        in.close();
                        socket.close();
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                    socketList.remove(socket);
                    serverMsgToSend = userName.get(socket) + "退出群聊，当前" + socketList.size() + "人在线";
                    sendServerMsg();
                    e.printStackTrace();
                    break;
                }
            }
        }
    }
}
