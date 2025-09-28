package org.kasbench.globeco_order_service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseDTOTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void testDefaultConstructor() {
        ErrorResponseDTO error = new ErrorResponseDTO();
        
        assertNotNull(error.getTimestamp());
        assertNull(error.getCode());
        assertNull(error.getMessage());
        assertNull(error.getRetryAfter());
        assertNull(error.getDetails());
    }
    
    @Test
    void testConstructorWithCodeAndMessage() {
        String code = "SERVICE_OVERLOADED";
        String message = "System temporarily overloaded";
        
        ErrorResponseDTO error = new ErrorResponseDTO(code, message);
        
        assertEquals(code, error.getCode());
        assertEquals(message, error.getMessage());
        assertNotNull(error.getTimestamp());
        assertNull(error.getRetryAfter());
        assertNull(error.getDetails());
    }
    
    @Test
    void testConstructorWithRetryAfter() {
        String code = "SERVICE_OVERLOADED";
        String message = "System temporarily overloaded";
        Integer retryAfter = 300;
        
        ErrorResponseDTO error = new ErrorResponseDTO(code, message, retryAfter);
        
        assertEquals(code, error.getCode());
        assertEquals(message, error.getMessage());
        assertEquals(retryAfter, error.getRetryAfter());
        assertNotNull(error.getTimestamp());
        assertNull(error.getDetails());
    }
    
    @Test
    void testConstructorWithDetails() {
        String code = "SERVICE_OVERLOADED";
        String message = "System temporarily overloaded";
        Integer retryAfter = 300;
        Map<String, Object> details = new HashMap<>();
        details.put("overloadReason", "database_connection_pool_exhausted");
        details.put("currentLoad", "95%");
        
        ErrorResponseDTO error = new ErrorResponseDTO(code, message, retryAfter, details);
        
        assertEquals(code, error.getCode());
        assertEquals(message, error.getMessage());
        assertEquals(retryAfter, error.getRetryAfter());
        assertEquals(details, error.getDetails());
        assertNotNull(error.getTimestamp());
    }
    
    @Test
    void testSettersAndGetters() {
        ErrorResponseDTO error = new ErrorResponseDTO();
        String code = "VALIDATION_ERROR";
        String message = "Invalid request data";
        Integer retryAfter = 60;
        String timestamp = Instant.now().toString();
        Map<String, Object> details = new HashMap<>();
        details.put("field", "orderId");
        details.put("issue", "required");
        
        error.setCode(code);
        error.setMessage(message);
        error.setRetryAfter(retryAfter);
        error.setTimestamp(timestamp);
        error.setDetails(details);
        
        assertEquals(code, error.getCode());
        assertEquals(message, error.getMessage());
        assertEquals(retryAfter, error.getRetryAfter());
        assertEquals(timestamp, error.getTimestamp());
        assertEquals(details, error.getDetails());
    }
    
    @Test
    void testJsonSerialization() throws Exception {
        String code = "SERVICE_OVERLOADED";
        String message = "System temporarily overloaded";
        Integer retryAfter = 300;
        Map<String, Object> details = new HashMap<>();
        details.put("overloadReason", "thread_pool_exhausted");
        
        ErrorResponseDTO error = new ErrorResponseDTO(code, message, retryAfter, details);
        
        String json = objectMapper.writeValueAsString(error);
        
        assertTrue(json.contains("\"code\":\"SERVICE_OVERLOADED\""));
        assertTrue(json.contains("\"message\":\"System temporarily overloaded\""));
        assertTrue(json.contains("\"retryAfter\":300"));
        assertTrue(json.contains("\"overloadReason\":\"thread_pool_exhausted\""));
        assertTrue(json.contains("\"timestamp\""));
    }
    
    @Test
    void testJsonDeserialization() throws Exception {
        String json = "{\"code\":\"VALIDATION_ERROR\",\"message\":\"Invalid data\",\"retryAfter\":60,\"timestamp\":\"2024-01-15T10:30:00Z\"}";
        
        ErrorResponseDTO error = objectMapper.readValue(json, ErrorResponseDTO.class);
        
        assertEquals("VALIDATION_ERROR", error.getCode());
        assertEquals("Invalid data", error.getMessage());
        assertEquals(Integer.valueOf(60), error.getRetryAfter());
        assertEquals("2024-01-15T10:30:00Z", error.getTimestamp());
    }
    
    @Test
    void testJsonIncludeNonNull() throws Exception {
        ErrorResponseDTO error = new ErrorResponseDTO("TEST_ERROR", "Test message");
        // retryAfter and details are null, should not be included in JSON
        
        String json = objectMapper.writeValueAsString(error);
        
        assertFalse(json.contains("\"retryAfter\""));
        assertFalse(json.contains("\"details\""));
        assertTrue(json.contains("\"code\":\"TEST_ERROR\""));
        assertTrue(json.contains("\"message\":\"Test message\""));
        assertTrue(json.contains("\"timestamp\""));
    }
}