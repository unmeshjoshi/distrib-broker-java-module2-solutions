package com.dist.simplekafka;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZkController implements IZkChildListener, IZkDataListener {
    private final ZookeeperClient zookeeperClient;
    private final int brokerId;
    private int currentLeader = -1;
    private List<Broker> liveBrokers = new ArrayList<>();

    public ZkController(ZookeeperClient zookeeperClient, int brokerId) {
        this.zookeeperClient = zookeeperClient;
        this.brokerId = brokerId;
    }

    /**
     * public static void main() {
     *     registerBroker();
     *     ZkController controller = new ZkController();
     *     controller.startup();
     * }
     */

    public void startup() {
        System.out.println("🔄 Starting controller election process for broker " + brokerId + "...");
        elect();
        zookeeperClient.subscribeControllerChangeListener(this);
        System.out.println("✅ Controller startup completed for broker " + brokerId);
    }


    public void shutdown() {
        // Implementation not provided in the original code
    }

     /**
     * Attempts to elect this broker as the controller in the Kafka cluster.
     * This method leverages ZooKeeper's strong consistency guarantees to ensure
     * only one controller exists at any time.
     * 
     * ZooKeeper's importance in controller election:
     * 1. Atomic Operations: ZooKeeper ensures the controller path creation is atomic
     *    (only one broker can succeed, even if multiple try simultaneously)
     * 2. Distributed Consensus: Using ZooKeeper's ZAB (ZooKeeper Atomic Broadcast) protocol,
     *    all servers in the ZooKeeper ensemble agree on the state of the controller
     * 3. High Availability: If the controller fails, ZooKeeper's watch mechanisms can
     *    notify other brokers to trigger a new election
     * 
     * Fault Tolerance in ZooKeeper:
     * - Quorum-based Updates: Changes are committed only when majority of ZooKeeper
     *   servers (2f+1 out of 2f+1 servers, where f is number of allowed failures) acknowledge
     * - Write Ahead Logging: Every state change is persisted to disk before acknowledging
     * - Failure Recovery: Even if some ZooKeeper servers fail, as long as a majority
     *   remains available, the controller election state is preserved
     * - Leader Election: ZooKeeper itself uses the ZAB protocol to elect its own leader,
     *   ensuring service continues even when some servers fail
     * 
     * Without a consensus system like ZooKeeper, distributed systems could end up in a
     * "split-brain" scenario where multiple nodes believe they are the controller,
     * leading to cluster inconsistencies.
     */
    public void elect() {
        try {
            System.out.println("🎯 Broker " + brokerId + " attempting to become controller...");
            
            // Attempt to create an ephemeral node in ZooKeeper to become the controller
            // This is an atomic operation - only one broker can succeed
            zookeeperClient.tryCreatingControllerPath(brokerId);

            // If we get here, we successfully became the controller
            this.currentLeader = brokerId;
            System.out.println("🎉 SUCCESS: Broker " + brokerId + " is now the CONTROLLER!");

            // Initialize controller state by loading broker information
            onBecomingController();

        } catch (ControllerExistsException e) {
            // Another broker is already the controller
            // Update our local state to recognize the existing controller
            this.currentLeader = e.getControllerId();
            System.out.println("📡 Broker " + brokerId + " is a FOLLOWER. Controller ID: " + e.getControllerId());
        }
    }

    private void onBecomingController() {
        System.out.println("🔧 Initializing controller state for broker " + brokerId + "...");
        
        // Get current broker list
        List<Broker> currentBrokers = new ArrayList<>(zookeeperClient.getAllBrokers());
        liveBrokers.clear();
        liveBrokers.addAll(currentBrokers);
        
        System.out.println("📊 Current cluster state: " + liveBrokers.size() + " brokers");
        for (Broker broker : liveBrokers) {
            System.out.println("   - Broker " + broker.id() + ": " + broker.host() + ":" + broker.port());
        }
        
        // Subscribe for broker changes
        zookeeperClient.subscribeBrokerChangeListener((parentPath, currentChilds) -> {
            handleBrokerMembershipChange(parentPath, currentChilds);
        });
        
        // Subscribe for topic changes
        zookeeperClient.subscribeTopicChangeListener((parentPath, currentChilds) -> {
            handleTopicChange(parentPath, currentChilds);
        });
        
        System.out.println("✅ Controller initialization completed for broker " + brokerId);
    }
    
    private void handleBrokerMembershipChange(String parentPath, List<String> currentChilds) {
        System.out.println("\n🔄 BROKER MEMBERSHIP CHANGE DETECTED!");
        System.out.println("   Path: " + parentPath);
        System.out.println("   Current brokers: " + currentChilds);
        
        // Get updated broker list
        List<Broker> newBrokerList = new ArrayList<>(zookeeperClient.getAllBrokers());
        
        // Find added brokers
        List<Broker> addedBrokers = new ArrayList<>();
        for (Broker newBroker : newBrokerList) {
            boolean found = false;
            for (Broker oldBroker : liveBrokers) {
                if (oldBroker.id() == newBroker.id()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                addedBrokers.add(newBroker);
            }
        }
        
        // Find removed brokers
        List<Broker> removedBrokers = new ArrayList<>();
        for (Broker oldBroker : liveBrokers) {
            boolean found = false;
            for (Broker newBroker : newBrokerList) {
                if (oldBroker.id() == newBroker.id()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                removedBrokers.add(oldBroker);
            }
        }
        
        // Report changes
        if (!addedBrokers.isEmpty()) {
            System.out.println("➕ BROKERS ADDED:");
            for (Broker broker : addedBrokers) {
                System.out.println("   + Broker " + broker.id() + ": " + broker.host() + ":" + broker.port());
            }
        }
        
        if (!removedBrokers.isEmpty()) {
            System.out.println("➖ BROKERS REMOVED:");
            for (Broker broker : removedBrokers) {
                System.out.println("   - Broker " + broker.id() + ": " + broker.host() + ":" + broker.port());
            }
        }
        
        // Update local state
        liveBrokers.clear();
        liveBrokers.addAll(newBrokerList);
        
        System.out.println("📊 Updated cluster state: " + liveBrokers.size() + " brokers total");
        System.out.println("========================================\n");
    }
    
    private void handleTopicChange(String parentPath, List<String> currentChilds) {
        System.out.println("\n📝 TOPIC CHANGE DETECTED!");
        System.out.println("   Path: " + parentPath);
        System.out.println("   Current topics: " + currentChilds);
        
        try {
            allTopics.putAll(zookeeperClient.getAllTopics());
            System.out.println("✅ Topic state updated successfully");
        } catch (Exception e) {
            System.out.println("❌ Error updating topic state: " + e.getMessage());
        }
        
        System.out.println("========================================\n");
    }


    public int getCurrentLeaderId() {
        return currentLeader;
    }

    @Override
    public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
        // This method is called when broker membership changes
        // The actual handling is done in handleBrokerMembershipChange
        System.out.println("🔍 Child change detected on path: " + parentPath);
    }

    @Override
    public void handleDataChange(String dataPath, Object data) throws Exception {
        System.out.println("\n🔄 CONTROLLER DATA CHANGE DETECTED!");
        System.out.println("   Path: " + dataPath);
        System.out.println("   New controller data: " + data);
        System.out.println("   Current broker ID: " + brokerId);
        
        // Trigger re-election
        elect();
        
        System.out.println("========================================\n");
    }

    @Override
    public void handleDataDeleted(String dataPath) throws Exception {
        System.out.println("\n💥 CONTROLLER FAILURE DETECTED!");
        System.out.println("   Path: " + dataPath);
        System.out.println("   Current broker ID: " + brokerId);
        System.out.println("   Previous controller ID: " + currentLeader);
        
        // The controller node was deleted, trigger re-election
        System.out.println("🔄 Initiating controller re-election...");
        elect();
        
        System.out.println("========================================\n");
    }

    Map allTopics = new HashMap();
    public Map<Object, Object> getAllTopics() {
        return allTopics;
    }
}
