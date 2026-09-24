package com.claimpilot.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ProcessingSlotsTest {

    @Test
    void noMoreThanTheLimitRunAtOnceAndTheRestWait() throws Exception {
        ProcessingSlots slots = new ProcessingSlots(2);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();

        List<Thread> uploads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            uploads.add(Thread.startVirtualThread(() -> slots.run(() -> {
                peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                running.decrementAndGet();
                done.incrementAndGet();
            })));
        }
        for (Thread upload : uploads) {
            upload.join();
        }

        assertThat(peak.get()).isEqualTo(2);
        assertThat(done.get()).as("every upload is processed in the end").isEqualTo(8);
        assertThat(slots.available()).isEqualTo(2);
    }

    @Test
    void aFailedDocumentGivesItsSlotBack() {
        ProcessingSlots slots = new ProcessingSlots(1);
        try {
            slots.run(() -> {
                throw new IllegalStateException("unreadable");
            });
        } catch (IllegalStateException expected) {
            // the document fails; the next one must still get a slot
        }
        assertThat(slots.available()).isEqualTo(1);
    }
}
