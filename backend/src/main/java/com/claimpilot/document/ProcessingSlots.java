package com.claimpilot.document;

import java.util.concurrent.Semaphore;

/**
 * At most N documents are processed at the same time on this server. OCR and model calls are
 * heavy; without a limit, many simultaneous uploads would start as many Tesseract processes and
 * model requests and slow everyone down. Further uploads wait their turn (a waiting virtual thread
 * costs almost nothing).
 */
public class ProcessingSlots {

    private final Semaphore slots;

    public ProcessingSlots(int maxConcurrent) {
        this.slots = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    public void run(Runnable work) {
        slots.acquireUninterruptibly();
        try {
            work.run();
        } finally {
            slots.release();
        }
    }

    int available() {
        return slots.availablePermits();
    }
}
