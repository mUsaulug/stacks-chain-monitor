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
    SMART_CONTRACT_EVENT
}
