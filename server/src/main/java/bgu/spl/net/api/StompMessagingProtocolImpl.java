package bgu.spl.net.api;

import javax.print.DocFlavor.STRING;

import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl<String> implements StompMessagingProtocol<String>{
    private int id;
    private boolean shouldTerminate;
    private Connections<String> connections;

    public void start(int connectionId, Connections<String> connections){
        this.id=connectionId;
        this.connections=connections;
        shouldTerminate=false;
    }

    
    public void process(String message){
        String commands = message;
        switch (commands) {
            case "CONNECT":
                handleConnect();
                break;

            case "SEND":
                handleSend();
                break;

            case "SUBSCRIBE":
                handleSubscribe();
                break;

            case "UNSUBSCRIBE":
                handleUnsubscribe();
                break;

            case "DISCONNECT":
                handleDisconnect();
                break;
        }


    }
	
	/**
     * @return true if the connection should be terminated
     */
   public  boolean shouldTerminate(){
    return this.shouldTerminate;

    }
    private void handleConnect(String[] lines) {  }
    private void handleSend(String[] lines, String body) {  }
    private void handleSubscribe(String[] lines) {  }
    private void handleUnsubscribe(String[] lines) {  }
    private void handleDisconnect(String[] lines) { }
    private void sendError(){}
    private void reciept(){}


}
