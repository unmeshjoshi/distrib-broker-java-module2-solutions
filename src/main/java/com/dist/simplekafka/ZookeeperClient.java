package com.dist.simplekafka;

import com.dist.common.Config;
import com.dist.common.JsonSerDes;
import com.dist.common.ZKStringSerializer;
import com.fasterxml.jackson.core.type.TypeReference;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkNoNodeException;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.log4j.Logger;
import org.apache.zookeeper.Watcher;

import java.util.*;


public class ZookeeperClient {
    private static final Logger logger = Logger.getLogger(ZookeeperClient.class);

    public static final String BrokerIdsPath = "/brokers/ids";
    public static final String BrokerTopicsPath = "/brokers/topics";
    public static final String ControllerPath = "/controller";

    private final ZkClient zkClient;
    private final Config config;

    public ZookeeperClient(Config config) {
        this.config = config;
        zkClient = new ZkClient(config.getZkConnect(), config.getZkSessionTimeoutMs(), config.getZkConnectionTimeoutMs(), new ZKStringSerializer());
        zkClient.subscribeStateChanges(new SessionExpireListener());
    }

    public void registerSelf() {
        Broker broker = new Broker(config.getBrokerId(), config.getHostName(), config.getPort());
        registerBroker(broker);
    }


    public void registerBroker(Broker broker) {
        String brokerValue = JsonSerDes.toJson(broker);
        String brokerKey = getBrokerPath(broker.id());
        //TODO: Create Ephemeral Path with this data.
        createEphemeralPath(zkClient, brokerKey, brokerValue);
    }

    public Set<Broker> getAllBrokers() {
        Set<String> brokerIds = new HashSet<>(zkClient.getChildren(BrokerIdsPath));
        Set<Broker> brokers = new HashSet<>();
        for (String idString : brokerIds) {
            int id = Integer.parseInt(idString);
            String data = zkClient.readData(getBrokerPath(id));
            brokers.add(JsonSerDes.fromJson(data.getBytes(), Broker.class));
        }
        return brokers;
    }

    public Broker getBrokerInfo(int brokerId) {
        String data = zkClient.readData(getBrokerPath(brokerId));
        return JsonSerDes.fromJson(data.getBytes(), Broker.class);
    }


    public Optional<List<String>> subscribeBrokerChangeListener(IZkChildListener listener) {
        List<String> result = zkClient.subscribeChildChanges(BrokerIdsPath, listener);
        return Optional.ofNullable(result);
    }


    public Optional<List<String>> subscribeTopicChangeListener(IZkChildListener listener) {
        List<String> result = zkClient.subscribeChildChanges(BrokerTopicsPath, listener);
        return Optional.ofNullable(result);
    }

    private void createEphemeralPath(ZkClient client, String path, String data) {
        try {
            client.createEphemeral(path, data);
        } catch (ZkNoNodeException e) {
            createParentPath(client, path);
            client.createEphemeral(path, data);

        }
    }

    private String getBrokerPath(int id) {
        return BrokerIdsPath + "/" + id;
    }

    private void createParentPath(ZkClient client, String path) {
        String parentDir = path.substring(0, path.lastIndexOf('/'));
        if (!parentDir.isEmpty()) {
            client.createPersistent(parentDir, true);
        }
    }


    public Set<Integer> getAllBrokerIds() {
        Set<String> brokerIds = new HashSet<>(zkClient.getChildren(BrokerIdsPath));
        Set<Integer> integerBrokerIds = new HashSet<>();
        for (String id : brokerIds) {
            integerBrokerIds.add(Integer.parseInt(id));
        }
        return integerBrokerIds;
    }

    public void setPartitionReplicasForTopic(String topicName,
                                             List<PartitionReplicas> partitionReplicas) {
        String topicsPath = getTopicPath(topicName);
        String topicsData = JsonSerDes.toJson(partitionReplicas);
        //Assignment== Permanently store topic metadata in zookeeper.
        createPersistentPath(zkClient, topicsPath, topicsData);
    }

    private void createPersistentPath(ZkClient client, String path,
                                      String data) {
        try {
            client.createPersistent(path, data);
        } catch (ZkNoNodeException e) {
            createParentPath(client, path);
            client.createPersistent(path, data);

        }
    }


    private String getTopicPath(String topicName) {
        return BrokerTopicsPath + "/" + topicName;
    }


    public Map<String, List<PartitionReplicas>> getAllTopics() throws Exception {
        List<String> topics = zkClient.getChildren(BrokerTopicsPath); // Assuming zkClient is available
        Map<String, List<PartitionReplicas>> topicPartitionMap = new HashMap<>();
        for (String topicName : topics) {
            String partitionAssignments = zkClient.readData(getTopicPath(topicName));
            List<PartitionReplicas> partitionReplicas = JsonSerDes.deserialize(partitionAssignments.getBytes(), new TypeReference<List<PartitionReplicas>>() {
            });
            topicPartitionMap.put(topicName, partitionReplicas);
        }
        return topicPartitionMap;
    }


    public List<PartitionReplicas> getPartitionAssignmentsFor(String topicName) {
        String partitionAssignmentsData = zkClient.readData(getTopicPath(topicName));
        return JsonSerDes.deserialize(partitionAssignmentsData.getBytes(),
                new TypeReference<>() {
                });
    }


    public void tryCreatingControllerPath(int brokerId) throws ControllerExistsException {
        try {

            //Assignment: Create controller path in Zookeeper..
            //Important to create an ephemeralPath which disasspears if the
            // node fails.
            createEphemeralPath(zkClient, ControllerPath,
                    String.valueOf(brokerId));

        } catch (ZkNodeExistsException e) {
            String existingControllerId = zkClient.readData(ControllerPath);
            throw new ControllerExistsException(Integer.parseInt(existingControllerId));
        }
    }

    public void subscribeControllerChangeListener(IZkDataListener listener) {
        zkClient.subscribeDataChanges(ControllerPath, listener);
    }


    class SessionExpireListener implements IZkStateListener {

        @Override
        public void handleStateChanged(Watcher.Event.KeeperState state) throws Exception {
            // do nothing, since zkclient will do reconnect for us.
        }

        @Override
        public void handleNewSession() throws Exception {
            logger.info("re-registering broker info in ZK for broker " + config.getBrokerId());
            registerSelf();
            logger.info("done re-registering broker");
        }

        @Override
        public void handleSessionEstablishmentError(Throwable error) {
            logger.debug(error.getMessage());
        }
    }
}