#include "../include/StompProtocol.h"
#include <sstream>
#include <vector>
#include <iostream>
#include "../src/StompClient.cpp"
#include <unordered_map>

bool is_loggedin = false;
std::atomic<int> sub_id(0);
std::atomic<int> receipt_id(0);
std::unordered_map<std::string, int> channel_subid_map;

std::string StompProtocol::processKeyboardInput(std::string input){
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
            return "The client is already logged in, log out before trying again";
        }
        std::string sendToServer = "CONNECT\n"+words[1]+"\n"+"username:"+words[2]+"\npassword:"+words[3]+"\n\n\0";
        //send to connectionhandler
        std::string response = "";
        if(response.find("CONNECTED") == 0){
            is_loggedin = true;
            return "Login successful";
        }
        if(response.find("ERROR") == 0){
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
        return "Could not connect to server";
        //handle socket error somehow?
    }
    if(is_loggedin){
        if(command == "join"){
            std::string send_toserver = "SUBSCRIBE\ndestination:/"+words[1]+"\nid:"+std::to_string(sub_id.load())+"\nreceipt:"+
            std::to_string(receipt_id.load())+"\n\n\0";
            channel_subid_map.insert({words[1], sub_id.load()});
            //send to connection handler
            std::string response = ""; //response from handler
            if(response.find("RECEIPT") == 0){
                return "Joined channel "+words[1];
            }
            else{
                return "Error in subscription";
            }
        }

        if(command == "exit"){
            int subid = get_subid_from_channel(words[1]);
            if(subid != -1){
                std::string sendToServer = "UNSUBSCRIBE\nid:"+std::to_string(subid)+"\nreceipt:" +std::to_string(receipt_id.load())+"\n\n\0";
                //send to connection handler
                std::string response = ""; //response from handler
                if(response.find("RECEIPT") == 0){
                    return "Exited channel "+words[1];
                }
                return "Server error";

            }
            return "You are not subscribed to channel "+words[1];
        }

        if(command == "report"){
            
        }

        if(command == "summary"){

        }

        if(command == "logout"){

        }
    }
    else{
        //ERRRORRRRR
    }






    return "";
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