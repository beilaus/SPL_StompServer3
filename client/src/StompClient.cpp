#include <iostream>
#include <thread>
#include <string>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

// Thread 1: The Socket Reader
// Responsibility: Read every frame from the server and process it
void socketReader(ConnectionHandler &handler, StompProtocol &protocol) {
    while (true) {
        std::string frame;
        // Read from server until the null terminator '\0'
        if (!handler.getFrameAscii(frame, '\0')) {
            break; 
        }

        if (!frame.empty()) {
            std::string feedback = protocol.processServerInput(frame);
            if (!feedback.empty()) {
                std::cout << feedback << std::endl;
            }

            // If the server confirmed logout, exit the reader loop
            if (feedback == "Logged out successfully") {
                break;
            }
        }
    }
}

// Thread 2 (Main): The Keyboard Input
// Responsibility: Read user commands, generate frames, and send to server
int main(int argc, char *argv[]) {
    StompProtocol protocol;
    ConnectionHandler *handler = nullptr;
    std::thread *readerThread = nullptr;

    while (true) {
        std::string line;
        if (!std::getline(std::cin, line) || line.empty()) continue;

        std::stringstream ss(line);
        std::string command;
        ss >> command;

        // Command: Login
        if (command == "login") {
            if (handler != nullptr) {
                std::cout << "The client is already logged in, log out before trying again" << std::endl;
                continue;
            }

            std::string hostport, user, pass;
            ss >> hostport >> user >> pass;
            size_t colon = hostport.find(':');
            std::string host = hostport.substr(0, colon);
            short port = std::stoi(hostport.substr(colon + 1));

            handler = new ConnectionHandler(host, port);
            if (!handler->connect()) {
                std::cout << "Could not connect to server" << std::endl;
                delete handler;
                handler = nullptr;
                continue;
            }

            // Start Socket Reader thread
            readerThread = new std::thread(socketReader, std::ref(*handler), std::ref(protocol));

            // Generate and send CONNECT frame
            std::string connectFrame = protocol.processKeyboardInput(line, *handler);
            handler->sendFrameAscii(connectFrame, '\0');
        } 
        
        // Every other command
        else {
            if (handler == nullptr) {
                std::cout << "You must login first" << std::endl;
                continue;
            }

            // processKeyboardInput handles SEND, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT, and SUMMARY
            std::string frameToSend = protocol.processKeyboardInput(line, *handler);

            // If the protocol returns a STOMP frame, send it to the socket
            if (!frameToSend.empty() && frameToSend != "0") {
                if (!handler->sendFrameAscii(frameToSend, '\0')) {
                    std::cout << "Connection lost" << std::endl;
                }
            }

            // Cleanup if logout was successful (triggered by the reader thread later)
            if (command == "logout") {
                if (readerThread && readerThread->joinable()) {
                    readerThread->join();
                    delete readerThread;
                    readerThread = nullptr;
                }
                delete handler;
                handler = nullptr;
            }
        }
    }

    return 0;
}