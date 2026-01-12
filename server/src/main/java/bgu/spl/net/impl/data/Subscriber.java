package bgu.spl.net.impl.data;

public class Subscriber {
    private int connectionId;
    private int subId;
    public Subscriber(int connectionId, int subId){
        connectionId = connectionId;
        subId = subId;
    }
    public int getConnectionId(){
        return connectionId;
    }
    public int getSubId(){
        return subId;
    }
}
