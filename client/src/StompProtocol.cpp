#include "../include/StompProtocol.h"
#include <sstream>
#include <vector>
#include <iostream>
#include "../src/StompClient.cpp"
#include <unordered_map>
#include "../include/event.h"
#include <fstream>
#include <iostream>

bool is_loggedin = false;
std::atomic<int> sub_id(0);
std::atomic<int> receipt_id(0);
std::map<std::string, int> channel_subid_map;
std::string current_username;
std::map<std::string, std::map<std::string, std::vector<Event>>> mapof_username_gamename_events;
std::map<int, std::string> receipt_to_command;


std::string StompProtocol::processKeyboardInput(std::string input, ConnectionHandler& connectionHandler){
    std::stringstream ss(input);
    std::string parse;
    std::vector<std::string> words;
    while(ss >> parse){
        words.push_back(parse);
    }
    if(words.empty()){
        return "";
    }
    std::string command = words[0];
    if(command == "login"){
        if(is_loggedin){
            std::cout << "The client is already logged in, log out before trying again" << "\n";
            return 0;
        }
        current_username = words[2]; //updates current username
        std::string sendToServer = "CONNECT\n"+words[1]+"\n"+"username:"+words[2]+"\npassword:"+words[3]+"\n\n";
        return sendToServer;
        //send to connectionhandler



        std::string response = "";
        if(response.find("CONNECTED") == 0){
            is_loggedin = true;
            return "Login successful";
        }
        if(response.find("ERROR") == 0){ // MUST MOVE THIS BLOCK TO SERVER INPUT PROCESS
            if(response.find("Wrong password") != std::string::npos){
                is_loggedin = false;
                return "Wrong password";
            }
            if(response.find("User already logged in") != std::string::npos){
                is_loggedin = false;
                return "User already logged in";
            }
            else{
                is_loggedin = false;
                return "Login failed";
            }
        }
            //if didn't get CONNECTED or ERROR then its socket error
        is_loggedin = false;
        std::cout << "Could not connect to server";
        return 0;
        //handle socket error somehow?
    }
    if(is_loggedin){
        if(command == "join"){
            int subid = get_subid_from_channel(words[1]);
            if(subid != -1){
                std::cout << "Already subscribed to channel "+words[1] << "\n";
                return 0;
            }
            std::string send_toserver = "SUBSCRIBE\ndestination:/"+words[1]+"\nid:"+std::to_string(sub_id.load())+"\nreceipt:"+
            std::to_string(receipt_id.load())+"\n\n";
            channel_subid_map.insert({words[1], sub_id.load()});
            receipt_to_command[receipt_id.load()] = "join";
            receipt_id++;
            sub_id++;
            return send_toserver;



            //send to connection handler
            std::string response = ""; //response from handler
            if(response.find("RECEIPT") == 0){
                return "Joined channel "+words[1]; //MUST MOVE THIS BLOCK TO SERVER INPUT PROCESS
            }
            else{
                is_loggedin = false;
                return "Error in subscription";
            }
        }

        if(command == "exit"){
            int subid = get_subid_from_channel(words[1]);
            if(subid != -1){
                std::string sendToServer = "UNSUBSCRIBE\nid:"+std::to_string(subid)+"\nreceipt:" +std::to_string(receipt_id.load())+"\n\n";
                receipt_to_command[receipt_id.load()] = "exit";
                receipt_id++;
                channel_subid_map.erase(words[1]);
                return sendToServer;
                //send to connection handler



                std::string response = ""; //response from handler
                if(response.find("RECEIPT") == 0){
                    return "Exited channel "+words[1];
                }
                is_loggedin = false;
                return "Server error";

            }
            std::cout << "You are not subscribed to channel "+words[1] << "\n";
            return 0;
        }

        if(command == "report"){
            names_and_events ne = parseEventsFile(words[1]);
            std::vector<Event> events = ne.events;
            std::vector<Event> first_half;
            auto it_partition = std::stable_partition(events.begin(), events.end(), [](const Event& e) {
                const std::map<std::string, std::string>& updates = e.get_game_updates();
                auto it = updates.find("before halftime");
                
                return (it != updates.end() && it->second == "true");
            });
            
            std::sort(events.begin(), it_partition, [](const Event& e1, const Event& e2){
                return e1.get_time() < e2.get_time();
            });

            std::sort(it_partition, events.end(), [](const Event& e1, const Event& e2){
                return e1.get_time() < e2.get_time();
            });         //Sorts the events by first half and second half, then by time!!

            std::string team_a = ne.team_a_name;
            std::string team_b = ne.team_b_name;
            std::string gamename = team_a+"_"+team_b;
            mapof_username_gamename_events[current_username][gamename] = events;
            std::string default_format = "SEND\ndestination:/"+gamename+"\n\nuser: "+current_username+"\nteam a: "+team_a+"\nteam b: "+team_b+"\n";
            for(const auto& event : events){
                std::string send_to_server = default_format+"event name: "+event.get_name()+"\ntime: "+std::to_string(event.get_time())+"\n";
                send_to_server += "general game updates:\n"+map_to_string(event.get_game_updates());
                send_to_server += "team a updates:\n" + map_to_string(event.get_team_a_updates());
                send_to_server += "team b updates:\n"+map_to_string(event.get_team_b_updates());
                send_to_server += "description:\n"+event.get_discription();
                send_to_server += "\n";
                //send to connnection handler one by one
                connectionHandler.sendFrameAscii(send_to_server, '\0');

                // HANDLE SERVER RESPONSE IN SERVER PROCESS INPUT
            }
            return 0;



        }

        if (command == "summary") {
            if (words.size() < 4) {
                std::cout << "Incorrect summary command" << "\n";
                return 0;
            }

            std::string gamename = words[1];
            std::string user = words[2];
            std::string file_name = words[3];

            if (mapof_username_gamename_events.find(user) == mapof_username_gamename_events.end() || 
                mapof_username_gamename_events[user].find(gamename) == mapof_username_gamename_events[user].end()) {
                std::cout << "No data found for user " << user << " in channel " << gamename << "\n";
                return 0;
            }

            std::vector<Event>& events = mapof_username_gamename_events[user][gamename];
            
            std::map<std::string, std::string> total_game_updates;
            std::map<std::string, std::string> total_team_a_updates;
            std::map<std::string, std::string> total_team_b_updates;
            std::string team_a = "";
            std::string team_b = "";
            std::stringstream event_reports;

            for (const auto& event : events) {
                if (team_a == "") team_a = event.get_team_a_name();
                if (team_b == "") team_b = event.get_team_b_name();

                for (auto const& [key, val] : event.get_game_updates()) total_game_updates[key] = val;
                for (auto const& [key, val] : event.get_team_a_updates()) total_team_a_updates[key] = val;
                for (auto const& [key, val] : event.get_team_b_updates()) total_team_b_updates[key] = val;

                event_reports << event.get_time() << " - " << event.get_name() << ":\n\n";
                event_reports << event.get_discription() << "\n\n";
            }

            std::ofstream out_file(file_name);
            if (out_file.is_open()) {
                out_file << team_a << " vs " << team_b << "\n";
                out_file << "Game stats:\nGeneral stats:\n" << map_to_string(total_game_updates);
                out_file << team_a << " stats:\n" << map_to_string(total_team_a_updates);
                out_file << team_b << " stats:\n" << map_to_string(total_team_b_updates);
                out_file << "Game event reports:\n" << event_reports.str();
                out_file.close();
                std::cout << "Summary for " + user + " generated in " + file_name << "\n";
                return 0;
            } 
            else {
                std::cout << "Error: Could not open file " + file_name + " for writing." << "\n";
                return 0;
            }
        }

        if(command == "logout"){
            std::string sendToServer = "DISCONNECT\nreceipt:"+std::to_string(receipt_id.load())+"\n\n";
            receipt_to_command[receipt_id.load()] = "logout";
            receipt_id++;
            channel_subid_map.clear();
            //send to connection handler
            std::string response = ""; //response from handler
            if(response.find("RECEIPT") == 0){
                is_loggedin = false;
                return "Logged out successfully";
            }
            else{
                is_loggedin = false;
                return "Error during logout";
            }
        }
    }
    else{
        //ERRRORRRRR
    }






    return "";
}

