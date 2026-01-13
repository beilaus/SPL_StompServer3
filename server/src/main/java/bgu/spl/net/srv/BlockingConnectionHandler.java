package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompProtocolImpl;
import bgu.spl.net.api.StompEncDec;
import bgu.spl.net.api.StompMessagingProtocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final MessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final StompMessagingProtocol<T> sprotocol;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, MessagingProtocol<T> protocol) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        sprotocol = null;
    }
    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, StompMessagingProtocol<T> sprotocol) {
        this.sock = sock;
        this.encdec = reader;
        this.sprotocol = sprotocol;
        this.protocol = null;
    }
    

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());
            if(protocol != null){
                while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                    T nextMessage = encdec.decodeNextByte((byte) read);
                    if (nextMessage != null) {
                        T response = protocol.process(nextMessage);
                        if (response != null) {
                            out.write(encdec.encode(response));
                            out.flush();
                        }
                    }
                }
            } 
            else if(sprotocol != null){
                while (!sprotocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                    T nextMessage = encdec.decodeNextByte((byte) read);
                    if (nextMessage != null) {
                        sprotocol.process(nextMessage);
                    }
                }
            }

        } 
        catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(T msg) {
        try {
            if(msg != null){
                out.write(encdec.encode(msg));
                out.flush();
            }
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
         
    }
}
