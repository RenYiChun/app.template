package com.lrenyi.template.core.json;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonServiceTest {
    
    @Mock
    private JsonProcessor processor;
    
    private JsonService jsonService;
    
    @BeforeEach
    void setUp() {
        when(processor.getProcessorName()).thenReturn("test-processor");
        jsonService = new JsonService(processor);
    }
    
    @Test
    void getProcessorName_returnsProcessorName() {
        assertEquals("test-processor", jsonService.getProcessorName());
    }
    
    @Test
    void serialize_success_returnsJson() throws JsonProcessingException {
        when(processor.toJson(any())).thenReturn("{\"a\":1}");
        assertEquals("{\"a\":1}", jsonService.serialize(new Object()));
        verify(processor).toJson(any());
    }
    
    @Test
    void serialize_exception_throwsRuntimeException() throws JsonProcessingException {
        when(processor.toJson(any())).thenThrow(new JsonProcessingException("") {});
        RuntimeException ex = assertThrows(RuntimeException.class, () -> jsonService.serialize("x"));
        assertEquals("JSON serialization failed", ex.getMessage());
    }
    
    @Test
    void deserialize_class_success_returnsObject() throws JsonProcessingException {
        when(processor.fromJson(eq("{}"), eq(String.class))).thenReturn("ok");
        assertEquals("ok", jsonService.deserialize("{}", String.class));
    }
    
    @Test
    void deserialize_class_exception_throwsRuntimeException() throws JsonProcessingException {
        when(processor.fromJson(any(), any(Class.class))).thenThrow(new JsonProcessingException("") {});
        assertThrows(RuntimeException.class, () -> jsonService.deserialize("x", String.class));
    }
    
    @Test
    void deserialize_typeReference_success_returnsObject() throws JsonProcessingException {
        TypeReference<List<String>> ref = new TypeReference<>() {};
        when(processor.fromJson(eq("[]"), eq(ref))).thenReturn(List.of("a"));
        assertEquals(List.of("a"), jsonService.deserialize("[]", ref));
    }
    
    @Test
    void deserialize_typeReference_exception_throwsRuntimeException() throws JsonProcessingException {
        TypeReference<List<String>> ref = new TypeReference<>() {};
        when(processor.fromJson(any(), any(TypeReference.class))).thenThrow(new JsonProcessingException("") {});
        assertThrows(RuntimeException.class, () -> jsonService.deserialize("x", ref));
    }
    
    @Test
    void parseToNode_success_returnsNode() throws JsonProcessingException {
        JsonNode node = org.mockito.Mockito.mock(JsonNode.class);
        when(processor.parse(eq("{}"))).thenReturn(node);
        assertEquals(node, jsonService.parseToNode("{}"));
    }
    
    @Test
    void parseToNode_exception_throwsRuntimeException() throws JsonProcessingException {
        when(processor.parse(any())).thenThrow(new JsonProcessingException("") {});
        assertThrows(RuntimeException.class, () -> jsonService.parseToNode("x"));
    }
    
    @Test
    void prettyPrint_success_returnsString() throws JsonProcessingException {
        when(processor.prettyPrint(any())).thenReturn("  {}");
        assertEquals("  {}", jsonService.prettyPrint(new Object()));
    }
    
    @Test
    void prettyPrint_exception_throwsRuntimeException() throws JsonProcessingException {
        when(processor.prettyPrint(any())).thenThrow(new JsonProcessingException("") {});
        assertThrows(RuntimeException.class, () -> jsonService.prettyPrint("x"));
    }
    
    @Test
    void toMap_success_returnsMap() throws JsonProcessingException {
        Map<String, Object> map = Map.of("k", "v");
        when(processor.toMap(eq("{}"))).thenReturn(map);
        assertEquals(map, jsonService.toMap("{}"));
    }
    
    @Test
    void toMap_exception_throwsRuntimeException() throws JsonProcessingException {
        when(processor.toMap(any())).thenThrow(new JsonProcessingException("") {});
        assertThrows(RuntimeException.class, () -> jsonService.toMap("x"));
    }
    
    @Test
    void toList_success_returnsList() throws JsonProcessingException {
        List<String> list = List.of("a");
        when(processor.toList(eq("[]"), eq(String.class))).thenReturn(list);
        assertEquals(list, jsonService.toList("[]", String.class));
    }
    
    @Test
    void toList_exception_throwsRuntimeException() throws JsonProcessingException {
        when(processor.toList(any(), any(Class.class))).thenThrow(new JsonProcessingException("") {});
        assertThrows(RuntimeException.class, () -> jsonService.toList("x", String.class));
    }
    
    @Test
    void registerCustomAdapter_success_callsProcessor() {
        jsonService.registerCustomAdapter(String.class, "adapter");
        verify(processor).registerTypeAdapter(String.class, "adapter");
    }
    
    @Test
    void registerCustomAdapter_exception_throwsRuntimeException() {
        doThrow(new RuntimeException("reg")).when(processor).registerTypeAdapter(String.class, "x");
        assertThrows(RuntimeException.class, () -> jsonService.registerCustomAdapter(String.class, "x"));
    }
    
    @Test
    void supportsFeature_delegatesToProcessor() {
        when(processor.supportsFeature(JsonProcessor.JsonProcessorFeature.PRETTY_PRINT)).thenReturn(true);
        assertEquals(true, jsonService.supportsFeature(JsonProcessor.JsonProcessorFeature.PRETTY_PRINT));
    }
}
