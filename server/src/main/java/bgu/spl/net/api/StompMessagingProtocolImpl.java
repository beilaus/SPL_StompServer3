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
        String command="d";
        switch (command) {
        case "CONNECT":
            handleConnect();
            break;

        case "SEND":
            handleSend(lines);
            break;

        case "SUBSCRIBE":
            handleSubscribe(lines);
            break;

        case "UNSUBSCRIBE":
            handleUnsubscribe(lines);
            break;

        case "DISCONNECT":
            handleDisconnect(lines);
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
