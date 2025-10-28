package com.stacksmonitoring.domain.valueobject;

/**
 * Stacks transaction types.
 */
public enum TransactionType {
    TOKEN_TRANSFER,
    SMART_CONTRACT,
    CONTRACT_CALL,
    POISON_MICROBLOCK,
    COINBASE,
    TENURE_CHANGE
}
