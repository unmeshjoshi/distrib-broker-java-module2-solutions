# Enhanced Kafka-like Distributed System Demo

This demo showcases a Kafka-like distributed system with controller election, topic creation, and ZooKeeper integration.

## Features Demonstrated

1. **Controller Election**: Automatic election of a controller broker using ZooKeeper
2. **Topic Creation**: Create topics with specified partitions and replication factors
3. **Partition Assignment**: Automatic assignment of partitions to brokers
4. **Replica Distribution**: Even distribution of replicas across brokers
5. **ZooKeeper Integration**: Persistent storage of topic metadata and broker information
6. **Real-time Monitoring**: Watch mechanisms for cluster changes

## Prerequisites

- Docker installed and running
- Java 8 or higher
- Gradle (included in the project)

## Available Gradle Tasks

The project includes several Gradle tasks to make running the demo easier:

### Application Tasks
- `./gradlew runBroker` - Run BrokerApp with custom arguments
- `./gradlew runTopicCommand` - Run TopicCommandApp with custom arguments

### Demo Tasks (Pre-configured)
- `./gradlew runBroker1` - Start Broker 1 (localhost:2181 1)
- `./gradlew runBroker2` - Start Broker 2 (localhost:2181 2)
- `./gradlew runBroker3` - Start Broker 3 (localhost:2181 3)
- `./gradlew createTestTopic` - Create test topic (test-topic with 3 partitions, 2 replicas)
- `./gradlew listTopics` - List all topics
- `./gradlew listBrokers` - List all brokers

### Custom Arguments
You can also use the default run task with custom arguments:
- `./gradlew run --args="localhost:2181 1"` - Run BrokerApp
- `./gradlew run --args="localhost:2181 createTopic my-topic 5 3"` - Run TopicCommandApp

To see all available tasks:
```bash
./gradlew tasks --group application
./gradlew tasks --group demo
```

## Step 1: Start ZooKeeper

Start a ZooKeeper container:

```bash
docker run --rm --name zookeeper-demo -p 2181:2181 zookeeper:3.8.1
```

Keep this terminal open - ZooKeeper will continue running.

## Step 2: Start Multiple Brokers

Open **new terminal windows** for each broker to demonstrate controller election.

### Terminal 2: Start Broker 1
```bash
# Option 1: Using the default run task
./gradlew runBroker --args="localhost:2181 1"

# Option 2: Using the specific broker task
./gradlew runBroker1
```

### Terminal 3: Start Broker 2
```bash
# Option 1: Using the default run task
./gradlew runBroker --args="localhost:2181 2"

# Option 2: Using the specific broker task
./gradlew runBroker2
```

### Terminal 4: Start Broker 3
```bash
# Option 1: Using the default run task
./gradlew runBroker --args="localhost:2181 3"

# Option 2: Using the specific broker task
./gradlew runBroker3
```

## Step 3: Create Topics

Open another terminal to create topics using the TopicCommandApp:

### Create a Topic
```bash
# Option 1: Using the default run task
./gradlew runTopicCommand --args="localhost:2181 listTopics test-topic 3 2"

# Option 2: Using the specific topic creation task
./gradlew createTestTopic
```

This creates a topic named "test-topic" with:
- 3 partitions
- Replication factor of 2 (each partition has 2 replicas)

### List All Topics
```bash
# Option 1: Using the default run task
./gradlew run --args="localhost:2181 listTopics"

# Option 2: Using the specific list topics task
./gradlew listTopics
```

### List All Brokers
```bash
# Option 1: Using the default run task
./gradlew run --args="localhost:2181 listBrokers"

# Option 2: Using the specific list brokers task
./gradlew listBrokers
```

## Expected Output

### When Starting Brokers

**Broker 1 (First to start - becomes controller):**
```
=== Enhanced Broker Demo Application ===
ZooKeeper Address: localhost:2181
Broker ID: 1
Features: Controller Election, Topic Management
=============================================
🎯 This broker is now the CONTROLLER!
Registering broker 1 with ZooKeeper...
✓ Broker 1 registered successfully!

=== CURRENT CLUSTER STATE ===
Total brokers in cluster: 1
  Broker 1 (CONTROLLER): Broker[id=1, host=192.168.1.100, port=9093]
Total topics: 0
=============================
```

**Broker 2 (Second to start - becomes follower):**
```
=== Enhanced Broker Demo Application ===
ZooKeeper Address: localhost:2181
Broker ID: 2
Features: Controller Election, Topic Management
=============================================
📡 This broker is a FOLLOWER. Controller ID: 1
Registering broker 2 with ZooKeeper...
✓ Broker 2 registered successfully!

=== CURRENT CLUSTER STATE ===
Total brokers in cluster: 2
  Broker 1 (CONTROLLER): Broker[id=1, host=192.168.1.100, port=9093]
  Broker 2: Broker[id=2, host=192.168.1.100, port=9094]
=============================
```

