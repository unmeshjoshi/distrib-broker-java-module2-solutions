package com.dist.simplekafka;

import com.dist.common.TestUtils;
import com.dist.common.ZookeeperTestHarness;
import org.junit.Test;

import java.util.*;

import static junit.framework.Assert.assertEquals;


public class AdminZkClientTest extends ZookeeperTestHarness {

    @Test
    public void shouldCreatePersistentPathForTopicWithTopicPartitionAssignmentsInZookeeper() throws Exception {

        zookeeperClient.registerBroker(new Broker(0, "10.10.10.10", 8000));
        zookeeperClient.registerBroker(new Broker(1, "10.10.10.11", 8001));
        zookeeperClient.registerBroker(new Broker(2, "10.10.10.12", 8002));


        var allTopics = new HashMap<>();
        zookeeperClient.subscribeTopicChangeListener((topic, topics) -> {
            allTopics.putAll(zookeeperClient.getAllTopics());
        });

        AdminZkClient createCommandTest =
                new AdminZkClient(zookeeperClient,
                        new ReplicaAssigner(new Random(100)));
        createCommandTest.createTopic("topic1", 2, 3);

        TestUtils.waitUntilTrue(() -> {
            return allTopics.containsKey("topic1");
        }, "Waiting for topic creation to be detected");

    }
}