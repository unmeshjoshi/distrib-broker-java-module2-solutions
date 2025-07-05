# Kafka-like Distributed System Implementation

This project implements core functionalities of a Kafka-like distributed system, focusing on topic management and controller election mechanisms.

## Implementation Tasks

### 1. Replica Assignment Testing
Enhance the `ReplicaAssignerTest` to:
- Support dynamic broker list creation:
  - Allow broker list size from 2 to 1000 brokers
  - Test with varying broker counts to ensure scalability
  - Verify assignment logic works with different cluster sizes
- Test dynamic partition count changes
- Verify replica distribution remains balanced when partition count changes
- Ensure replica assignment rules are maintained with different partition counts

Test scenarios should include:
- Small broker clusters (2-5 brokers)
- Medium broker clusters (10-50 brokers)
- Large broker clusters (100-1000 brokers)
- Edge cases:
  - Minimum broker count (2)
  - Large broker counts (1000)
  - When broker count equals partition count
  - When broker count exceeds partition count
  - When partition count exceeds broker count

Current test coverage includes:
- Correct number of partitions and replicas
- Replicas on different brokers
- Even distribution of replicas

### 2. Topic Change Detection
Complete the `detectNewTopicCreation` test in `TopicChangeListenerTest`:
- Implement test cases for new topic creation detection
- Verify callback mechanisms work correctly
- Ensure proper handling of topic metadata changes
- Test the listener's response to ZooKeeper events

### 3. Controller Election Implementation
Complete the `tryCreatingControllerPath` method to:
- Implement atomic controller election using ZooKeeper
- Handle race conditions in controller election
- Manage controller state transitions
- Ensure proper error handling


### 4. Log Implementation Testing
Complete the `LogTest` implementation:
- Implement log append and read functionality
- Test log segment creation and management
- Verify log entry persistence
- Test log compaction if implemented

Key test requirements:
- Test log entry writing and reading
- Verify log segment boundaries
- Test log file handling
- Ensure proper error handling for I/O operations

### 5. Demo Application Development
Create a comprehensive demo application to demonstrate:
- Multiple brokers running simultaneously
- Controller election using ZooKeeper
- Topic creation with partition and replica assignment
- ZooKeeper data persistence and monitoring

**Components Created:**
- `BrokerApp.java`: Enhanced broker application with controller election
- `TopicCommandApp.java`: Command-line tool for topic management
- `DEMO_README.md`: Comprehensive demo guide

**Features Demonstrated:**
- Controller election and failover
- Topic creation with validation
- Partition and replica assignment
- Real-time cluster monitoring
- ZooKeeper data inspection

The current `ZkController.java` provides the framework with:
- Controller election structure
- ZooKeeper event handling
- Broker state management

### Partition and Replica Assignment
Implement logic for:
- Distributing partitions across available brokers evenly
- Assigning replicas to different brokers for fault tolerance
- Ensuring replicas are spread across different brokers
- Maintaining balanced load across brokers

Key considerations:
- Each partition should have a leader and followers
- Replicas should be distributed across different brokers for fault tolerance
- Implement a round-robin or similar algorithm for balanced distribution

### ZooKeeper Persistence
Stores topic and partition information in ZooKeeper:
- Create persistent nodes for topics
- Store partition assignments
- Store replica assignments
- Maintain broker-topic-partition mapping

ZooKeeper path structure: 
brokers/topics/[topic_name]
/brokers/topics/[topic_name]/partitions/[partition_id]
/brokers/topics/[topic_name]/partitions/[partition_id]/state

### Topic Partition Change Detection
Callback mechanisms to:
- Monitor changes in topic partitions
- React to partition reassignments
- Handle broker failures affecting partitions
- Update local state based on ZooKeeper changes

Example callback registration:
```java
zookeeperClient.subscribeDataChanges("/brokers/topics", topicChangeListener);
```

### Controller Election
Implement controller election using ZooKeeper:
- Use ZooKeeper's atomic operations for leader election
- Handle controller failover
- Maintain controller state
- Register watches for controller changes

Current implementation in `ZkController.java`:
- Uses ephemeral nodes for controller election
- Handles election failures and existing controllers
- Maintains live broker information
- Implements callback interfaces for ZooKeeper events