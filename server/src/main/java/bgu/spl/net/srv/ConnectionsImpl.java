package bgu.spl.net.srv;
import bgu.spl.net.impl.data.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.LinkedList;

public class ConnectionsImpl implements Connections<String>{
    private final Database database = Database.getInstance();
    private final ConcurrentHashMap<Integer,ConnectionHandler<String>> clientList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,CopyOnWriteArraySet<Subscriber>> clientsByTopic= new ConcurrentHashMap<>();
    private long message_id = 0;
        
    public boolean send(int connectionId, String msg) {
        ConnectionHandler<String> handler = clientList.get(connectionId);
        if (handler == null){
            return false;
        }
        handler.send(msg);
        return true;
    }

    public void send(String channel, String msg){
        CopyOnWriteArraySet<Subscriber> members= clientsByTopic.get(channel);
        String toSend = "";
        if(members!=null){
            for(Subscriber subscriber : members){
                toSend = "MESSAGE\nsubscription:"+subscriber.getSubId()+"\nmessage-id:"+message_id+"\ndestination:"+channel+"\n\n"+msg;
                send(subscriber.getConnectionId(), toSend);
            }
        }
    }
    public void disconnect(int connectionId){
        clientList.remove(connectionId); //only removes him from active clients, he remains in topics.
        LinkedList<String> topicsToRemove = database.getUser(connectionId).unSubscribeAll();
        for(String topic : topicsToRemove){
            CopyOnWriteArraySet<Subscriber> set = clientsByTopic.get(topic);
            for(Subscriber sub : set){
                if(connectionId == sub.getConnectionId()){
                    set.remove(sub);
                }
            }
        }
    }
    public void connect(int connectionId, ConnectionHandler<String> handler) {
        clientList.put(connectionId, handler); //Server will call this function
    }

    public boolean clientDuplicateSubId(int connectionId, int subId){
        return database.getUser(connectionId).subIdExists(subId);
    }
    
    public boolean clientAlreadySubscribedByTopic(int connectionId, String topic){
        return database.getUser(connectionId).alreadySubscribed(topic);
    }
    public boolean clientAlreadySubscribedBySubId(int connectionId, int subId){
        return database.getUser(connectionId).getSub(subId) != null;
    }

    public void addClientToTopic (int connectionId,int subId, String topic){ //Subscribe stomp
        Subscriber newSub = new Subscriber(connectionId, subId, topic);
        if(clientsByTopic.get(topic) == null){ //If topic doesn't exist we create it
            clientsByTopic.put(topic, new CopyOnWriteArraySet<>());
        }
        clientsByTopic.get(topic).add(newSub);
        database.getUser(connectionId).subscribe(newSub);
    }

    public void removeClientFromTopic (int connectionId, int subId){ //Unsubscribe stomp
        Subscriber subToRemove = database.getUser(connectionId).getSub(subId);
        clientsByTopic.get(subToRemove.getTopic()).remove(subToRemove);
        database.getUser(connectionId).unSubscribe(subId); //remove from user list
    }

    public boolean TopicExists(String topic){
        return clientsByTopic.contains(topic);
    }


    


}
