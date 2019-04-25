package org.quaink.chat.server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    public static int PORT = 2333; // 服务器端监听端口
    public static int maxConnect = 30; // 最大连接数
    public static List<Socket> mList = new ArrayList<Socket>(); // 客户端 socket 列表
    public static ServerSocket server = null; // 服务器端 socket
    public static ExecutorService myExecutorService = null; // 线程池

    public static String serverMsgToSend; // 待发服务器消息
    public static String[] userMsgToSend = new String[maxConnect]; // 待发用户消息数组
    public static InetAddress[] userAddress = new InetAddress[maxConnect]; //　用户地址数组
    public static Date[] userLoginDate = new Date[30]; // 用户登录日期时间数组
    public static boolean serverRunFlag = false; // 服务器运行状态标记

    public ServerMain() {
        try {
            // 开始监听端口，启动服务器
            server = new ServerSocket(PORT);
            serverRunFlag = true;
            // 创建一个可缓存线程池
            myExecutorService = Executors.newCachedThreadPool();
            // 刷新日志
            ServerGUI.newLogLine = "服务器：服务器运行中，监听端口" + PORT;
            System.out.println("***" + getCurrentTimeString() + "***" + ServerGUI.newLogLine);
            ServerGUI.refreshLog();
            Socket client = null; // 客户端 socket
            // 循环阻塞等待客户端 socket 连接
            while (serverRunFlag) {
                client = server.accept();
                // 有客户端连接，获取地址
                String clientAddressString = client.getInetAddress().toString();

                /*
                 * 1. 下面注释的部分是为了测试程序在互联网（不同的 IP 网络）上是否成功实现功能。
                 *
                 * 2. 如果想要在本地网络（例如手机模拟器、同一台电脑的服务器端和客户端等，
                 * 即主机名为 localhost 或者 IP 为 127.0.0.1）测试，
                 * 则保持注释。
                 *
                 * 3. 如果仅测试互联网，则取消注释。
                 */
/*
                if (!(clientAddressString.equals("localhost") || clientAddressString.equals("/127.0.0.1"))) {
                    mList.add(client);
                    myExecutorService.execute(new Service(client));
                }
*/
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new ServerMain();
    }

    /* 获取当前时间并返回格式化的字符串 */
    public static String getCurrentTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }

    /* 停止服务器 */
    public static void stopServer() {
        Thread stopServerSubThread = new Thread(new Runnable() {
            @Override
            public void run() {
                serverRunFlag = false;
                ServerGUI.newLogLine = "服务器：正在断开所有客户端连接并关闭服务器";
                System.out.println("***" + getCurrentTimeString() + "***" + ServerGUI.newLogLine);
                ServerGUI.refreshLog();
                ServerGUI.btn_stop.setText("正在关闭");
                ServerGUI.btn_stop.setEnabled(false);
                try {
                    Socket tmpSocket = new Socket("localhost", PORT);
                    Thread.sleep(100);
                    tmpSocket.close();
                    server.close();
                    server = null;
                    ServerGUI.btn_stop.setText("关闭服务");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        stopServerSubThread.start();
    }

    /*
     * 内部类 Service，主要的业务逻辑
     * 每个客户端都对应一个实例化的该类对象
     */
    class Service implements Runnable {
        private final Socket socket;
        private BufferedReader in = null;

        public Service(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                // 0 序号
                ServerGUI.data[mList.size() - 1][0] = String.valueOf(mList.size() - 1);
                // 1 状态
                ServerGUI.data[mList.size() - 1][1] = "已连接";
                // 2 IP地址
                userAddress[mList.size() - 1] = this.socket.getInetAddress();
                ServerGUI.data[mList.size() - 1][2] = String.valueOf(this.socket.getInetAddress());
                // 3 用户名
                ServerGUI.data[mList.size() - 1][3] = String.valueOf(in.readLine());
                // 4 登录时间
                userLoginDate[mList.size() - 1] = new Date();
                ServerGUI.data[mList.size() - 1][4] = getCurrentTimeString();
                // 5 在线时长
                ServerGUI.data[mList.size() - 1][5] = "0 天 0 时 0 分 0 秒";
                serverMsgToSend = "用户" + ServerGUI.data[mList.size() - 1][3]
                        + "加入群聊，当前 " + mList.size() + " 人在线";
                this.sendMsg(-1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            String userMsgReceived;
            try {
                while (serverRunFlag) {
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    if ((userMsgReceived = in.readLine()) != null) {
                        for (int i = 0; i < userAddress.length; ++i) {
                            if (userAddress[i].equals(this.socket.getInetAddress())) {
                                userMsgToSend[i] = userMsgReceived;
                                this.sendMsg(i);
                                break;
                            }
                        }
                    } else {
                        mList.remove(socket);
                        for (int i = 0; i < userAddress.length; ++i) {
                            if (userAddress[i].equals(this.socket.getInetAddress())) {
                                serverMsgToSend = "服务器：用户" + ServerGUI.data[i][3]
                                        + "退出群聊，当前 " + mList.size()
                                        + " 人在线";
                                userAddress[i] = null;
                                userLoginDate[i] = null;
                                for (int j = 0; j < 6; j++) {
                                    ServerGUI.data[i][j] = "";
                                }
                                break;
                            }
                        }
                        in.close();
                        socket.close();
                        ServerGUI.newLogLine = serverMsgToSend;
                        ServerGUI.refreshLog();
                        this.sendMsg(-1);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /* 为连接上服务端的每个客户端发送信息 */
        public void sendMsg(int msgCreatorIndex) {
            int num = mList.size();

            for (int index = 0; index < num; index++) {
                Socket mSocket = mList.get(index);
                PrintWriter pout = null;
                try {
                    pout = new PrintWriter(
                            new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream(),
                                    StandardCharsets.UTF_8)), true);
                    if (msgCreatorIndex == -1) {
                        pout.println("***" + getCurrentTimeString() + "***" + "服务器：" + serverMsgToSend);
                        ServerGUI.newLogLine = "服务器：" + serverMsgToSend;
                    } else {
                        pout.println("***" + getCurrentTimeString() + "***"
                                + ServerGUI.data[msgCreatorIndex][3] + "：" + userMsgToSend[msgCreatorIndex]);
                        ServerGUI.newLogLine = ServerGUI.data[msgCreatorIndex][3] + "："
                                + userMsgToSend[msgCreatorIndex];
                    }
                    pout.flush();
                    System.out.println("***" + getCurrentTimeString() + "***" + ServerGUI.newLogLine);
                    ServerGUI.refreshLog();
                } catch (IOException e) {
                    e.printStackTrace();
                    ServerGUI.newLogLine = getCurrentTimeString() + "服务器：群发服务器/用户消息失败";
                    System.out.println("***" + getCurrentTimeString() + "***" + ServerGUI.newLogLine);
                    ServerGUI.refreshLog();
                }
            }
        }
    }
}
