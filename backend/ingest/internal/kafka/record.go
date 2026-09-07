package kafka

// Record contains the Kafka transport data shared by producers and consumers.
type Record struct {
	Topic     string
	Partition int32
	Offset    int64
	Key       []byte
	Value     []byte
}
