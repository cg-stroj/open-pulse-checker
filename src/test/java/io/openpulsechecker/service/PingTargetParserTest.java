package io.openpulsechecker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PingTargetParserTest {

    @Test
    void validateForConfigurationAcceptsHostnameAndIpv4() {
        assertEquals("example.com", PingTargetParser.validateForConfiguration("example.com"));
        assertEquals("1.1.1.1", PingTargetParser.validateForConfiguration("1.1.1.1"));
    }

    @Test
    void validateForConfigurationRejectsUrlAndPort() {
        IllegalArgumentException urlEx = assertThrows(
                IllegalArgumentException.class,
                () -> PingTargetParser.validateForConfiguration("https://example.com"));
        assertEquals(
                "PING target must be a hostname or IP address without URL scheme (e.g. example.com or 1.1.1.1).",
                urlEx.getMessage());

        IllegalArgumentException portEx = assertThrows(
                IllegalArgumentException.class,
                () -> PingTargetParser.validateForConfiguration("example.com:443"));
        assertEquals("PING target must not include a port. Use hostname/IP only.", portEx.getMessage());
    }

    @Test
    void resolveHostForExecutionSupportsLegacyUrlAndHostname() {
        assertEquals("example.com", PingTargetParser.resolveHostForExecution("https://example.com/path"));
        assertEquals("example.com", PingTargetParser.resolveHostForExecution("example.com"));
    }
}
