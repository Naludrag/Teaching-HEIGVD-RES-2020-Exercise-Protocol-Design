package ch.heigvd.res.client;

import ch.heigvd.res.common.Operator;
import ch.heigvd.res.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {
    final static Logger LOG = Logger.getLogger(Client.class.getName());

    Socket clientSocket;
    BufferedReader in;
    PrintWriter out;
    boolean connected = false;

    /**
     * This method is used to connect to the server on the default port.
     *
     * @param serverAddress the IP address used by the Presence Server
     */
    public void connect(String serverAddress) {
        connect(serverAddress, Protocol.PRESENCE_DEFAULT_PORT);
    }

    /**
     * This method is used to connect to the server.
     *
     * @param serverAddress the IP address used by the Presence Server
     * @param serverPort the port used by the Presence Server
     */
    public void connect(String serverAddress, int serverPort) {
        try {
            clientSocket = new Socket(serverAddress, serverPort);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(),Protocol.CHARSET));
            out = new PrintWriter(clientSocket.getOutputStream());
            connected = true;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Unable to connect to server: {0}", e.getMessage());
            cleanup();
            return;
        }

        // Let us send the HELLO command to connect us to the server
        out.println(Protocol.CMD_HELLO);
        out.flush();
        try {
            //See what the server responded to us
            LOG.log(Level.INFO, String.format("Server response: %s", in.readLine()));
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    public void disconnect() {
        LOG.log(Level.INFO, "Attempting to disconnect");
        connected = false;
        out.println(Protocol.CMD_BYE);
        cleanup();
    }

    private void cleanup() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }

        if (out != null) {
            out.close();
        }

        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    public String getCalculationResult(int a, int b, Operator op) {
        if (!connected) {
            LOG.log(Level.SEVERE, "Attempting to run getCalculationResult when not connected");
            return "";
        }

        String str = String.format("%d %s %d", a, op, b);
        LOG.log(Level.INFO, "Sending to server: {0}", str);
        out.println(str);
        out.flush();
        String response = "";
        try {
            response = in.readLine();
            LOG.log(Level.INFO, String.format("Server response: %s", response));
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }

        return response;
    }
}