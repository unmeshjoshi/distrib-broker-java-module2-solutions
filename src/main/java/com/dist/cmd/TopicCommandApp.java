package com.dist.cmd;

import com.dist.simplekafka.*;
import com.dist.common.Config;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.util.*;

public class TopicCommandApp {
    private static final Logger logger = Logger.getLogger(TopicCommandApp.class);
    
    public static void main(String[] args) {
        TopicCommandApp app = new TopicCommandApp();
        app.run(args);
    }
    
    public void run(String[] args) {
        validateArguments(args);
        
        String zkAddress = args[0];
        String command = args[1];
        
        try {
            setupZookeeperClient(zkAddress);
            
            switch (command.toLowerCase()) {
                case "createtopic":
                    handleCreateTopic(args);
                    break;
                case "listtopics":
                    handleListTopics();
                    break;
                case "listbrokers":
                    handleListBrokers();
                    break;
                default:
                    System.out.println("Unknown command: " + command);
                    displayUsage();
            }
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    private void validateArguments(String[] args) {
        if (args.length < 2) {
            displayUsage();
            System.exit(1);
        }
    }
    
    private void displayUsage() {
        System.out.println("Usage: java TopicCommandApp <zookeeper-address> <command> [args...]");
        System.out.println("\nAvailable commands:");
        System.out.println("  createTopic <topic-name> <partitions> <replication-factor>");
        System.out.println("  listTopics");
        System.out.println("  listBrokers");
        System.out.println("\nExamples:");
        System.out.println("  java TopicCommandApp localhost:2181 createTopic test-topic 3 2");
        System.out.println("  java TopicCommandApp localhost:2181 listTopics");
        System.out.println("  java TopicCommandApp localhost:2181 listBrokers");
    }
    
    private ZookeeperClient zookeeperClient;
    
    private void setupZookeeperClient(String zkAddress) throws Exception {
        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        Config config = new Config(999, hostAddress, 9999, zkAddress, Arrays.asList("/tmp/topic-command"));
        zookeeperClient = new ZookeeperClient(config);
    }
    
    private void handleCreateTopic(String[] args) throws Exception {
        if (args.length != 5) {
            System.out.println("Error: createTopic requires topic-name, partitions, and replication-factor");
            System.out.println("Usage: createTopic <topic-name> <partitions> <replication-factor>");
            return;
        }
        
        String topicName = args[2];
        int partitions = Integer.parseInt(args[3]);
        int replicationFactor = Integer.parseInt(args[4]);
        
        System.out.println("=== Creating Topic ===");
        System.out.println("Topic Name: " + topicName);
        System.out.println("Partitions: " + partitions);
        System.out.println("Replication Factor: " + replicationFactor);
        
        // Get available brokers
        Set<Broker> brokers = zookeeperClient.getAllBrokers();
        if (brokers.size() < replicationFactor) {
            System.out.println("Error: Not enough brokers available. Need " + replicationFactor + 
                             " but only " + brokers.size() + " are available.");
            return;
        }
        
        // Create broker list for replica assignment
        List<Integer> brokerIds = new ArrayList<>();
        for (Broker broker : brokers) {
            brokerIds.add(broker.id());
        }
        
        // Assign replicas to partitions
        ReplicaAssigner assigner = new ReplicaAssigner(new Random(42));
        Set<PartitionReplicas> partitionReplicas = assigner.assignReplicasToBrokers(brokerIds, partitions, replicationFactor);
        
        // Convert to list for storage
        List<PartitionReplicas> partitionReplicasList = new ArrayList<>(partitionReplicas);
        
        // Store in ZooKeeper
        zookeeperClient.setPartitionReplicasForTopic(topicName, partitionReplicasList);
        
        System.out.println("✓ Topic '" + topicName + "' created successfully!");
        System.out.println("Partition assignments:");
        for (PartitionReplicas pr : partitionReplicasList) {
            System.out.println("  Partition " + pr.getPartitionId() + ": " + pr.getBrokerIds());
        }
        System.out.println("=====================");
    }
    
    private void handleListTopics() throws Exception {
        System.out.println("=== Available Topics ===");
        Map<String, List<PartitionReplicas>> topics = zookeeperClient.getAllTopics();
        
        if (topics.isEmpty()) {
            System.out.println("No topics found.");
        } else {
            for (Map.Entry<String, List<PartitionReplicas>> entry : topics.entrySet()) {
                System.out.println("Topic: " + entry.getKey());
                List<PartitionReplicas> partitions = entry.getValue();
                System.out.println("  Partitions: " + partitions.size());
                for (PartitionReplicas pr : partitions) {
                    System.out.println("    Partition " + pr.getPartitionId() + ": " + pr.getBrokerIds());
                }
                System.out.println();
            }
        }
        System.out.println("=======================");
    }
    
    private void handleListBrokers() {
        System.out.println("=== Available Brokers ===");
        Set<Broker> brokers = zookeeperClient.getAllBrokers();
        
        if (brokers.isEmpty()) {
            System.out.println("No brokers found.");
        } else {
            for (Broker broker : brokers) {
                System.out.println("Broker " + broker.id() + ": " + broker.host() + ":" + broker.port());
            }
        }
        System.out.println("=========================");
    }
    
    private void handleError(Exception e) {
        System.err.println("Error: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
} 