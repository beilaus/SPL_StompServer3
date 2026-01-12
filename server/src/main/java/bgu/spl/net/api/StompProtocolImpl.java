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

    @Override
    public void process(String message) {
        // 1. Split Command, Headers, and Body
        String[] parts = message.split("\n\n", 2);
        String headerSection = parts[0];
        String body = (parts.length > 1) ? parts[1] : "";

        // 2. Parse Command and Headers
        String[] headerLines = headerSection.split("\n");
        String command = headerLines[0].trim();
        Map<String, String> headers = parseHeaders(headerLines);

        // 3. Validation: Must be logged in for any command except CONNECT
        if (!isLoggedIn && !command.equals("CONNECT")) {
            sendError(headers.get("receipt"), "Not logged in", "You must connect first.");
            shouldTerminate = true; // Strict STOMP behavior often disconnects on protocol violation
            return;
        }

        // 4. Dispatch Command
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

        if (!"1.2".equals(version)) {
            sendError(null, "Version mismatch", "Supported version is 1.2");
            shouldTerminate = true;
            return;
        }
        if (!"stomp.cs.bgu.ac.il".equals(host)) {
            sendError(null, "Invalid Host", "Host must be stomp.cs.bgu.ac.il");
            shouldTerminate = true;
            return;
        }

        LoginStatus status = Database.getInstance().login(connectionId, login, passcode);

        if (status == LoginStatus.LOGGED_IN_SUCCESSFULLY || status == LoginStatus.ADDED_NEW_USER) {
            isLoggedIn = true;
            String response = "CONNECTED\n" +
                              "version:1.2\n" +
                              "\n"; 
            connections.send(connectionId, response);
        } else {
            String errDetail = (status == LoginStatus.WRONG_PASSWORD) ? "Wrong password" : 
                               (status == LoginStatus.ALREADY_LOGGED_IN) ? "User already logged in" : "Login failed";
            sendError(null, "Login Failed", errDetail);
            shouldTerminate = true;
        }
    }

    private void handleSubscribe(Map<String, String> headers) {
        String destination = headers.get("destination");
        String idStr = headers.get("id");
        String receipt = headers.get("receipt");

        if (destination == null || idStr == null) {
            sendError(receipt, "Malformed Frame", "Missing 'destination' or 'id' header.");
            return;
        }

        try {
            int subId = Integer.parseInt(idStr);
            // Safe cast since we know the implementation
            if (connections instanceof ConnectionsImpl) {
                ((ConnectionsImpl<String>) connections).addClientToTopic(connectionId, subId, destination);
            }
            
            if (receipt != null) {
                sendReceipt(receipt);
            }
        } catch (NumberFormatException e) {
            sendError(receipt, "Malformed Frame", "Subscription ID must be an integer.");
        }
    }

    private void handleUnsubscribe(Map<String, String> headers) {
        String idStr = headers.get("id");
        String receipt = headers.get("receipt");

        if (idStr == null) {
            sendError(receipt, "Malformed Frame", "Missing 'id' header.");
            return;
        }

        try {
            int subId = Integer.parseInt(idStr);
            if (connections instanceof ConnectionsImpl) {
                ((ConnectionsImpl<String>) connections).removeClientFromTopic(connectionId, subId);
            }

            if (receipt != null) {
                sendReceipt(receipt);
            }
        } catch (NumberFormatException e) {
            sendError(receipt, "Malformed Frame", "Subscription ID must be an integer.");
        }
    }

    private void handleSend(Map<String, String> headers, String body) {
        String destination = headers.get("destination");
        String receipt = headers.get("receipt");

        if (destination == null) {
            sendError(receipt, "Malformed Frame", "Missing 'destination' header.");
            return;
        }

        // Check if client is subscribed to the topic they are sending to
        boolean isSubscribed = false;
        if (connections instanceof ConnectionsImpl) {
            isSubscribed = ((ConnectionsImpl<String>) connections).clientAlreadySubscribed(connectionId, destination);
        }

        if (!isSubscribed) {
            sendError(receipt, "Access Denied", "You are not subscribed to " + destination);
            return;
        }

        // Construct the MESSAGE frame
        // Note: In a real STOMP server, 'subscription' header should match the receiver's ID.
        // With the current Connections interface, we broadcast the same String to all.
        String msgFrame = "MESSAGE\n" +
                          "destination:" + destination + "\n" +
                          "message-id:" + System.currentTimeMillis() + "\n" +
                          "subscription:0\n" + 
                          "\n" +
                          body;

        connections.send(destination, msgFrame);

        if (receipt != null) {
            sendReceipt(receipt);
        }
    }

    private void handleDisconnect(Map<String, String> headers) {
        String receipt = headers.get("receipt");
        if (receipt != null) {
            sendReceipt(receipt);
        }
        
        isLoggedIn = false;
        shouldTerminate = true;
        
        connections.disconnect(connectionId);
        Database.getInstance().logout(connectionId);
    }

    // ================= Helper Methods =================

    private void sendReceipt(String receiptId) {
        String frame = "RECEIPT\n" +
                       "receipt-id:" + receiptId + "\n" +
                       "\n";
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
        // Start from 1 to skip the command line
        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i].trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }
}