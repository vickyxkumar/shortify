package com.url.shortify.utils;

public class SnowflakeGenerator {
    private final long epoch = 1700000000000L; // custom epoch

    private final long machineId;
    private final long sequenceBits = 12;
    private final long machineIdBits = 10;

    private final long maxSequence = (1L << sequenceBits) - 1;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeGenerator(long machineId) {
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & maxSequence;

            if (sequence == 0) {
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - epoch) << (machineIdBits + sequenceBits))
                | (machineId << sequenceBits)
                | sequence;
    }

    private long waitNextMillis(long timestamp) {
        while (timestamp == lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