//TO DO:
std::string StompProtocol::processServerInput(std::string input) { // MUST CHANGE TO CORRECT LOGIC
    std::stringstream ss(input);
    std::string line;
    std::string frame_type;
    std::getline(ss, frame_type);

    // Remove any trailing \r or whitespace
    if (!frame_type.empty() && frame_type.back() == '\r') frame_type.pop_back();

    if (frame_type == "CONNECTED") {
        is_loggedin = true;
        return "Login successful";
    }

    if (frame_type == "RECEIPT") {
        std::string receiptIdLine;
        std::getline(ss, receiptIdLine);
        return "Receipt " + receiptIdLine + " received";
    }

    if (frame_type == "ERROR") {
        is_loggedin = false;
        return "Server Error: " + input;
    }

    if (frame_type == "MESSAGE") {
        std::string destination, subscription, messageId;
        std::string currentLine;
        
        // Parse Headers
        while (std::getline(ss, currentLine) && currentLine != "" && currentLine != "\r") {
            if (currentLine.find("destination:") == 0) {
                destination = currentLine.substr(13); // remove "destination:/"
                if (destination.back() == '\r') destination.pop_back();
            }
        }

        // Parse Body
        std::string body;
        std::string reporter_username;
        bool first_body_line = true;

        while (std::getline(ss, currentLine)) {
            if (first_body_line) {
                // The first line of the body in our protocol is "user: <name>"
                if (currentLine.find("user: ") == 0) {
                    reporter_username = currentLine.substr(6);
                    if (reporter_username.back() == '\r') reporter_username.pop_back();
                }
                first_body_line = false;
            } else {
                body += currentLine + "\n";
            }
        }

        // 1. Reconstruct the Event object using the body string
        Event receivedEvent(body);

        // 2. Insert into the global map
        mapof_username_gamename_events[reporter_username][destination].push_back(receivedEvent);

        // 3. Keep the events sorted by time for the summary command
        std::sort(mapof_username_gamename_events[reporter_username][destination].begin(), 
                  mapof_username_gamename_events[reporter_username][destination].end(), 
                  [](const Event& a, const Event& b) {
                      return a.get_time() < b.get_time();
                  });

        return "New report received from " + reporter_username + " in " + destination;
    }

    return "";
}

std::string map_to_string(std::map<std::string, std::string> event_data){
    std::string result;
    for(auto const& [key, value] : event_data){
        result += key +": "+value+"\n";
    }
    return result;
}

int get_subid_from_channel(std::string channel){
    auto it = channel_subid_map.find(channel);
    if (it != channel_subid_map.end()) {
        int id = it->second;
        return id;
    } 
    else{
        return -1; // not found
    }
}
