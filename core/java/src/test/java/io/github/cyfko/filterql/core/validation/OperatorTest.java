package io.github.cyfko.filterql.core.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperatorTest {
    
    @Test
    void testFromStringWithSymbol() {
        assertEquals(Op.EQ, Op.fromString("="));
        assertEquals(Op.NE, Op.fromString("!="));
        assertEquals(Op.GT, Op.fromString(">"));
        assertEquals(Op.MATCHES, Op.fromString("LIKE"));
    }
    
    @Test
    void testFromStringWithCode() {
        assertEquals(Op.EQ, Op.fromString("EQ"));
        assertEquals(Op.NE, Op.fromString("NE"));
        assertEquals(Op.GT, Op.fromString("GT"));
        assertEquals(Op.MATCHES, Op.fromString("LIKE"));
    }
    
    @Test
    void testFromStringCaseInsensitive() {
        assertEquals(Op.EQ, Op.fromString("eq"));
        assertEquals(Op.MATCHES, Op.fromString("like"));
        assertEquals(Op.IN, Op.fromString("in"));
    }
    
    @Test
    void testFromStringWithWhitespace() {
        // Test avec des espaces autour des codes/symboles valides
        assertEquals(Op.EQ, Op.fromString(" = "));
        assertEquals(Op.EQ, Op.fromString(" EQ "));
        assertEquals(Op.MATCHES, Op.fromString(" LIKE "));
    }
    
    @Test
    void testFromStringNull() {
        assertThrows(NullPointerException.class, () -> Op.fromString(null));
    }
    
    @Test
    void testFromStringInvalid() {
        assertEquals(Op.CUSTOM, Op.fromString("INVALID"));
    }

    @Test
    void testFromCodeOrSymbol() {
        for (var op :  Op.values()) {
            if (op ==  Op.CUSTOM) continue;
            assertEquals(op, Op.fromString(op.getCode()));
            assertEquals(op, Op.fromString(op.getSymbol()));
        }
    }
    
    @Test
    void testRequiresValue() {
        assertTrue(Op.EQ.requiresValue());
        assertTrue(Op.MATCHES.requiresValue());
        assertTrue(Op.IN.requiresValue());
        assertFalse(Op.IS_NULL.requiresValue());
        assertFalse(Op.NOT_NULL.requiresValue());
    }
    
    @Test
    void testSupportsMultipleValues() {
        assertTrue(Op.IN.supportsMultipleValues());
        assertTrue(Op.NOT_IN.supportsMultipleValues());
        assertTrue(Op.RANGE.supportsMultipleValues());
        assertTrue(Op.NOT_RANGE.supportsMultipleValues());
        assertFalse(Op.EQ.supportsMultipleValues());
        assertFalse(Op.MATCHES.supportsMultipleValues());
    }
    
    @Test
    void testGetSymbol() {
        assertEquals("=", Op.EQ.getSymbol());
        assertEquals("!=", Op.NE.getSymbol());
        assertEquals("LIKE", Op.MATCHES.getSymbol());
    }
    
    @Test
    void testGetCode() {
        assertEquals("EQ", Op.EQ.getCode());
        assertEquals("NE", Op.NE.getCode());
        assertEquals("MATCHES", Op.MATCHES.getCode());
    }
}
