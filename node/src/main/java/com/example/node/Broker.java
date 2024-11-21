package com.example.node;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Broker {

    private static final int MAX_PUB = 5;
    private static final int MAX_SUB = 10;


    private static int publisherCount = 0;
    private static int subscriberCount = 0;

    private static final Map<String, Topic> topics = new HashMap<>();

    private static final Map<String, List<Socket>> socketClients = new HashMap<>();
    private static final List<Socket> brokerConnections = new ArrayList<>();
    private static final List<String> failedBrokers = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Broker started on port: " + port);

        // Connect to other Brokers
        String brokersArg = "";

        // Parse command line arguments
        for (int i = 1; i < args.length; i++) {
            if ("-b".equals(args[i])) {

                StringBuilder brokers = new StringBuilder();
                for (int j = i + 1; j < args.length; j++) {
                    brokers.append(args[j]).append(" ");
                }
                brokersArg = brokers.toString().trim();
                break;
            }
        }

        if (!brokersArg.isEmpty()) {

            connectToOtherBrokers(brokersArg);
            new Thread(new BrokerConnectionListener(args[2])).start();
        }


        // Accept connections from clients and other Brokers
        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> {
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);


                    String type = in.readLine();
                    if ("BROKER".equalsIgnoreCase(type)) {

                        brokerConnections.add(socket);
                        new Thread(new BrokerHandler(socket)).start();
                        System.out.println("Accepted connection from another broker.");
                    } else if ("SUB".equalsIgnoreCase(type) || "PUB".equalsIgnoreCase(type)) {

//                        clientConnections.add(socket);
                        new Thread(new ClientHandler(socket)).start();
                        System.out.println("Accepted connection from a client.");


                        if ("SUB".equalsIgnoreCase(type)) {
                            subscriberCount++;
                            if (subscriberCount > MAX_SUB) {
                                //socket.close();
                                sendResponse(socket, "close");
                                subscriberCount--;
                            }

                        } else {
                            publisherCount++;
                            if (publisherCount > MAX_PUB) {
                                //socket.close();
                                sendResponse(socket, "close");
                                publisherCount--;
                            }

                        }

                    }
                } catch (IOException e) {
                    //e.printStackTrace();
                    System.out.println(e.getMessage());
                }
            }).start();
        }

    }

    // Connect to other Brokers
    private static void connectToOtherBrokers(String brokersArg) {
        String[] brokers = brokersArg.split(" ");
        for (String broker : brokers) {
            String[] brokerInfo = broker.split(":");
            String brokerIp = brokerInfo[0];
            int brokerPort = Integer.parseInt(brokerInfo[1]);
            try {
                Socket brokerSocket = new Socket(brokerIp, brokerPort);
                PrintWriter out = new PrintWriter(brokerSocket.getOutputStream(), true);
                out.println("BROKER");
                brokerConnections.add(brokerSocket);
                new Thread(new BrokerHandler(brokerSocket)).start();
                System.out.println("Connected to broker: " + brokerIp + ":" + brokerPort);
            } catch (IOException e) {
                System.out.println("Failed to connect to broker: " + brokerIp + ":" + brokerPort);
                if (!failedBrokers.contains(broker)) {
                    failedBrokers.add(broker);
                }

            }
        }
    }

    // Used to process messages from other Brokers
    private static class BrokerHandler implements Runnable {
        private final Socket brokerSocket;

        public BrokerHandler(Socket brokerSocket) {
            this.brokerSocket = brokerSocket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()))) {
                String message;
                while ((message = in.readLine()) != null) {
                    handleBrokerMessage(message);
                }
            } catch (IOException e) {
                //e.printStackTrace();
                System.out.println(e.getMessage());
            }
        }


        // Process messages from other Brokers
        private static void handleBrokerMessage(String message) {
            String[] parts = message.split(" ");
            String command = parts[0];


            switch (command) {
                case "CREATE":
                    createTopic(parts);
                    break;
                case "PUBLISH":
                    publishMessage(parts);
                    break;
                case "SUBSCRIBE":
                    subscribe(parts);
                    break;
                case "DELETE":
                    deleteTopic(parts);
                    break;
                case "UNSUBSCRIBE":
                    unsubscribe(parts);
                default:
                    break;
            }


        }


        // Create a topic
        private static void createTopic(String[] parts) {
            if (parts.length != 4) {
                return;
            }

            String topicId = parts[1];
            String topicName = parts[2];
            String publisher = parts[3];

            if (!topics.containsKey(topicId)) {
                topics.put(topicId, new Topic(topicId, topicName, publisher));
            }
        }

        // Publish a message
        private static void publishMessage(String[] parts) {
            if (parts.length < 4) {
                return;
            }

            String topicId = parts[1];
            String message = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            Topic topic = topics.get(topicId);

            if (topic == null) {
                return;
            }

            String username = parts[3];

            if (!topic.getPublisher().equals(username)) {

                return;
            }


            String timestamp = new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date());
            String formattedMessage = String.format("[%s] [Topic ID:%s:%s] [%s]", timestamp, topicId, topic.getName(), message);
            topic.publishMessage(formattedMessage);

            if (socketClients.get(topicId) != null) {
                for (Socket subscriberSocket : socketClients.get(topicId)) {
                    try {
                        PrintWriter out = new PrintWriter(subscriberSocket.getOutputStream(), true);
                        out.println(formattedMessage);
                    } catch (IOException e) {
                        //e.printStackTrace();
                        System.out.println(e.getMessage());
                    }
                }
            }


        }

        //Subscribe to topic
        private static void subscribe(String[] parts) {
            if (parts.length != 3) {

                return;
            }

            String topicId = parts[1];
            String subscriber = parts[2];
            Topic topic = topics.get(topicId);
            if (topic == null) {

                return;
            }

            topic.addSubscriber(subscriber);

        }

        // Delete the topic
        private static void deleteTopic(String[] parts) {
            if (parts.length != 2) {

                return;
            }

            String topicId = parts[1];
            topics.remove(topicId);


            if (socketClients.get(topicId) != null) {
                socketClients.remove(topicId);
            }
        }

        // Unsubscribe
        private static void unsubscribe(String[] parts) {
            if (parts.length != 3) {
                return;
            }

            String topicId = parts[1];
            String subscriber = parts[2];
            Topic topic = topics.get(topicId);
            if (topic == null) {

                return;
            }

            topic.removeSubscriber(subscriber);

        }
    }


    // Listener class: Periodically retry the Broker that failed to connect
    private static class BrokerConnectionListener implements Runnable {
        private final String brokersArg;
        private static final int RETRY_INTERVAL = 5000;

        public BrokerConnectionListener(String brokersArg) {
            this.brokersArg = brokersArg;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(RETRY_INTERVAL);
                    retryFailedConnections();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // Retry the Broker that failed to connect
        private void retryFailedConnections() {
            Iterator<String> iterator = failedBrokers.iterator();
            while (iterator.hasNext()) {
                String broker = iterator.next();
                String[] brokerInfo = broker.split(":");
                String brokerIp = brokerInfo[0];
                int brokerPort = Integer.parseInt(brokerInfo[1]);
                try {
                    Socket brokerSocket = new Socket(brokerIp, brokerPort);
                    brokerConnections.add(brokerSocket);
                    new Thread(new BrokerHandler(brokerSocket)).start();
                    System.out.println("Reconnected to broker: " + brokerIp + ":" + brokerPort);
                    iterator.remove();
                } catch (IOException e) {
                    System.out.println("Retrying failed broker: " + brokerIp + ":" + brokerPort);
                }
            }
        }
    }


    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String message;
                while ((message = in.readLine()) != null) {
                    handleClientMessage(message, clientSocket);
                }
            } catch (IOException e) {
                //e.printStackTrace();
                System.out.println(e.getMessage());
            }
        }

        // Process messages from clients
        private void handleClientMessage(String message, Socket socket) {
            String[] parts = message.split(" ");
            String command = parts[0];

            switch (command) {
                case "CREATE":
                    createTopic(parts, socket);
                    broadcastToBrokers(message);
                    break;
                case "PUBLISH":
                    publishMessage(parts, socket);
                    broadcastToBrokers(message);
                    break;
                case "SHOW":
                    showSubscribers(parts, socket);
                    break;
                case "DELETE":
                    deleteTopic(parts, socket);
                    broadcastToBrokers(message);
                    break;
                case "SUBSCRIBE":
                    subscribe(parts, socket);
                    broadcastToBrokers(message);
                    break;
                case "DISPLAY":
                    displayTopics(socket);
                    break;
                case "CURRENT":
                    showCurrentSubscriptions(parts, socket);
                    break;
                case "UNSUBSCRIBE":
                    unsubscribe(parts, socket);
                    broadcastToBrokers(message);
                    break;
                default:
                    System.out.println("[ERROR] Illegal client instruction.");
            }
        }


        // Create a topic
        private static void createTopic(String[] parts, Socket socket) {
            if (parts.length != 4) {
                sendResponse(socket, "[ERROR] Parameters are incorrect.");
                return;
            }

            String topicId = parts[1];
            String topicName = parts[2];
            String publisher = parts[3];

            if (topics.containsKey(topicId)) {
                sendResponse(socket, "[ERROR] Topic already exists.");
            } else {
                topics.put(topicId, new Topic(topicId, topicName, publisher));
                sendResponse(socket, "[SUCCESS] Topic created: " + topicId + " - " + topicName);
            }
        }

        // Publish a message
        private static void publishMessage(String[] parts, Socket socket) {
            if (parts.length < 4) {
                sendResponse(socket, "[ERROR] Parameters are incorrect.");
                return;
            }

            String topicId = parts[1];
            String message = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
            Topic topic = topics.get(topicId);

            if (topic == null) {
                sendResponse(socket, "[ERROR] Topic not found: " + topicId);
                return;
            }
            String username = parts[3];
            if (!topic.getPublisher().equals(username)) {
                sendResponse(socket, "[ERROR] This Topic is created by others.");
                return;
            }


            String timestamp = new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date());
            String formattedMessage = String.format("[%s] [Topic ID:%s:%s] [%s]", timestamp, topicId, topic.getName(), message);
            topic.publishMessage(formattedMessage);

            if (socketClients.get(topicId) != null) {
                for (Socket subscriberSocket : socketClients.get(topicId)) {
                    try {
                        PrintWriter out = new PrintWriter(subscriberSocket.getOutputStream(), true);
                        out.println(formattedMessage);
                    } catch (IOException e) {
                        //e.printStackTrace();
                        System.out.println(e.getMessage());
                    }
                }
            }

            sendResponse(socket, "[SUCCESS] Successfully published message: " + topicId);
        }

        // Display the number of subscribers to a publisher
        private static void showSubscribers(String[] parts, Socket socket) {
            if (parts.length != 2) {
                sendResponse(socket, "[ERROR] Parameters are incorrect.");
                return;
            }

            String publisher = parts[1];
            List<Topic> publisherTopics = getTopicsByPublisher(publisher);

            for (Topic tp : publisherTopics) {
                int subscriberCount = tp.getSubscribers().size();
                sendResponse(socket, String.format("[Topic ID:%s] [Topic Name:%s] [Subscriber Count:%d]", tp.getId(), tp.getName(), subscriberCount));
            }
        }

        // Delete the topic
        private static void deleteTopic(String[] parts, Socket socket) {
            if (parts.length != 2) {
                sendResponse(socket, "[ERROR] Parameters are incorrect.");
                return;
            }

            String topicId = parts[1];
            if (topics.remove(topicId) != null) {
                sendResponse(socket, "[SUCCESS] Topic deleted successfully: " + topicId);
            } else {
                sendResponse(socket, "[ERROR] Topic not found: " + topicId);
            }


            if (socketClients.get(topicId) != null) {
                socketClients.remove(topicId);
            }
        }

        //Subscribe to topic
        private static void subscribe(String[] parts, Socket socket) {
            if (parts.length != 3) {
                sendResponse(socket, "[ERROR] Parameters are incorrect.");
                return;
            }

            String topicId = parts[1];
            String subscriber = parts[2];
            Topic topic = topics.get(topicId);
            if (topic == null) {
                sendResponse(socket, "[ERROR] Topic not found: " + topicId);
                return;
            }

            topic.addSubscriber(subscriber);
            if (socketClients.get(topicId) == null) {
                List<Socket> sockets = new ArrayList<>();
                sockets.add(socket);
                socketClients.put(topicId, sockets);
            } else {
                List<Socket> sockets = socketClients.get(topicId);
                if (!sockets.contains(socket)) {
                    sockets.add(socket);
                }
            }

            sendResponse(socket, "[SUCCESS] Successfully subscribed to the topic: " + topicId);
        }

        // Show all topics
        private static void displayTopics(Socket socket) {
            if (topics.isEmpty()) {
                sendResponse(socket, "[ERROR] Topic is empty.");
                return;
            }

            for (Topic topic : topics.values()) {
                sendResponse(socket, String.format("[Topic ID:%s] [Topic Name:%s] [Publisher:%s]", topic.getId(), topic.getName(), topic.getPublisher()));
            }
        }

        // Show current subscriptions
        private static void showCurrentSubscriptions(String[] parts, Socket socket) {


            if (parts.length != 2) {
                sendResponse(socket, "[ERROR]Parameters are incorrect.");
                return;
            }

            String subscriber = parts[1];
            List<Topic> result = getTopicsBySubscriber(subscriber);
            for (Topic topic : result) {
                sendResponse(socket, String.format("[Topic ID:%s] [Topic Name:%s] [Publisher:%s]", topic.getId(), topic.getName(), topic.getPublisher()));
            }
        }

        // Unsubscribe
        private static void unsubscribe(String[] parts, Socket socket) {
            if (parts.length != 3) {
                sendResponse(socket, "[ERROR] Parameters are incorrect.");
                return;
            }

            String topicId = parts[1];
            String subscriber = parts[2];
            Topic topic = topics.get(topicId);
            if (topic == null) {
                sendResponse(socket, "[ERROR] Topic not found: " + topicId);
                return;
            }

            topic.removeSubscriber(subscriber);

            List<Socket> sockets = socketClients.get(topicId);
            if (sockets != null) {
                sockets.remove(socket);
            }

            sendResponse(socket, "[SUCCESS] Unsubscribe Successfully: " + topicId);
        }


        // Get the topic list based on the publisher
        private static List<Topic> getTopicsByPublisher(String publisher) {
            return topics.values().stream()
                    .filter(topic -> topic.getPublisher().equals(publisher))
                    .collect(Collectors.toList());
        }


        // Get the list of topics containing current subscribers
        private static List<Topic> getTopicsBySubscriber(String subscriber) {
            List<Topic> result = new ArrayList<>();

            for (Topic topic : topics.values()) {
                if (topic.getSubscribers().contains(subscriber)) {
                    result.add(topic);
                }
            }

            return result;
        }


    }

    // Send response to the client
    private static void sendResponse(Socket socket, String message) {

        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(message);
        } catch (IOException e) {
            //e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }

    //Broadcast messages to other brokers
    private static void broadcastToBrokers(String message) {
        for (Socket brokerSocket : brokerConnections) {
            try {
                PrintWriter out = new PrintWriter(brokerSocket.getOutputStream(), true);
                out.println(message);
            } catch (IOException e) {
                System.out.println("Failed to broadcast message to broker: " + e.getMessage());
            }
        }
    }


    static class Topic {
        private final String id;
        private final String name;
        private String publisher;

        private final List<String> subscribers = new ArrayList<>();

        public Topic(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Topic(String id, String name, String publisher) {
            this.id = id;
            this.name = name;
            this.publisher = publisher;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getPublisher() {
            return publisher;
        }

        public void setPublisher(String publisher) {
            this.publisher = publisher;
        }

        public List<String> getSubscribers() {
            return subscribers;
        }

        public void publishMessage(String message) {

        }

        public void addSubscriber(String subscriber) {
            subscribers.add(subscriber);
        }

        public void removeSubscriber(String subscriber) {
            subscribers.remove(subscriber);
        }
    }
}
