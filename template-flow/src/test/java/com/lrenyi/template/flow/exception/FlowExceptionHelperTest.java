package com.lrenyi.template.flow.exception;

import com.lrenyi.template.flow.api.FlowExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FlowExceptionHelperTest {
    
    @AfterEach
    void tearDown() {
        FlowExceptionHelper.clearHandlers();
    }
    
    @Test
    void handleException_withContext_invokesHandlers() {
        FlowExceptionContext ctx = new FlowExceptionContext("job1",
                                                            "e1",
                                                            new RuntimeException("test invokesHandlers"),
                                                            FlowPhase.PRODUCTION
        );
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException(ctx));
    }
    
    @Test
    void handleException_withParams_delegatesToContext() {
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("job1",
                                                                     "e1",
                                                                     new RuntimeException("test delegatesToContext"),
                                                                     FlowPhase.STORAGE
        ));
    }
    
    @Test
    void registerHandler_null_ignored() {
        assertDoesNotThrow(() -> FlowExceptionHelper.registerHandler(null));
    }
    
    @Test
    void registerHandler_nonNull_added() {
        FlowExceptionHandler h = context -> {};
        FlowExceptionHelper.registerHandler(h);
        FlowExceptionHelper.handleException(new FlowExceptionContext("j",
                                                                     null,
                                                                     new RuntimeException("test registerHandler"),
                                                                     FlowPhase.UNKNOWN
        ));
        FlowExceptionHelper.removeHandler(h);
    }
    
    @Test
    void removeHandler_removesHandler() {
        FlowExceptionHandler h = context -> {};
        FlowExceptionHelper.registerHandler(h);
        FlowExceptionHelper.removeHandler(h);
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException("test removeHandler"),
                                                                     FlowPhase.UNKNOWN
        ));
    }
    
    @Test
    void setDefaultHandler_null_ignored() {
        FlowExceptionHandler before = new DefaultFlowExceptionHandler();
        FlowExceptionHelper.setDefaultHandler(before);
        FlowExceptionHelper.setDefaultHandler(null);
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException("test setDefaultHandler "
                                                                                                  + "null"),
                                                                     FlowPhase.UNKNOWN
        ));
    }
    
    @Test
    void setDefaultHandler_newHandler_notInList_addsIt() {
        FlowExceptionHandler custom = new DefaultFlowExceptionHandler();
        FlowExceptionHelper.clearHandlers();
        FlowExceptionHelper.setDefaultHandler(custom);
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException("test setDefaultHandler "
                                                                                                  + "adds"),
                                                                     FlowPhase.UNKNOWN
        ));
    }
    
    @Test
    void handlerThrowingException_doesNotBreakChain() {
        FlowExceptionHelper.registerHandler(context -> {
            throw new RuntimeException("handler fail");
        });
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException("test handler chain"),
                                                                     FlowPhase.UNKNOWN
        ));
    }
    
    @Test
    void handlerShouldHandleFalse_skipsHandle() {
        FlowExceptionHelper.clearHandlers();
        FlowExceptionHelper.registerHandler(new FlowExceptionHandler() {
            @Override
            public void handleException(FlowExceptionContext context) {
                throw new AssertionError("should not be called");
            }
            
            @Override
            public boolean shouldHandle(FlowExceptionContext context) {
                return false;
            }
        });
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException("test shouldHandle false"),
                                                                     FlowPhase.UNKNOWN
        ));
    }
    
    @Test
    void clearHandlers_restoresDefaultOnly() {
        FlowExceptionHelper.clearHandlers();
        assertDoesNotThrow(() -> FlowExceptionHelper.handleException("j",
                                                                     null,
                                                                     new RuntimeException("test clearHandlers"),
                                                                     FlowPhase.UNKNOWN
        ));
    }
}
