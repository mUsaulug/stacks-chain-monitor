package com.stacksmonitoring.domain.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EventType.fromWireFormat() (P2-4: Type-safe event parsing).
 * Tests wire format string to enum conversion.
 */
class EventTypeTest {

    @ParameterizedTest
    @CsvSource({
        "FT_TRANSFER_EVENT, FT_TRANSFER",
        "FT_TRANSFER, FT_TRANSFER",
        "FT_MINT_EVENT, FT_MINT",
        "FT_MINT, FT_MINT",
        "FT_BURN_EVENT, FT_BURN",
        "FT_BURN, FT_BURN",
        "NFT_TRANSFER_EVENT, NFT_TRANSFER",
        "NFT_TRANSFER, NFT_TRANSFER",
        "NFT_MINT_EVENT, NFT_MINT",
        "NFT_MINT, NFT_MINT",
        "NFT_BURN_EVENT, NFT_BURN",
        "NFT_BURN, NFT_BURN",
        "STX_TRANSFER_EVENT, STX_TRANSFER",
        "STX_TRANSFER, STX_TRANSFER",
        "STX_MINT_EVENT, STX_MINT",
        "STX_MINT, STX_MINT",
        "STX_BURN_EVENT, STX_BURN",
        "STX_BURN, STX_BURN",
        "STX_LOCK_EVENT, STX_LOCK",
        "STX_LOCK, STX_LOCK"
    })
    void testFromWireFormat_ValidFormats_ShouldParse(String wireFormat, EventType expected) {
        // When
        EventType result = EventType.fromWireFormat(wireFormat);

        // Then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "SMART_CONTRACT_LOG, SMART_CONTRACT_EVENT",
        "PRINT_EVENT, SMART_CONTRACT_EVENT",
        "PRINT, SMART_CONTRACT_EVENT"
    })
    void testFromWireFormat_SmartContractEventAliases_ShouldParse(String wireFormat, EventType expected) {
        // When
        EventType result = EventType.fromWireFormat(wireFormat);

        // Then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void testFromWireFormat_CaseInsensitive_ShouldParse() {
        // Given
        String[] variations = {
            "ft_transfer_event",
            "FT_TRANSFER_EVENT",
            "Ft_Transfer_Event",
            "fT_tRaNsFeR_eVeNt"
        };

        // When/Then
        for (String variation : variations) {
            EventType result = EventType.fromWireFormat(variation);
            assertThat(result).isEqualTo(EventType.FT_TRANSFER);
        }
    }

    @Test
    void testFromWireFormat_WithWhitespace_ShouldTrimAndParse() {
        // Given
        String wireFormat = "  FT_TRANSFER_EVENT  ";

        // When
        EventType result = EventType.fromWireFormat(wireFormat);

        // Then
        assertThat(result).isEqualTo(EventType.FT_TRANSFER);
    }

    @Test
    void testFromWireFormat_NullInput_ShouldReturnUnknown() {
        // When
        EventType result = EventType.fromWireFormat(null);

        // Then
        assertThat(result).isEqualTo(EventType.UNKNOWN);
    }

    @Test
    void testFromWireFormat_EmptyString_ShouldReturnUnknown() {
        // When
        EventType result = EventType.fromWireFormat("");

        // Then
        assertThat(result).isEqualTo(EventType.UNKNOWN);
    }

    @Test
    void testFromWireFormat_BlankString_ShouldReturnUnknown() {
        // When
        EventType result = EventType.fromWireFormat("   ");

        // Then
        assertThat(result).isEqualTo(EventType.UNKNOWN);
    }

    @Test
    void testFromWireFormat_UnknownType_ShouldReturnUnknown() {
        // Given
        String[] unknownTypes = {
            "INVALID_EVENT",
            "RANDOM_TYPE",
            "FT_TRANSFER_WRONG",
            "NOT_AN_EVENT"
        };

        // When/Then
        for (String unknownType : unknownTypes) {
            EventType result = EventType.fromWireFormat(unknownType);
            assertThat(result).isEqualTo(EventType.UNKNOWN);
        }
    }

    @Test
    void testFromWireFormat_BackwardCompatibility_OldAndNewFormats() {
        // Given - both "_EVENT" suffix and without should work
        String oldFormat = "FT_TRANSFER_EVENT";
        String newFormat = "FT_TRANSFER";

        // When
        EventType fromOld = EventType.fromWireFormat(oldFormat);
        EventType fromNew = EventType.fromWireFormat(newFormat);

        // Then - both should produce same result
        assertThat(fromOld).isEqualTo(EventType.FT_TRANSFER);
        assertThat(fromNew).isEqualTo(EventType.FT_TRANSFER);
        assertThat(fromOld).isEqualTo(fromNew);
    }

    @Test
    void testFromWireFormat_AllEnumValues_ShouldHaveWireMapping() {
        // Verify that all non-UNKNOWN enum values can be parsed from wire format
        assertThat(EventType.fromWireFormat("FT_MINT")).isEqualTo(EventType.FT_MINT);
        assertThat(EventType.fromWireFormat("FT_BURN")).isEqualTo(EventType.FT_BURN);
        assertThat(EventType.fromWireFormat("FT_TRANSFER")).isEqualTo(EventType.FT_TRANSFER);
        assertThat(EventType.fromWireFormat("NFT_MINT")).isEqualTo(EventType.NFT_MINT);
        assertThat(EventType.fromWireFormat("NFT_BURN")).isEqualTo(EventType.NFT_BURN);
        assertThat(EventType.fromWireFormat("NFT_TRANSFER")).isEqualTo(EventType.NFT_TRANSFER);
        assertThat(EventType.fromWireFormat("STX_TRANSFER")).isEqualTo(EventType.STX_TRANSFER);
        assertThat(EventType.fromWireFormat("STX_MINT")).isEqualTo(EventType.STX_MINT);
        assertThat(EventType.fromWireFormat("STX_BURN")).isEqualTo(EventType.STX_BURN);
        assertThat(EventType.fromWireFormat("STX_LOCK")).isEqualTo(EventType.STX_LOCK);
        assertThat(EventType.fromWireFormat("SMART_CONTRACT_LOG")).isEqualTo(EventType.SMART_CONTRACT_EVENT);
    }
}
