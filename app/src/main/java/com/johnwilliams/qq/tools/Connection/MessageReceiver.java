package com.johnwilliams.qq.tools.Connection;

import android.os.AsyncTask;
import android.os.Message;
import android.util.Log;

import com.johnwilliams.qq.Activities.ChatActivity;
import com.johnwilliams.qq.tools.Utils.Utils;
import com.johnwilliams.qq.tools.Message.ChatMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.datatype.BmobQueryResult;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.SQLQueryListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;

public class MessageReceiver implements Runnable{
    public List<ChatMessage> unread_msgs;
    public int unread_num = 0;

    public static String ServerPort = "50002";
    protected ServerSocket serverSocket = null;
    protected Socket clientSocket = null;
    protected boolean isStopped = false;
    protected Thread runningThread = null;
    protected ServerWorkerRunnable serverWorkerRunnable = null;


    public MessageReceiver(){
        unread_msgs = new ArrayList<>();
    }

    public static void saveMsg(final ChatMessage chatMessage) {
        String bql = "select * from ChatMessage where time = ?";
        BmobQuery<ChatMessage> chatMessageBmobQuery = new BmobQuery<>();
        chatMessageBmobQuery.doSQLQuery(bql, new SQLQueryListener<ChatMessage>() {
            @Override
            public void done(BmobQueryResult<ChatMessage> bmobQueryResult, BmobException e) {
                if (bmobQueryResult == null || bmobQueryResult.getResults().isEmpty())
                    chatMessage.save(new SaveListener<String>() {
                        @Override
                        public void done(String s, BmobException e) {

                        }
                    });
                if (e != null) {
                    e.printStackTrace();
                }
            }
        }, chatMessage.getTime());
    }
    public List<ChatMessage> readMsg(){
        List<ChatMessage> result = new ArrayList<>(unread_msgs);
        unread_msgs.clear();
        unread_num = 0;
        return result;
    }

    public ChatMessage get(int position){
        return unread_msgs.get(position);
    }

    static public void deleteMessages(final String stu_num) {
        String bql = "select * from ChatMessage where from_stunum = ? or to_stunum = ?";
        BmobQuery<ChatMessage> chatMessageBmobQuery = new BmobQuery<>();
        chatMessageBmobQuery.doSQLQuery(bql, new SQLQueryListener<ChatMessage>() {
            @Override
            public void done(BmobQueryResult<ChatMessage> bmobQueryResult, BmobException e) {
                if (bmobQueryResult == null) {
                    return;
                }
                List<ChatMessage> results = bmobQueryResult.getResults();
                for (ChatMessage result : results) {
                    result.delete(new UpdateListener() {
                        @Override
                        public void done(BmobException e) {

                        }
                    });
                }
            }
        }, stu_num, stu_num);
    }

    static public void updateMessages(final ChatMessage chatMessage) {
        String bql = "select * from ChatMessage where time = ?";
        BmobQuery<ChatMessage> chatMessageBmobQuery = new BmobQuery<>();
        chatMessageBmobQuery.doSQLQuery(bql, new SQLQueryListener<ChatMessage>() {
            @Override
            public void done(BmobQueryResult<ChatMessage> bmobQueryResult, BmobException e) {
                List<ChatMessage> results = bmobQueryResult.getResults();
                if (!results.isEmpty()) {
                    ChatMessage result = results.get(0);
                    result.setProgress(chatMessage.getProgress());
                    result.update(result.getObjectId(), new UpdateListener() {
                        @Override
                        public void done(BmobException e) {
                            if (e != null) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
                if (e != null) {
                    e.printStackTrace();
                }
            }
        }, chatMessage.getTime());
    }

    static public void fetchMessages(String my_stunum, String friend_stunum){
        final List<ChatMessage> chatMessages = new ArrayList<>();
        // fetch from bmob server
        String bql = "select * from ChatMessage where (from_stunum = ? and to_stunum = ?) " + // from me, to friend
                "or (from_stunum = ? and to_stunum = ?)"; // from friend, to me
        String bql_group = "select * from ChatMessage where to_stunum = ?";// from anyone. to the group
        BmobQuery<ChatMessage> chatMessageBmobQuery = new BmobQuery<>();
        if (!Utils.isGroupChat(friend_stunum)) {
            chatMessageBmobQuery.doSQLQuery(bql, new SQLQueryListener<ChatMessage>() {
                @Override
                public void done(BmobQueryResult<ChatMessage> bmobQueryResult, BmobException e) {
                    if (e == null){
                        List<ChatMessage> list = (List<ChatMessage>) bmobQueryResult.getResults();
                        if (list != null && list.size() > 0){
                            chatMessages.addAll(list);
                            Message msg = new Message();
                            msg.what = Utils.LOAD_DONE;
                            msg.obj = chatMessages;
                            ChatActivity.chatMessageHandler.sendMessage(msg);
                        }
                    }
                }
            }, my_stunum, friend_stunum, friend_stunum, my_stunum);
        } else {
            chatMessageBmobQuery.doSQLQuery(bql_group, new SQLQueryListener<ChatMessage>() {
                @Override
                public void done(BmobQueryResult<ChatMessage> bmobQueryResult, BmobException e) {
                    if (e == null){
                        List<ChatMessage> list = (List<ChatMessage>) bmobQueryResult.getResults();
                        if (list != null && list.size() > 0){
                            chatMessages.addAll(list);
                            Message msg = new Message();
                            msg.what = Utils.LOAD_DONE;
                            msg.obj = chatMessages;
                            ChatActivity.chatMessageHandler.sendMessage(msg);
                        }
                    }
                }
            }, friend_stunum);
        }

        // sort by time
        Comparator<ChatMessage> comparator = new Comparator<ChatMessage>() {
            @Override
            public int compare(ChatMessage o1, ChatMessage o2) {
                return o1.getTime().compareTo(o2.getTime());
            }
        };
        Collections.sort(chatMessages, comparator);
    }

    @Override
    public void run(){
        isStopped = false;
        synchronized (this){
            this.runningThread = Thread.currentThread();
        }
        AsyncTask<Void, Void, Void> serverTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                openServerSocket();
                while(!isStopped()){
                    try{
                        if (serverSocket != null)
                            clientSocket = serverSocket.accept();
                    } catch (Exception e){
                        if (isStopped()){
                            Log.d("ConnectionError", "Server Stopped");
                            return null;
                        }
                        throw new RuntimeException("Error accepting", e);
                    }
                    serverWorkerRunnable = new ServerWorkerRunnable(clientSocket);
                    new Thread(
                            serverWorkerRunnable
                    ).start();
                }
                Log.d("ConnectionFinish", "Server Stopped");
                return null;
            }
        };
        serverTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public synchronized boolean isStopped(){
        return isStopped;
    }

    public synchronized void stop(){
        isStopped = true;
        try {
            if (serverSocket != null){
                serverSocket.close();
                if (serverWorkerRunnable != null) {
                    serverWorkerRunnable.stop();
                }
                serverSocket = null;
            }
        } catch (IOException e){
            throw new RuntimeException("Fail to close Server", e);
        }
    }


    private void openServerSocket(){
        int port = Integer.parseInt(ServerPort);
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));
        } catch (IOException e){
            throw new RuntimeException("Cannot open port" + String.valueOf(port), e);
        }
    }

    public synchronized void receiveMsg(){
        List<ChatMessage> results = serverWorkerRunnable.getResults();
        unread_msgs.addAll(results);
        unread_num += results.size();
    }
}
