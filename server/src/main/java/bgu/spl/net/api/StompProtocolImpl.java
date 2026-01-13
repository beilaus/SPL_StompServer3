package bgu.spl.net.api;

import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

import java.util.HashMap;
import java.util.Map;

public class StompProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    private boolean isLoggedIn = false;

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    public void start(int connectionId, ConnectionsImpl connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        String[] parts = message.split("\n\n", 2); //Breaking down the given message to parts
        String headerSection = parts[0];
        String body = "";
        if(parts.length > 1){
            body = parts[1];
        }

        String[] headerLines = headerSection.split("\n");
        String command = headerLines[0].trim();
        Map<String, String> headers = parseHeaders(headerLines);
        if(headers == null){
            return;
        }
        if (!isLoggedIn && !command.equals("CONNECT")) {
            sendError(headers.get("receipt"), "Not logged in", "User must connect first.");
            shouldTerminate = true;
            return;
        }
        switch (command) {
            case "CONNECT":
                handleConnect(headers);
                break;
            case "SUBSCRIBE":
                handleSubscribe(headers);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(headers);
                break;
            case "SEND":
                handleSend(headers, body);
                break;
            case "DISCONNECT":
                handleDisconnect(headers);
                break;
            default:
                sendError(headers.get("receipt"), "Unknown Command", "Command '" + command + "' is not recognized.");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    // ================= Command Handlers =================

    private void handleConnect(Map<String, String> headers) {
        String version = headers.get("accept-version");
        String host = headers.get("host");
        String login = headers.get("login");
        String passcode = headers.get("passcode");
        String receipt = headers.get("receipt");

        if (!"1.2".equals(version)) {
            sendError(receipt, "Version mismatch", "Correct version is 1.2");
            shouldTerminate = true;
            return;
        }
        if (!"stomp.cs.bgu.ac.il".equals(host)) {
            sendError(receipt, "Invalid Host", "Host must be stomp.cs.bgu.ac.il");
            shouldTerminate = true;
            return;
        }

        LoginStatus status = Database.getInstance().login(connectionId, login, passcode);

        if (status == LoginStatus.LOGGED_IN_SUCCESSFULLY || status == LoginStatus.ADDED_NEW_USER) {
            isLoggedIn = true;
            String response = "CONNECTED\n"+"version:1.2\n\n";
            connections.send(connectionId, response);
            if(receipt != null){
                sendReceipt(receipt);
            }
        } 
        else {
            String errorMsg = "Login failed";
            if(status == LoginStatus.WRONG_PASSWORD){
                errorMsg = "Wrong password";
            }
            else if(status == LoginStatus.ALREADY_LOGGED_IN){
                errorMsg = "User already logged in";
            }
            sendError(receipt, "Login Failed", errorMsg);
            shouldTerminate = true;
        }
    }

    private void handleSubscribe(Map<String, String> headers) {
        String destination = headers.get("destination");
        String idStr = headers.get("id");
        String receipt = headers.get("receipt");

        if (destination == null || idStr == null) {
            sendError(receipt, "Invalid header", "Missing 'destination' or 'id' header.");
            return;
        }

        try {
            int subId = Integer.parseInt(idStr);
            if (connections instanceof ConnectionsImpl) {
                if(((ConnectionsImpl) connections).clientAlreadySubscribedByTopic(connectionId, destination) 
                    || ((ConnectionsImpl) connections).clientDuplicateSubId(connectionId, subId)){
                    sendError(receipt, "Invalid subscription", "Client already subscribed to topic or used an existing sub id");
                    shouldTerminate = true;
                    return;
                }
                ((ConnectionsImpl) connections).addClientToTopic(connectionId, subId, destination);
            }
            if (receipt != null) {
                sendReceipt(receipt);
            }
        } 
        catch (NumberFormatException e) {
            sendError(receipt, "Invalid header", "Subscription ID must be a number.");
        }
    }

    private void handleUnsubscribe(Map<String, String> headers) {
        String subIdStr = headers.get("id");
        String receipt = headers.get("receipt");

        if (subIdStr == null) {
            sendError(receipt, "Invalid header", "Missing 'id' header.");
            return;
        }

        try {
            int subId = Integer.parseInt(subIdStr);
            if (connections instanceof ConnectionsImpl) {
                if(!((ConnectionsImpl) connections).clientAlreadySubscribedBySubId(connectionId ,subId)){
                    sendError(receipt, "Invalid unsubscribtion", "Client is not subscribed with this sub id");
                    shouldTerminate = true;
                    return;
                }
                ((ConnectionsImpl) connections).removeClientFromTopic(connectionId, subId);
            }
            if (receipt != null) {
                sendReceipt(receipt);
            }
        } 
        catch (NumberFormatException e) {
            sendError(receipt, "Invalid header", "Subscription ID must be a number.");
        }
    }

    private void handleSend(Map<String, String> headers, String body) {
        String destination = headers.get("destination");
        String receipt = headers.get("receipt");

        if (destination == null) {
            sendError(receipt, "Invalid header", "Missing 'destination' header.");
            shouldTerminate = true;
            return;
        }

        boolean isSubscribed = false;
        if (connections instanceof ConnectionsImpl) {
            if(!((ConnectionsImpl) connections).TopicExists(destination)){
                sendError(receipt, "Invalid message", "destination does not exist");
                shouldTerminate = true;
                return;
            }
            isSubscribed = ((ConnectionsImpl) connections).clientAlreadySubscribedByTopic(connectionId, destination);
        }

        if (!isSubscribed) {
            sendError(receipt, "Access Denied", "Client is not subscribed to " + destination);
            shouldTerminate = true;
            return;
        }
        String msgFrameBody = body;
        connections.send(destination, msgFrameBody);
        if (receipt != null) {
            sendReceipt(receipt);
        }
    }

    private void handleDisconnect(Map<String, String> headers) {
        String receipt = headers.get("receipt");
        if (receipt == null) {
            sendError(receipt, "Invalid header", "Missing 'receipt' header.");
            shouldTerminate = true;
            return;
        }
        sendReceipt(receipt);
        isLoggedIn = false;
        shouldTerminate = true;
        
        connections.disconnect(connectionId);
        Database.getInstance().logout(connectionId);
    }

    
    private void sendReceipt(String receiptId) {
        String frame = "RECEIPT\nreceipt-id:" + receiptId + "\n\n";
        connections.send(connectionId, frame);
    }

    private void sendError(String receiptId, String message, String description) {
        StringBuilder frame = new StringBuilder();
        frame.append("ERROR\n");
        if (receiptId != null) {
            frame.append("receipt-id:").append(receiptId).append("\n");
        }
        frame.append("message:").append(message).append("\n");
        frame.append("\n");
        if (description != null) {
            frame.append(description);
        }
        connections.send(connectionId, frame.toString());
    }

    private Map<String, String> parseHeaders(String[] headerLines) {
        Map<String, String> map = new HashMap<>();
        
        String receipt = "";
        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i].trim();
            if (line.isEmpty()){
                for(String isReceipt : headerLines){
                    if(isReceipt.startsWith("receipt")){
                        receipt = isReceipt;
                        sendError(isReceipt, "Incorrect Headers", "Found an empty line within the headers");
                        shouldTerminate = true;
                        return null;
                    }
                }
                sendError(receipt, "Incorrect Headers", "Found an empty line within the headers");
                shouldTerminate = true;
                return null;
            }
            
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                map.put(parts[0].trim(), parts[1].trim());
            }
            else{
                sendError(receipt, "Incorrect Headers", "Missing ':' in header");
                shouldTerminate = true;
                return null;
            }
        }
        return map;
    }
}