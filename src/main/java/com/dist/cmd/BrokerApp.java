package com.dist.cmd;

import com.dist.simplekafka.*;
import com.dist.common.Config;
import org.I0Itec.zkclient.IZkChildListener;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.util.*;

public class BrokerApp {
    private static final Logger logger = Logger.getLogger(BrokerApp.class);
    
    private ZookeeperClient zookeeperClient;
    private ZkController controller;
    private int brokerId;
    private String zkAddress;
    
    public static void main(String[] args) {
        BrokerApp app = new BrokerApp();
        app.run(args);
    }
    
    public void run(String[] args) {
        validateArguments(args);
        
        zkAddress = args[0];
        brokerId = Integer.parseInt(args[1]);
        
        displayStartupInfo(zkAddress, brokerId);
        
        try {
            setupZookeeperClient();
            setupController();
            registerBrokerWithZookeeper();
            displayCurrentClusterState();
            keepBrokerRunning();
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    private void validateArguments(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java BrokerApp <zookeeper-address> <broker-id>");
            System.out.println("Example: java BrokerApp localhost:2181 1");
            System.out.println("\nAvailable commands:");
            System.out.println("  createTopic <topic-name> <partitions> <replication-factor>");
            System.out.println("  listTopics");
            System.out.println("  listBrokers");
            System.exit(1);
        }
    }
    
    private void displayStartupInfo(String zkAddress, int brokerId) {
        System.out.println("=== Enhanced Broker Demo Application ===");
        System.out.println("ZooKeeper Address: " + zkAddress);
        System.out.println("Broker ID: " + brokerId);
        System.out.println("Features: Controller Election, Topic Management");
        System.out.println("=============================================");
    }
    
    private void setupZookeeperClient() throws Exception {
        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        int brokerPort = 9092 + brokerId;
        
        Config config = new Config(brokerId, hostAddress, brokerPort, zkAddress, Arrays.asList("/tmp/broker-" + brokerId));
        zookeeperClient = new ZookeeperClient(config);
        zookeeperClient.registerSelf();
    }
    
    private void setupController() {
        controller = new ZkController(zookeeperClient, brokerId);
        controller.startup();
        
        if (controller.getCurrentLeaderId() == brokerId) {
            System.out.println("🎯 This broker is now the CONTROLLER!");
        } else {
            System.out.println("📡 This broker is a FOLLOWER. Controller ID: " + controller.getCurrentLeaderId());
        }
    }
    
    private void registerBrokerWithZookeeper() throws InterruptedException {
        System.out.println("Registering broker " + brokerId + " with ZooKeeper...");
        System.out.println("✓ Broker " + brokerId + " registered successfully!");
        
        // Wait for registration to be processed
        Thread.sleep(1000);
    }
    
    private void displayCurrentClusterState() {
        System.out.println("\n=== CURRENT CLUSTER STATE ===");
        Set<Broker> allBrokers = zookeeperClient.getAllBrokers();
        System.out.println("Total brokers in cluster: " + allBrokers.size());
        
        for (Broker broker : allBrokers) {
            String role = (broker.id() == controller.getCurrentLeaderId()) ? " (CONTROLLER)" : "";
            System.out.println("  Broker " + broker.id() + role + ": " + broker);
        }
        
        // Display topics if this broker is the controller
        if (controller.getCurrentLeaderId() == brokerId) {
            Map<Object, Object> topics = controller.getAllTopics();
            System.out.println("Total topics: " + topics.size());
            for (Object topicName : topics.keySet()) {
                System.out.println("  Topic: " + topicName);
            }
        }
        System.out.println("=============================\n");
    }

    private void keepBrokerRunning() throws InterruptedException {
        displayRunningInstructions();
        
        // Set up topic change listener
        setupTopicChangeListener();
        
        while (true) {
            Thread.sleep(5000);
            System.out.println("Broker " + brokerId + " is still alive and watching...");
            
            // Periodically show controller status
            if (controller.getCurrentLeaderId() == brokerId) {
                System.out.println("🎯 Controller status: Active");
            }
        }
    }
    
    private void setupTopicChangeListener() {
        zookeeperClient.subscribeTopicChangeListener(new IZkChildListener() {
            @Override
            public void handleChildChange(String parentPath, List<String> currentChildren) throws Exception {
                System.out.println("\n=== TOPIC CHANGE DETECTED ===");
                System.out.println("Parent Path: " + parentPath);
                System.out.println("Current Topics: " + currentChildren);
                System.out.println("============================\n");
            }
        });
    }
    
    private void displayRunningInstructions() {
        System.out.println("Broker application is running...");
        System.out.println("Broker ID: " + brokerId);
        System.out.println("Controller ID: " + controller.getCurrentLeaderId());
        System.out.println("This broker will stay registered and watch for changes.");
        System.out.println("Press Ctrl+C to exit.");
        System.out.println("\nTo test topic creation (if this is the controller):");
        System.out.println("1. Open another terminal");
        System.out.println("2. Run: java TopicCommandApp " + zkAddress + " createTopic test-topic 3 2");
        System.out.println("\nTo start other brokers:");
        System.out.println("Example: java BrokerApp " + zkAddress + " 2");
    }
    
    private void handleError(Exception e) {
        System.err.println("Error: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
} 