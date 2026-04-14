package com.lrenyi.template.flow.resource;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PermitPairTest {

    @Test
    void tryAcquireBothWithResult_singlePermit_success() throws InterruptedException {
        Semaphore global = new Semaphore(2, true);
        Semaphore perJob = new Semaphore(2, true);
        PermitPair pair = PermitPair.of(global, perJob);

        PermitPair.AcquireResult result = pair.tryAcquireBothWithResult(1, 50, TimeUnit.MILLISECONDS);
        assertSame(PermitPair.AcquireResult.SUCCESS, result);
        assertEquals(1, global.availablePermits());
        assertEquals(1, perJob.availablePermits());

        pair.release(1);
        assertEquals(2, global.availablePermits());
        assertEquals(2, perJob.availablePermits());
    }

    @Test
    void tryAcquireBothWithResult_perJobFails_rollsBackGlobal() throws InterruptedException {
        Semaphore global = new Semaphore(2, true);
        Semaphore perJob = new Semaphore(0, true);
        PermitPair pair = PermitPair.of(global, perJob);

        PermitPair.AcquireResult result = pair.tryAcquireBothWithResult(1, 50, TimeUnit.MILLISECONDS);
        assertSame(PermitPair.AcquireResult.FAILED_ON_PER_JOB, result);
        assertEquals(2, global.availablePermits());
        assertEquals(0, perJob.availablePermits());
    }

    @Test
    void tryAcquireBothWithResult_globalNull_usesOnlyPerJob() throws InterruptedException {
        Semaphore perJob = new Semaphore(1, true);
        PermitPair pair = PermitPair.of(null, perJob);

        PermitPair.AcquireResult result = pair.tryAcquireBothWithResult(1);
        assertSame(PermitPair.AcquireResult.SUCCESS, result);
        assertEquals(0, perJob.availablePermits());

        pair.release(1);
        assertEquals(1, perJob.availablePermits());
    }

    @Test
    void tryAcquireBothWithResult_releaseOrder_perJobFirstThenGlobal() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(1, true);
        PermitPair pair = PermitPair.of(global, perJob);

        assertSame(PermitPair.AcquireResult.SUCCESS, pair.tryAcquireBothWithResult(1));
        pair.release(1);

        assertEquals(1, global.availablePermits());
        assertEquals(1, perJob.availablePermits());
    }

    @Test
    void tryAcquireBothWithResult_withTimeout_success() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(1, true);
        PermitPair pair = PermitPair.of(global, perJob);

        assertSame(PermitPair.AcquireResult.SUCCESS,
                pair.tryAcquireBothWithResult(1, 100, TimeUnit.MILLISECONDS));
        pair.release(1);
    }

    @Test
    void tryAcquireBothWithResult_withTimeout_perJobFull_returnsFailedOnPerJob() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(0, true);
        PermitPair pair = PermitPair.of(global, perJob);

        PermitPair.AcquireResult result = pair.tryAcquireBothWithResult(1, 50, TimeUnit.MILLISECONDS);
        assertSame(PermitPair.AcquireResult.FAILED_ON_PER_JOB, result);
        assertEquals(1, global.availablePermits());
    }
}
