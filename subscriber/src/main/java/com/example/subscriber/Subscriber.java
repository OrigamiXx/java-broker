package com.example.subscriber;

import java.io.*;
import java.net.*;
import java.util.UUID;

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


        Socket socket = new Socket(brokerIp, brokerPort);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        BufferedReader brokerReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));


        out.println("SUB");


        new Thread(() -> {
            try {
                String message;
                while ((message = brokerReader.readLine()) != null) {
                    System.out.println("[Response from Broker]: " + message);
                    if (message.equals("close")){
                        socket.close();
                    }
                }
            } catch (IOException e) {
                if (e.getMessage().equals("socket closed")){

                    System.exit(0);
                }
            }
        }).start();


        System.out.println("Connected to broker as " + username);
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

    private static void displayTopics(String[] parts, PrintWriter out) {
        if (parts.length != 1 || !parts[0].equals("display")) {
            System.out.println("[ERROR] Show parameter error.");
            return;
        }
        String message = "DISPLAY";

        out.println(message);
    }

    private static void subscribe(String[] parts, PrintWriter out) {
        if (parts.length != 2) {
            System.out.println("[ERROR] Parameter error.");
            return;
        }

        String topicId = parts[1];

        String message = "SUBSCRIBE " + topicId + " " + username;


        out.println(message);
    }

    private static void currentSubscriptions(PrintWriter out) {
        String message = "CURRENT "+username;

        out.println(message);
    }

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
