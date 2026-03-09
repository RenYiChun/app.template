package com.lrenyi.template.flow.resource;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermitPairTest {

    @Test
    void tryAcquireBoth_singlePermit_success() throws InterruptedException {
        Semaphore global = new Semaphore(2, true);
        Semaphore perJob = new Semaphore(2, true);
        PermitPair pair = PermitPair.of(global, perJob);

        boolean acquired = pair.tryAcquireBoth(1, 50, TimeUnit.MILLISECONDS);
        assertTrue(acquired);
        assertEquals(1, global.availablePermits());
        assertEquals(1, perJob.availablePermits());

        pair.release(1);
        assertEquals(2, global.availablePermits());
        assertEquals(2, perJob.availablePermits());
    }

    @Test
    void tryAcquireBoth_perJobFails_rollsBackGlobal() throws InterruptedException {
        Semaphore global = new Semaphore(2, true);
        Semaphore perJob = new Semaphore(0, true);
        PermitPair pair = PermitPair.of(global, perJob);

        boolean acquired = pair.tryAcquireBoth(1, 50, TimeUnit.MILLISECONDS);
        assertFalse(acquired);
        assertEquals(2, global.availablePermits());
        assertEquals(0, perJob.availablePermits());
    }

    @Test
    void tryAcquireBoth_globalNull_usesOnlyPerJob() throws InterruptedException {
        Semaphore perJob = new Semaphore(1, true);
        PermitPair pair = PermitPair.of(null, perJob);

        boolean acquired = pair.tryAcquireBoth(1);
        assertTrue(acquired);
        assertEquals(0, perJob.availablePermits());

        pair.release(1);
        assertEquals(1, perJob.availablePermits());
    }

    @Test
    void tryAcquireBoth_releaseOrder_perJobFirstThenGlobal() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(1, true);
        PermitPair pair = PermitPair.of(global, perJob);

        assertTrue(pair.tryAcquireBoth(1));
        pair.release(1);

        assertEquals(1, global.availablePermits());
        assertEquals(1, perJob.availablePermits());
    }

    @Test
    void tryAcquireBoth_withTimeout_success() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(1, true);
        PermitPair pair = PermitPair.of(global, perJob);

        boolean acquired = pair.tryAcquireBoth(1, 100, TimeUnit.MILLISECONDS);
        assertTrue(acquired);
        pair.release(1);
    }

    @Test
    void tryAcquireBoth_withTimeout_perJobFull_returnsFalse() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(0, true);
        PermitPair pair = PermitPair.of(global, perJob);

        boolean acquired = pair.tryAcquireBoth(1, 50, TimeUnit.MILLISECONDS);
        assertFalse(acquired);
        assertEquals(1, global.availablePermits());
    }
}
