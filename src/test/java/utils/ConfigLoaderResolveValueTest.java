package utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigLoaderResolveValueTest {

    @Test
    void resolveValue_returnsTrimmedPlainValue() {
        assertEquals("abc", ConfigLoader.resolveValue("  abc  "));
    }

    @Test
    void resolveValue_returnsDefaultFromPlaceholder_whenEnvMissing() {
        assertEquals("def", ConfigLoader.resolveValue("${MISSING_ENV:def}"));
    }

    @Test
    void resolveValue_handlesQuotedValue() {
        assertEquals("abc", ConfigLoader.resolveValue("\"abc\""));
    }
}
