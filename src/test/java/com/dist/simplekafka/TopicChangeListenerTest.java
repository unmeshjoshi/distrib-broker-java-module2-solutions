package com.dist.simplekafka;

import com.dist.common.TestUtils;
import com.dist.common.ZookeeperTestHarness;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TopicChangeListenerTest extends ZookeeperTestHarness {

    @Test
    public void detectNewTopicCreation() {
        var allTopics = new HashMap<>();
        // Setup: Register 3 brokers in ZooKeeper with their network details
        // This metadata will be loaded by controller during election
        zookeeperClient.registerBroker(new Broker(0, "10.10.10.10", 8000));
        zookeeperClient.registerBroker(new Broker(1, "10.10.10.11", 8001));
        zookeeperClient.registerBroker(new Broker(2, "10.10.10.12", 8002));

        //TODO: Implement subscriber for the topicChanges to populate allTopics list.
        zookeeperClient.subscribeTopicChangeListener((topic, topics) -> {
            allTopics.putAll(zookeeperClient.getAllTopics());
        });
        // Create a new topic using admin utility
        // This will:
        // 1. Create topic metadata in ZK
        // 2. Trigger TopicChangeHandler
        // 3. Controller uses broker metadata to send requests
        AdminZkClient adminUtil = new AdminZkClient(zookeeperClient, new ReplicaAssigner());
        adminUtil.createTopic("topic1", 2, 3);

        // Verify: Wait until all expected messages are sent to correct broker addresses
        // This only works because controller loaded broker metadata during elect()
        TestUtils.waitUntilTrue(() -> allTopics.size() == 1, "Waiting for topic data to be received");

        // At this point, controller has successfully:
        // 1. Detected the new topic via TopicChangeHandler
    }
}