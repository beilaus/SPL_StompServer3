package bgu.spl.net.srv;
import java.util.Set; // This fixes your current error
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;

public class ConnectionsImplementation<T> implements Connections<T>{

    private final ConcurrentHashMap<Integer,ConnectionHandler<T>> connections= new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Set<Integer>> connectionsByTopic= new ConcurrentHashMap<>();
        
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = connections.get(connectionId);
        if (handler == null) return false;
        handler.send(msg);
        return true;
    }

    public void send(String channel, T msg){
        Set<Integer>members= connectionsByTopic.get(channel);
        if(members!=null)
            for(Integer c:members){
                send(c,msg);
            }
    }

    public void disconnect(int connectionId){
            connections.remove(connectionId);
        for (Set<Integer> topicSubscribers : connectionsByTopic.values()) {
            topicSubscribers.remove(connectionId);
        }
    }
    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        connections.put(connectionId, handler);
    }
    public void subscribe(String channel, int connectionId) {
        connectionsByTopic.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(connectionId);
    }

}
