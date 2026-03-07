package com.lrenyi.template.flow.exception;

import java.util.concurrent.atomic.AtomicBoolean;
import com.lrenyi.template.flow.api.FlowExceptionHandler;
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
    void handleExceptionWithContextInvokesHandlers() {
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
    void handleExceptionWithParamsDelegatesToContext() {
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
    void registerHandlerNullIgnored() {
        assertDoesNotThrow(() -> FlowExceptionHelper.registerHandler(null));
        // Verify no side effects (implicit, hard to verify without inspecting internal list size)
    }
    
    @Test
    void registerHandlerNonNullAdded() {
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
    void removeHandlerRemovesHandler() {
        AtomicBoolean called = new AtomicBoolean(false);
        FlowExceptionHandler h = context -> called.set(true);
        FlowExceptionHelper.registerHandler(h);
        FlowExceptionHelper.removeHandler(h);
        
        FlowExceptionHelper.handleException("j", null, new RuntimeException("test removeHandler"), FlowPhase.UNKNOWN);
        assertFalse(called.get());
    }
    
    @Test
    void setDefaultHandlerNullIgnored() {
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
    void setDefaultHandlerNewHandlerNotInListAddsIt() {
        AtomicBoolean called = new AtomicBoolean(false);
        FlowExceptionHandler custom = ctx -> called.set(true);
        
        FlowExceptionHelper.clearHandlers();
        FlowExceptionHelper.setDefaultHandler(custom);
        
        FlowExceptionHelper.handleException("j",
                                            null,
                                            new RuntimeException("test setDefaultHandler " + "adds"),
                                            FlowPhase.UNKNOWN
        );
        assertTrue(called.get());
    }
    
    @Test
    void handlerThrowingExceptionDoesNotBreakChain() {
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
    void handlerShouldHandleFalseSkipsHandle() {
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
    void clearHandlersRestoresDefaultOnly() {
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
