package com.stacksmonitoring.domain.valueobject;

/**
 * Transaction event types in Stacks blockchain.
 * This enum covers all possible event types that can occur during transaction execution.
 */
public enum EventType {
    FT_MINT,
    FT_BURN,
    FT_TRANSFER,
    NFT_MINT,
    NFT_BURN,
    NFT_TRANSFER,
    STX_TRANSFER,
    STX_MINT,
    STX_BURN,
    STX_LOCK,
    SMART_CONTRACT_EVENT,
    UNKNOWN;

    /**
     * Parse event type from Chainhook wire format.
     * Handles both "_EVENT" suffix and plain format (e.g., "FT_TRANSFER_EVENT" or "FT_TRANSFER").
     *
     * @param wireFormat The event type string from Chainhook webhook
     * @return The corresponding EventType enum, or UNKNOWN if not recognized
     */
    public static EventType fromWireFormat(String wireFormat) {
        if (wireFormat == null || wireFormat.isBlank()) {
            return UNKNOWN;
        }

        // Normalize: uppercase and remove "_EVENT" suffix if present
        String normalized = wireFormat.toUpperCase().trim();
        if (normalized.endsWith("_EVENT")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }

        // Handle special cases
        return switch (normalized) {
            case "FT_TRANSFER" -> FT_TRANSFER;
            case "FT_MINT" -> FT_MINT;
            case "FT_BURN" -> FT_BURN;
            case "NFT_TRANSFER" -> NFT_TRANSFER;
            case "NFT_MINT" -> NFT_MINT;
            case "NFT_BURN" -> NFT_BURN;
            case "STX_TRANSFER" -> STX_TRANSFER;
            case "STX_MINT" -> STX_MINT;
            case "STX_BURN" -> STX_BURN;
            case "STX_LOCK" -> STX_LOCK;
            case "SMART_CONTRACT_LOG", "PRINT" -> SMART_CONTRACT_EVENT;
            default -> UNKNOWN;
        };
    }
}
