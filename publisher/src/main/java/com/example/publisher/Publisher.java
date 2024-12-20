package com.example.publisher;

import java.io.*;
import java.net.*;
import java.util.Arrays;

/**
 * The Publisher class is responsible for connecting to a broker and managing topics and messages.
 * It supports commands such as create, publish, show, and delete.
 */
public class Publisher {

    static String username;

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java -jar publisher.jar <username> <broker_ip> <broker_port>");
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

        out.println("PUB"); // Identify as a publisher

        // Thread to handle incoming responses from the broker
        new Thread(() -> {
            try {
                String response;
                while ((response = brokerReader.readLine()) != null) {
                    System.out.println("[Response from Broker]: " + response);
                    if (response.equals("close")) {
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
            System.out.println("Please select a command: create, publish, show, delete.");
            String command = reader.readLine();
            String[] parts = command.split(" ");

            switch (parts[0].toLowerCase()) {
                case "create":
                    createTopic(parts, out);
                    break;
                case "publish":
                    publishMessage(parts, out);
                    break;
                case "show":
                    showSubscribers(parts, out);
                    break;
                case "delete":
                    deleteTopic(parts, out);
                    break;
                default:
                    System.out.println("[ERROR] Illegal instruction.");
            }
        }
    }

    /**
     * Creates a topic on the broker.
     * @param parts Command arguments
     * @param out Output stream to send messages to the broker
     */
    private static void createTopic(String[] parts, PrintWriter out) {
        if (parts.length != 3) {
            System.out.println("[ERROR] Parameter error.");
            return;
        }

        String topicId = parts[1];
        String topicName = parts[2];
        String message = "CREATE " + topicId + " " + topicName + " " + username;

        out.println(message);
    }

    /**
     * Publishes a message to a topic.
     * @param parts Command arguments
     * @param out Output stream to send messages to the broker
     */
    private static void publishMessage(String[] parts, PrintWriter out) {
        if (parts.length < 3) {
            System.out.println("[ERROR] Parameter error.");
            return;
        }

        String topicId = parts[1];
        String content = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));

        if (content.length() > 100) {
            System.out.println("[ERROR] No more than 100 characters.");
            return;
        }

        String message = "PUBLISH " + topicId + " " + content + " " + username;

        out.println(message);
    }

    /**
     * Requests a list of subscribers from the broker.
     * @param parts Command arguments
     * @param out Output stream to send messages to the broker
     */
    private static void showSubscribers(String[] parts, PrintWriter out) {
        String message = "SHOW " + username;
        out.println(message);
    }

    /**
     * Deletes a topic on the broker.
     * @param parts Command arguments
     * @param out Output stream to send messages to the broker
     */
    private static void deleteTopic(String[] parts, PrintWriter out) {
        if (parts.length != 2) {
            System.out.println("[ERROR] Parameter error.");
            return;
        }

        String topicId = parts[1];
        String message = "DELETE " + topicId;

        out.println(message);
    }
}