### When Creating a Topic

```
=== Creating Topic ===
Topic Name: test-topic
Partitions: 3
Replication Factor: 2

✓ Topic 'test-topic' created successfully!
Partition assignments:
  Partition 0: [1, 2]
  Partition 1: [2, 3]
  Partition 2: [3, 1]
=====================
```

### When Listing Topics

```
=== Available Topics ===
Topic: test-topic
  Partitions: 3
    Partition 0: [1, 2]
    Partition 1: [2, 3]
    Partition 2: [3, 1]

=======================
```

## ZooKeeper Data Structure

### Broker Information
- **Path**: `/brokers/ids/{brokerId}`
- **Type**: Ephemeral (automatically deleted when broker disconnects)
- **Data**: JSON with broker information

### Controller Information
- **Path**: `/controller`
- **Type**: Ephemeral (only one controller at a time)
- **Data**: Broker ID of the current controller

### Topic Information
- **Path**: `/brokers/topics/{topicName}`
- **Type**: Persistent (survives broker restarts)
- **Data**: JSON array of partition assignments

### Example ZooKeeper Inspection

Connect to ZooKeeper and inspect the data:

```bash
# Connect to ZooKeeper container
docker exec -it zookeeper-demo /bin/bash
cd bin
./zkCli.sh

# List all brokers
ls /brokers/ids

# Get controller information
get /controller

# List all topics
ls /brokers/topics

# Get topic partition assignments
get /brokers/topics/test-topic
```

## Controller Election Process

1. **First Broker**: When the first broker starts, it attempts to create the `/controller` ephemeral node
2. **Success**: If successful, it becomes the controller
3. **Subsequent Brokers**: Later brokers find the controller node exists and become followers
4. **Controller Failure**: If the controller fails, the ephemeral node is automatically deleted
5. **Re-election**: Other brokers detect the deletion and attempt to become the new controller

## Topic Creation Process

1. **Validation**: Check if enough brokers are available for the replication factor
2. **Replica Assignment**: Use the ReplicaAssigner to distribute partitions across brokers
3. **ZooKeeper Storage**: Store partition assignments as persistent nodes
4. **Notification**: All brokers receive notifications about the new topic
5. **Controller Processing**: The controller updates its internal topic state

## Testing Scenarios

### Scenario 1: Controller Failover
1. Start 3 brokers (Broker 1 becomes controller)
2. Stop Broker 1 (Ctrl+C)
3. Observe Broker 2 or 3 becoming the new controller
4. Restart Broker 1 - it becomes a follower

### Scenario 2: Topic Creation with Different Configurations
```bash
# Create topic with 5 partitions, replication factor 3
./gradlew run --args="localhost:2181 createTopic large-topic 5 3"

# Create topic with 1 partition, replication factor 1
./gradlew run --args="localhost:2181 createTopic simple-topic 1 1"
```

### Scenario 3: Insufficient Brokers
```bash
# Try to create topic with replication factor higher than available brokers
./gradlew run --args="localhost:2181 createTopic invalid-topic 3 5"
# Should show error: "Not enough brokers available"
```

## Key Concepts Demonstrated

1. **Controller Election (Cluster leader-election)**: ZooKeeper ensures only one controller exists
2. **Fault Tolerance**: Controller failover when the current controller fails
3. **Load Balancing**: Even distribution of partitions and replicas across brokers
4. **Persistence**: Topic metadata survives broker restarts
5. **Real-time Updates**: Brokers receive immediate notifications of cluster changes
6. **Scalability**: System works with varying numbers of brokers

## Cleanup

To stop the demo:

1. **Stop all broker applications**: Press `Ctrl+C` in each broker terminal
2. **Stop ZooKeeper**: Press `Ctrl+C` in the ZooKeeper terminal
3. **Remove container**: The `--rm` flag will automatically remove the container

## Troubleshooting

### Common Issues

1. **Port Already in Use**: Check if port 2181 is already in use
2. **Connection Refused**: Ensure ZooKeeper is running and accessible
3. **Controller Not Elected**: Check ZooKeeper logs for connection issues
4. **Topic Creation Fails**: Verify enough brokers are running for the replication factor

### Debug Commands

```bash
# Check ZooKeeper status
echo ruok | nc localhost 2181

# Check what's using port 2181
lsof -i :2181

# View ZooKeeper logs
docker logs zookeeper-demo
```

## Next Steps

After running this demo, you can explore:

1. **Enhanced Topic Management**: Add topic deletion and modification
2. **Partition Rebalancing**: Implement automatic partition reassignment
3. **Producer/Consumer**: Add message production and consumption
4. **Monitoring**: Add metrics and health checks
5. **Security**: Implement authentication and authorization 