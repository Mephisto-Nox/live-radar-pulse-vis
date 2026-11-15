package com.github.mephisto_nox.radar_pulse_vis.pdw;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class PdwIngestMetrics {
    private final LongAdder receivedDatagrams = new LongAdder();
    private final LongAdder rejectedDatagrams = new LongAdder();
    private final LongAdder decodedPdws = new LongAdder();
    private final LongAdder droppedBatches = new LongAdder();
    private final LongAdder publishedBatches = new LongAdder();
    private final LongAdder publishFailures = new LongAdder();

    public void datagramReceived() { receivedDatagrams.increment(); }
    public long datagramRejected() {
        rejectedDatagrams.increment();
        return rejectedDatagrams.sum();
    }
    public void pdwsDecoded(long count) { decodedPdws.add(count); }
    public long batchDropped() {
        droppedBatches.increment();
        return droppedBatches.sum();
    }
    public void batchPublished() { publishedBatches.increment(); }
    public void publishFailed() { publishFailures.increment(); }

    public Map<String, Long> snapshot() {
        return Map.of(
                "receivedDatagrams", receivedDatagrams.sum(),
                "rejectedDatagrams", rejectedDatagrams.sum(),
                "decodedPdws", decodedPdws.sum(),
                "droppedBatches", droppedBatches.sum(),
                "publishedBatches", publishedBatches.sum(),
                "publishFailures", publishFailures.sum());
    }
}
