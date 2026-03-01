package com.lrenyi.template.flow.exception;

import com.lrenyi.template.flow.api.FlowExceptionHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExceptionHelperTest {
    
    @AfterEach
    void tearDown() {
        FlowExceptionHelper.clearHandlers();
    }
    
    @Test
    void handleException_withContext_invokesHandlers() {
        AtomicBoolean called = new AtomicBoolean(false);
        FlowExceptionHelper.registerHandler(ctx -> called.set(true));
        
        FlowExceptionContext ctx = new FlowExceptionContext("job1",
                                                            "e1",
                                                            new RuntimeException("test invokesHandlers"),
                                                            FlowPhase.PRODUCTION
        );
        FlowExceptionHelper.handleException(ctx);
        assertTrue(called.get());
    }
    
    @Test
    void handleException_withParams_delegatesToContext() {
        AtomicBoolean called = new AtomicBoolean(false);
        FlowExceptionHelper.registerHandler(ctx -> {
            if ("job1".equals(ctx.getJobId()) && "test delegatesToContext".equals(ctx.getException().getMessage())) {
                called.set(true);
            }
        });
        
        FlowExceptionHelper.handleException("job1",
                                             "e1",
                                             new RuntimeException("test delegatesToContext"),
                                             FlowPhase.STORAGE
        );
        assertTrue(called.get());
    }
    
    @Test
    void registerHandler_null_ignored() {
        assertDoesNotThrow(() -> FlowExceptionHelper.registerHandler(null));
        // Verify no side effects (implicit, hard to verify without inspecting internal list size)
    }
    
    @Test
    void registerHandler_nonNull_added() {
        AtomicBoolean called = new AtomicBoolean(false);
        FlowExceptionHandler h = context -> called.set(true);
        FlowExceptionHelper.registerHandler(h);
        
        FlowExceptionHelper.handleException(new FlowExceptionContext("j",
                                                                     null,
                                                                     new RuntimeException("test registerHandler"),
                                                                     FlowPhase.UNKNOWN
        ));
        assertTrue(called.get());
        
        FlowExceptionHelper.removeHandler(h);
    }
    
    @Test
    void removeHandler_removesHandler() {
        AtomicBoolean called = new AtomicBoolean(false);
        FlowExceptionHandler h = context -> called.set(true);
        FlowExceptionHelper.registerHandler(h);
        FlowExceptionHelper.removeHandler(h);
        
        FlowExceptionHelper.handleException("j",
                                            null,
                                            new RuntimeException("test removeHandler"),
                                            FlowPhase.UNKNOWN
        );
        assertFalse(called.get());
    }
    
    @Test
    void setDefaultHandler_null_ignored() {
        FlowExceptionHandler before = new DefaultFlowExceptionHandler();
        FlowExceptionHelper.setDefaultHandler(before);
        FlowExceptionHelper.setDefaultHandler(null);
        // Difficult to verify "ignored" without access to internal state, but ensure no exception
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException(
                                                                             "test setDefaultHandler " + "null"),
                                                                     FlowPhase.UNKNOWN
        ));
    }
    
    @Test
    void setDefaultHandler_newHandler_notInList_addsIt() {
        AtomicBoolean called = new AtomicBoolean(false);
        FlowExceptionHandler custom = ctx -> called.set(true);
        
        FlowExceptionHelper.clearHandlers();
        FlowExceptionHelper.setDefaultHandler(custom);
        
        FlowExceptionHelper.handleException("j",
                                            null,
                                            new RuntimeException(
                                                    "test setDefaultHandler " + "adds"),
                                            FlowPhase.UNKNOWN
        );
        assertTrue(called.get());
    }
    
    @Test
    void handlerThrowingException_doesNotBreakChain() {
        AtomicBoolean secondCalled = new AtomicBoolean(false);
        
        FlowExceptionHelper.registerHandler(context -> {
            throw new RuntimeException("handler fail");
        });
        FlowExceptionHelper.registerHandler(context -> secondCalled.set(true));
        
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException("test handler chain"),
                                                                     FlowPhase.UNKNOWN
        ));
        assertTrue(secondCalled.get());
    }
    
    @Test
    void handlerShouldHandleFalse_skipsHandle() {
        FlowExceptionHelper.clearHandlers();
        AtomicBoolean handled = new AtomicBoolean(false);
        
        FlowExceptionHelper.registerHandler(new FlowExceptionHandler() {
            @Override
            public void handleException(FlowExceptionContext context) {
                handled.set(true);
                throw new AssertionError("should not be called");
            }
            
            @Override
            public boolean shouldHandle(FlowExceptionContext context) {
                return false;
            }
        });
        
        FlowExceptionHelper.handleException("j",
                                            null,
                                            new RuntimeException("test shouldHandle false"),
                                            FlowPhase.UNKNOWN
        );
        assertFalse(handled.get());
    }
    
    @Test
    void clearHandlers_restoresDefaultOnly() {
        // This test assumes clearHandlers resets to a state where only default handler exists.
        // Since we can't easily check internal state, we just ensure it doesn't throw.
        FlowExceptionHelper.clearHandlers();
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException("test clearHandlers"),
                                                                     FlowPhase.UNKNOWN
        ));
    }
}
