/*
 * qk-chat-server
 *
 * @author QuainK
 * @version 0.1.0
 * @date 2019.03.31
 *
 * 基本实现功能。能提供与客户端的 TCP 连接，收发消息
 * 此版本只有命令行终端，没有 GUI
 */
package org.quaink.chat.server;

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
    private static final Map<Socket, String> userName = new HashMap<>(); // 用户名，通过 socket 对应
    private static final Map<Socket, String> userMsgToSend = new HashMap<>(); // 用户待发消息
    private static final Map<Socket, InetAddress> userIP = new HashMap<>(); // 用户IP，通过 socket 对应
    private static boolean serverRunFlag = false; // 服务器运行状态标记
    private static String serverMsgToSend = ""; // 服务器待发消息
    private static String newLogLine = ""; // 待打印日志消息

    private ServerMain() {
        int port = 2333; // 端口
        ExecutorService myExecutorService; // 线程池
        ServerSocket server; // ServerSocket 对象
        Socket client; // 客户端 socket
        try {
            server = new ServerSocket(port); // 实例化服务器 socket
            serverRunFlag = true; // 服务器开始运行
            myExecutorService = Executors.newCachedThreadPool(); // 创建线程池
            newLogLine = "服务器：服务器运行中，监听端口" + port; // 更新日志消息
            refreshLog();
            while (serverRunFlag) { // 服务器一直运行，一直循环判断等待用户 socket 连接
                client = server.accept(); // 阻塞等待用户socket连接
                // 如果用户 socket 连接成功，程序继续往下执行
                socketList.add(client); // 用户 socket 添加到线程池
                myExecutorService.execute(new Service(client)); // 为这个用户 socket 开启一个子线程
            }
        } catch (Exception e) {
            newLogLine = "服务器：服务器启动失败"; // 更新日志消息
            refreshLog();
            e.printStackTrace(); // 输出错误日志
        }
    }

    public static void main(String[] args) {
        new ServerMain(); // 匿名构造方法，实例化
    }

    /* 获取当前时间 */
    private String getCurrentTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }

    /* 刷新日志 */
    private void refreshLog() {
        System.out.println("---" + getCurrentTimeString() + "--- " + newLogLine + "\n");
    }

    /* 内部类 Service，指定线程运行的内容 */
    class Service implements Runnable { // 实现Runnable接口，用于实现多线程功能
        private final Socket socket; // Service 类私有 socket 对象，用于该线程存储用户 socket
        private BufferedReader in = null; // 输入流，准备用来读取“用户名”和后续消息

        Service(Socket socket) { // 构造方法
            // 等号左边是内部类 Service 私有对象，
            // 等号右边是外部类 ServerMain 的线程池 execute() 方法中传来的参数
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
                serverMsgToSend = "用户" + userName.get(this.socket)
                        + "加入群聊，当前" + socketList.size() + "人在线";
                sendServerMsg(); // 发送消息
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* 线程运行方法 */
        @Override
        public void run() {
            String userMsgReceived;
            try {
                while (serverRunFlag) { // 不断等待用户的输入
                    if ((userMsgReceived = in.readLine()) != null) { // 如果输入流不为空
                        userMsgToSend.put(socket, userMsgReceived); // 存储消息
                        sendUserMsg(socket);
                    } else {
                        socketList.remove(socket);
                        serverMsgToSend = "用户" + userName.get(socket)
                                + "退出群聊，当前" + socketList.size() + "人在线";
                        in.close();
                        socket.close();
                        sendServerMsg();
                        break;
                    }
                }
            } catch (IOException e) {
                serverRunFlag = false;
                socketList.remove(socket);
                e.printStackTrace();
            }
        }

        /* 为连接上服务端的每个客户端发送其他客户的消息 */
        void sendUserMsg(Socket userSocket) {
            for (int index = 0; index < socketList.size(); index++) {
                Socket currentSocket = socketList.get(index);
                try {
                    PrintWriter pout = new PrintWriter(
                            new BufferedWriter(new OutputStreamWriter(currentSocket.getOutputStream(),
                                    StandardCharsets.UTF_8)), true);
                    newLogLine = userName.get(userSocket) + "：" + userMsgToSend.get(userSocket);
                    pout.println("---" + getCurrentTimeString() + "--- " + newLogLine);
                    pout.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    newLogLine = getCurrentTimeString() + "服务器：群发用户消息失败";
                }
            }
            refreshLog();
        }

        /* 为连接上服务端的每个客户端发送服务器的消息 */
        void sendServerMsg() {
            for (int index = 0; index < socketList.size(); index++) {
                Socket currentSocket = socketList.get(index);
                try {
                    PrintWriter pout = new PrintWriter(
                            new BufferedWriter(new OutputStreamWriter(currentSocket.getOutputStream(),
                                    StandardCharsets.UTF_8)), true);
                    newLogLine = "服务器：" + serverMsgToSend;
                    pout.println("---" + getCurrentTimeString() + "--- " + newLogLine);
                    pout.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    newLogLine = getCurrentTimeString() + "服务器：群发服务器消息失败";
                }
            }
            refreshLog();
        }
    }
}
