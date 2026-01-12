package bgu.spl.net.impl.data;

public class Subscriber {
    private int connectionId;
    private int subId;
    private String topic;
    public Subscriber(int connectionId, int subId, String topic){
        this.connectionId = connectionId;
        this.subId = subId;
        this.topic = topic;
    }
    public int getConnectionId(){
        return connectionId;
    }
    public int getSubId(){
        return subId;
    }
    public String getTopic(){
        return topic;
    }
}
