package ch.heigvd.res.server;

import ch.heigvd.res.common.Protocol;
import ch.heigvd.res.common.Operator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements a multi-threaded TCP server. It is able to interact
 * with several clients at the time, as well as to continue listening for
 * connection requests.
 *
 * @author Olivier Liechti
 * @modified Stéphane Teixeira Carvalho
 */
public class Server implements Runnable{

    final static Logger LOG = Logger.getLogger(Server.class.getName());

    boolean shouldRun;
    ServerSocket serverSocket;
    final List<Worker> connectedWorkers;

    public Server() {
        this.shouldRun = true;
        this.connectedWorkers = Collections.synchronizedList(new LinkedList<Worker>());
    }

    private void registerWorker(Worker worker) {
        LOG.log(Level.INFO, ">> Waiting for lock before registring worker");
        connectedWorkers.add(worker);
        //Add message to the worker
        LOG.log(Level.INFO, "<< Worker registered.");
    }

    private void unregisterWorker(Worker worker) {
        LOG.log(Level.INFO, ">> Waiting for lock before unregistring worker");
        connectedWorkers.remove(worker);
        LOG.log(Level.INFO, "<< Worker unregistered.");
    }

    private void disconnectConnectedWorkers() {
        LOG.info(">> Waiting for lock before disconnecting workers");
        synchronized (connectedWorkers) {
            LOG.info("Disconnecting workers");
            for (Worker worker : connectedWorkers) {
                worker.disconnect();
            }
        }
        LOG.info("<< Workers disconnected");
    }

    @Override
    public void run() {
        try {
            LOG.log(Level.INFO, "Starting Presence Server on port {0}", Protocol.PRESENCE_DEFAULT_PORT);
            serverSocket = new ServerSocket(Protocol.PRESENCE_DEFAULT_PORT);
            while (shouldRun) {
                Socket clientSocket = serverSocket.accept();
                Worker newWorker = new Worker(clientSocket);
                registerWorker(newWorker);
                new Thread(newWorker).start();
            }
            serverSocket.close();
            LOG.info("shouldRun is false... server going down");
        } catch (IOException ex) {
            if(ex instanceof SocketException){
                LOG.info("socket closed server is going down");
            }else {
                LOG.log(Level.SEVERE, ex.getMessage(), ex);
                System.exit(-1);
            }
        }
    }

    public void shutdown() {
        LOG.info("Shutting down server...");
        shouldRun = false;
        try {
            serverSocket.close();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
        disconnectConnectedWorkers();
    }

    class Worker implements Runnable {

        Socket clientSocket;
        BufferedReader in;
        PrintWriter out;
        boolean connected;

        public Worker(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(),Protocol.CHARSET));
                out = new PrintWriter(clientSocket.getOutputStream());
                connected = true;
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }

        @Override
        public void run() {
            String commandLine;
            try {
                while (connected && ((commandLine = in.readLine()) != null)) {
                    String[] tokens = commandLine.split(" ");
                    if (tokens[0].toUpperCase().equals(Protocol.CMD_BYE)) {
                        connected = false;
                    } else if (tokens[0].toUpperCase().equals(Protocol.CMD_HELLO)) {
                        sendNotification("HELLO, GIVE CALCULATIONS(supported operators : + - * /)");
                    } else {
                        Calculator calculator = new Calculator(tokens);
                        new Thread(calculator).start();
                    }
                }
            } catch (IOException ex) {
                if (!shouldRun){
                    LOG.info("socket closed server is going down");
                }else {
                    LOG.log(Level.SEVERE, ex.getMessage(), ex);
                    System.exit(-1);
                }
            } finally {
                unregisterWorker(this);
                cleanup();
            }
        }

        private void cleanup() {
            LOG.log(Level.INFO, "Cleaning up worker used by worker");

            LOG.log(Level.INFO, "Closing clientSocket used by worker");
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, ex.getMessage(), ex);
            }

            LOG.log(Level.INFO, "Closing in used by worker");
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, ex.getMessage(), ex);
            }

            LOG.log(Level.INFO, "Closing out used by a worker");
            if (out != null) {
                out.close();
            }

            LOG.log(Level.INFO, "Clean up done for worker");
        }

        public void sendNotification(String message) {
            out.println(message);
            out.flush();
        }

        private void disconnect() {
            LOG.log(Level.INFO, "Disconnecting worker");
            connected = false;
            cleanup();
        }

        class Calculator implements Runnable {
            String[] tokens;
            int result;
            int operand1;
            int operand2;

            public Calculator(String[] tokens) {
                this.tokens = tokens;
                result = 0;
                operand1 = 0;
                operand2 = 0;
            }

            @Override
            public void run() {
                //If tokens has more than 3 arguments error
                if (tokens.length > 3) {
                    sendNotification("Please send another calcul wrong number of arguments");
                } else {
                    try {
                        operand1 = Integer.parseInt(tokens[0]);
                        operand2 = Integer.parseInt(tokens[2]);
                    } catch (NumberFormatException e) {
                        sendNotification("Please send another calcul number incorrect");
                    } finally {
                        //Search in the operators if the one passed is in the list
                        Operator op = Operator.getOperator(tokens[1]);
                        if (op == null) {
                            sendNotification("Unknown operator");
                        } else {
                            result = op.eval(operand1, operand2);
                            sendNotification(operand1 + " " + op.toString() + " " + operand2 + " = " + result);
                        }
                    }
                }
            }
        }
    }


}