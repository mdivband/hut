package tool;

import server.model.State;

import java.util.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 Socket communication server for accepting incoming request (from VR headset)
 and handling the sending of periodic status updates.
 **/
public class SocketServer {
    private static Logger LOGGER = Logger.getLogger(SocketServer.class.getName());
    private static final int port = 5555;
    private Socket clientSocket;
    private State state;

    public SocketServer(State state) {
        this.state = state;
        //start socket server
        this.waitForClient();
    }

    /**
     * Wait for client to connect to socket server.
     * Currently a blocking process, only supports one client (the VR headset).
     */
    private void waitForClient(){
        ServerSocket serverSocket = null;
        try{
            serverSocket = new ServerSocket(port);
            LOGGER.info("Started server socket, monitoring port " + port + "...");
            // waits idle until connection is established
            clientSocket = serverSocket.accept();
            LOGGER.info("Socket connected!");

        }catch (Exception ex){
            LOGGER.warning("Socket connection failed!");
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            LOGGER.warning("Server delayed restart failed!");
        }
    }

    /**
     * Sends the current simulator status in JSON format to the client.
     */
    public void sendStatus() {
        OutputStream out = null;
        try {
            out = this.clientSocket.getOutputStream();
            String stateJson = GsonUtils.toJson(this.state);
            out.write(stateJson.getBytes());
//            out.write(new String("here's a status update!").getBytes());
            LOGGER.info("Message sent.");
        } catch (IOException e) {
            LOGGER.warning("Message failed to send.");
        }
    }
}
