package com.dist.simplekafka;

import java.util.*;
import java.util.stream.Collectors;

public class ReplicaAssigner {
    final Random random;

    public ReplicaAssigner(Random random) {
        this.random = random;
    }

    public ReplicaAssigner() {
        this(new Random());
    }

    /**
     * Assigns replicas to brokers ensuring:
     * 1. Each partition has exactly replicationFactor replicas
     * 2. Replicas for a partition are on different brokers
     * 3. Replicas are evenly distributed across all brokers
     *
     * Strategy:
     * - Use a "starting point" approach where each partition starts assigning
     *   from a different position in the broker list
     * - This naturally achieves even distribution while keeping replicas separate
     */

    public Set<PartitionReplicas> assignReplicasToBrokers(
            List<Integer> brokerIds,
            int nPartitions,
            int replicationFactor) {

        validateBrokerList(brokerIds, replicationFactor);

        Set<PartitionReplicas> partitionAssignments = new HashSet<>();
        List<Integer> shuffledBrokers = new ArrayList<>(brokerIds);
        Collections.shuffle(shuffledBrokers, random);

        for (int partitionId = 0; partitionId < nPartitions; partitionId++) {
            List<Integer> replicas = computeReplicaSet(partitionId, replicationFactor, shuffledBrokers);
            partitionAssignments.add(new PartitionReplicas(partitionId, replicas));
            maybeRotateBrokerList(partitionId, shuffledBrokers);
        }

        return partitionAssignments.stream()
                .sorted(Comparator.comparingInt(PartitionReplicas::getPartitionId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateBrokerList(List<Integer> brokerIds, int replicationFactor) {
        if (brokerIds.size() < replicationFactor) {
            throw new IllegalArgumentException(
                    "Not enough brokers (" + brokerIds.size() + ") for replication factor " + replicationFactor);
        }
    }

    private List<Integer> computeReplicaSet(int partitionId, int replicationFactor, List<Integer> brokerIds) {
        List<Integer> replicaBrokers = new ArrayList<>(replicationFactor);
        int startingPoint = partitionId % brokerIds.size();

        for (int i = 0; i < replicationFactor; i++) {
            int index = (startingPoint + i) % brokerIds.size();
            replicaBrokers.add(brokerIds.get(index));
        }

        return replicaBrokers;
    }

    private void maybeRotateBrokerList(int partitionId, List<Integer> brokerIds) {
        if (partitionId % brokerIds.size() == 0) {
            Collections.rotate(brokerIds, random.nextInt(brokerIds.size()));
        }
    }

}
