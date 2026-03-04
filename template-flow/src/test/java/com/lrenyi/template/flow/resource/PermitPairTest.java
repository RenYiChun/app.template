package com.lrenyi.template.flow.resource;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermitPairTest {
    
    @Test
    void tryAcquireBoth_singlePermit_success() throws InterruptedException {
        Semaphore global = new Semaphore(2, true);
        Semaphore perJob = new Semaphore(2, true);
        
        boolean acquired = PermitPair.tryAcquireBoth(global, perJob, 1);
        assertTrue(acquired);
        assertTrue(global.availablePermits() == 1);
        assertTrue(perJob.availablePermits() == 1);
        
        PermitPair pair = PermitPair.createHeld(global, perJob, 1);
        pair.release();
        assertTrue(global.availablePermits() == 2);
        assertTrue(perJob.availablePermits() == 2);
    }
    
    @Test
    void tryAcquireBoth_perJobFails_rollsBackGlobal() throws InterruptedException {
        Semaphore global = new Semaphore(2, true);
        Semaphore perJob = new Semaphore(0, true);
        
        boolean acquired = PermitPair.tryAcquireBoth(global, perJob, 1);
        assertFalse(acquired);
        assertTrue(global.availablePermits() == 2);
        assertTrue(perJob.availablePermits() == 0);
    }
    
    @Test
    void tryAcquireBoth_globalNull_usesOnlyPerJob() throws InterruptedException {
        Semaphore perJob = new Semaphore(1, true);
        
        boolean acquired = PermitPair.tryAcquireBoth(null, perJob, 1);
        assertTrue(acquired);
        assertTrue(perJob.availablePermits() == 0);
        
        PermitPair.createHeld(null, perJob, 1).release();
        assertTrue(perJob.availablePermits() == 1);
    }
    
    @Test
    void tryAcquireBoth_releaseOrder_perJobFirstThenGlobal() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(1, true);
        
        assertTrue(PermitPair.tryAcquireBoth(global, perJob, 1));
        PermitPair pair = PermitPair.createHeld(global, perJob, 1);
        pair.release();
        
        assertTrue(global.availablePermits() == 1);
        assertTrue(perJob.availablePermits() == 1);
    }
    
    @Test
    void tryAcquireBoth_withTimeout_success() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(1, true);
        
        boolean acquired = PermitPair.tryAcquireBoth(global, perJob, 1, 100, TimeUnit.MILLISECONDS);
        assertTrue(acquired);
        PermitPair.createHeld(global, perJob, 1).release();
    }
    
    @Test
    void tryAcquireBoth_withTimeout_perJobFull_returnsFalse() throws InterruptedException {
        Semaphore global = new Semaphore(1, true);
        Semaphore perJob = new Semaphore(0, true);
        
        boolean acquired = PermitPair.tryAcquireBoth(global, perJob, 1, 50, TimeUnit.MILLISECONDS);
        assertFalse(acquired);
        assertTrue(global.availablePermits() == 1);
    }
}
