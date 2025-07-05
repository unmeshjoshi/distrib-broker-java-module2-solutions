package com.dist.simplekafka;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PartitionReplicas {
    private final int partitionId;
    private final List<Integer> brokerIds;

    PartitionReplicas(int partitionId, List<Integer> brokerIds) {
        this.partitionId = partitionId;
        this.brokerIds = brokerIds;
    }

    private PartitionReplicas() {
        this(0, Collections.EMPTY_LIST); //for jackson
    } // Default constructorpublic int partitionId(){ return partitionId; }

    public List<Integer> brokerIds() {
        return brokerIds;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PartitionReplicas) obj;
        return this.partitionId == that.partitionId &&
                Objects.equals(this.brokerIds, that.brokerIds);
    }

    public int getPartitionId() {
        return partitionId;
    }

    public List<Integer> getBrokerIds() {
        return brokerIds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitionId, brokerIds);
    }

    @Override
    public String toString() {
        return "PartitionReplicas[" +
                "partitionId=" + partitionId + ", " +
                "brokerIds=" + brokerIds + ']';
    }


}
