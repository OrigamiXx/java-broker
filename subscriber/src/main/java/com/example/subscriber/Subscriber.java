package com.example.subscriber;

import java.io.*;
import java.net.*;

/**
 * The Subscriber class connects to a broker and manages subscriptions to topics.
 * It supports commands such as display, subscribe, current, and unsubscribe.
 */
public class Subscriber {

    static String username;

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java -jar subscriber.jar <username> <broker_ip> <broker_port>");
            return;
        }

        username = args[0];
        String brokerIp = args[1];
        int brokerPort = Integer.parseInt(args[2]);

        // Establish a connection to the broker
        Socket socket = new Socket(brokerIp, brokerPort);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        BufferedReader brokerReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.println("SUB"); // Identify as a subscriber

        // Thread to handle incoming messages from the broker
        new Thread(() -> {
            try {
                String message;
                while ((message = brokerReader.readLine()) != null) {
                    System.out.println("[Response from Broker]: " + message);
                    if (message.equals("close")) {
                        socket.close();
                    }
                }
            } catch (IOException e) {
                if (e.getMessage().equals("socket closed")) {
                    System.exit(0);
                }
            }
        }).start();

        System.out.println("Connected to broker as " + username);

        // Main loop to handle user commands
        while (true) {
            System.out.println("Please select a command: display, subscribe, current, unsubscribe.");
            String command = reader.readLine();
            String[] parts = command.split(" ");

            switch (parts[0].toLowerCase()) {
                case "display":
                    displayTopics(parts, out);
                    break;
                case "subscribe":
                    subscribe(parts, out);
                    break;
                case "current":
                    currentSubscriptions(out);
                    break;
                case "unsubscribe":
                    unsubscribe(parts, out);
                    break;
                default:
                    System.out.println("[ERROR] Illegal instruction.");
            }
        }
    }

    /**
     * Displays all available topics from the broker.
     * @param parts Command arguments
     * @param out Output stream to send messages to the broker
     */
    private static void displayTopics(String[] parts, PrintWriter out) {
        if (parts.length != 1 || !parts[0].equals("display")) {
            System.out.println("[ERROR] Show parameter error.");
            return;
        }
        String message = "DISPLAY";
        out.println(message);
    }

    /**
     * Subscribes to a specific topic.
     * @param parts Command arguments
     * @param out Output stream to send messages to the broker
     */
    private static void subscribe(String[] parts, PrintWriter out) {
        if (parts.length != 2) {
            System.out.println("[ERROR] Parameter error.");
            return;
        }

        String topicId = parts[1];
        String message = "SUBSCRIBE " + topicId + " " + username;
        out.println(message);
    }

    /**
     * Displays the current subscriptions of the subscriber.
     * @param out Output stream to send messages to the broker
     */
    private static void currentSubscriptions(PrintWriter out) {
        String message = "CURRENT " + username;
        out.println(message);
    }

    /**
     * Unsubscribes from a specific topic.
     * @param parts Command arguments
     * @param out Output stream to send messages to the broker
     */
    private static void unsubscribe(String[] parts, PrintWriter out) {
        if (parts.length != 2) {
            System.out.println("[ERROR] Parameter error.");
            return;
        }

        String topicId = parts[1];
        String message = "UNSUBSCRIBE " + topicId + " " + username;
        out.println(message);
    }
}
