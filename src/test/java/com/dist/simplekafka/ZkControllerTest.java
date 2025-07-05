package com.dist.simplekafka;

import com.dist.common.TestUtils;
import com.dist.common.ZookeeperTestHarness;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class ZkControllerTest extends ZookeeperTestHarness {
    @Test
    public void singleNodeBecomesController() {
        var broker = new Broker(config.getBrokerId(), config.getHostName(), config.getPort());
        zookeeperClient.registerBroker(broker); //this will happen together
        // with controller initialization on each node.

        ZkController zkController = new ZkController(zookeeperClient,
                config.getBrokerId());

        zkController.elect();

        assertEquals(config.getBrokerId(), zkController.getCurrentLeaderId());
    }

    /**
     * Tests the distributed controller election process with multiple brokers.
     * This test verifies that:
     * 1. When multiple brokers attempt to become controller simultaneously,
     *    only one succeeds (demonstrating ZooKeeper's atomic operations)
     * 2. All brokers agree on who the controller is (demonstrating consensus)
     * 3. The lowest broker ID (1) becomes the controller (implementation detail
     *    of how the election works in this example)
     * 
     * Real-world implications:
     * - In a distributed system, we must ensure only one controller exists
     * - All nodes must have the same view of who the controller is
     * - This test shows how ZooKeeper prevents "split-brain" scenarios where
     *   multiple nodes think they're the controller
     */
    @Test
    public void forMultipleNodesOnlyOneBecomesController() {
        // Register a broker in ZooKeeper (simulating a node joining the cluster)
        var broker = new Broker(config.getBrokerId(), config.getHostName(), config.getPort());
        zookeeperClient.registerBroker(broker);

        // Create three controller instances representing different brokers
        ZkController zkController1 = new ZkController(zookeeperClient, 1);
        ZkController zkController2 = new ZkController(zookeeperClient, 2);
        ZkController zkController3 = new ZkController(zookeeperClient, 3);

        // Simulate concurrent election attempts
        // In a real cluster, these would happen on different nodes
        zkController1.elect();
        zkController2.elect();
        zkController3.elect();

        // Verify that all nodes agree on the same controller (broker 1)
        // This demonstrates that ZooKeeper successfully maintained consistency
        assertEquals(1, zkController1.getCurrentLeaderId());
        assertEquals(1, zkController2.getCurrentLeaderId());
        assertEquals(1, zkController3.getCurrentLeaderId());
    }

    @Test
    public void controllerSubscribesToTopicMetadataChangesInZookeeper() {
        zookeeperClient.registerBroker(new Broker(0, "10.10.10.10", 8000));
        zookeeperClient.registerBroker(new Broker(1, "10.10.10.11", 8001));
        zookeeperClient.registerBroker(new Broker(2, "10.10.10.12", 8002));

        ZkController zkController = new ZkController(zookeeperClient,
                config.getBrokerId());

        zkController.elect();

        AdminZkClient createCommandTest =
                new AdminZkClient(zookeeperClient,
                        new ReplicaAssigner(new Random(100)));
        createCommandTest.createTopic("topic1", 2, 3);

        TestUtils.waitUntilTrue(()->{
                    return zkController.getAllTopics().size() == 1;
                },
                "Controller should get topic details in a callback");

    }

}