package bgu.spl.net.srv;
import bgu.spl.net.impl.data.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.LinkedList;

public class ConnectionsImpl<T> implements Connections<T>{

    private final ConcurrentHashMap<Integer,ConnectionHandler<T>> clientList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,CopyOnWriteArraySet<Subscriber>> clientsByTopic= new ConcurrentHashMap<>();
        
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = clientList.get(connectionId);
        if (handler == null){
            return false;
        }
        handler.send(msg);
        return true;
    }

    public void send(String channel, T msg){
        CopyOnWriteArraySet<Subscriber> members= clientsByTopic.get(channel);
        if(members!=null){
            for(Subscriber subscriber : members){
                if(clientList.get(subscriber.getConnectionId()) != null){ //if client is logged in, send msg
                    send(subscriber.getConnectionId(), msg);
                }
            }
        }
    }
    public void disconnect(int connectionId){
        clientList.remove(connectionId); //only removes him from active clients, he remains in topics.
    }
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        clientList.put(connectionId, handler); //Server will call this function
    }
    


}
