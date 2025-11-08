package com.stacksmonitoring.infrastructure.parser;

import com.stacksmonitoring.api.dto.webhook.*;
import com.stacksmonitoring.domain.model.blockchain.*;
import com.stacksmonitoring.domain.valueobject.EventType;
import com.stacksmonitoring.domain.valueobject.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parser for converting Chainhook webhook DTOs to domain entities.
 * Handles the transformation of webhook payloads into blockchain domain objects.
 */
@Component
@Slf4j
public class ChainhookPayloadParser {

    /**
     * Parse a block event DTO to a StacksBlock domain entity.
     */
    public StacksBlock parseBlock(BlockEventDto blockEventDto) {
        StacksBlock block = new StacksBlock();

        // Block identifier
        BlockIdentifierDto identifier = blockEventDto.getBlockIdentifier();
        block.setBlockHash(identifier.getHash());
        block.setBlockHeight(identifier.getIndex());
        block.setIndexBlockHash(identifier.getHash());

        // Parent block
        if (blockEventDto.getParentBlockIdentifier() != null) {
            block.setParentBlockHash(blockEventDto.getParentBlockIdentifier().getHash());
        }

        // Timestamp
        if (blockEventDto.getTimestamp() != null) {
            block.setTimestamp(Instant.ofEpochSecond(blockEventDto.getTimestamp()));
        }

        // Metadata (burn block info)
        if (blockEventDto.getMetadata() != null) {
            BlockMetadataDto metadata = blockEventDto.getMetadata();
            if (metadata.getBurnBlockHeight() != null) {
                block.setBurnBlockHeight(metadata.getBurnBlockHeight());
            }
            if (metadata.getBurnBlockHash() != null) {
                block.setBurnBlockHash(metadata.getBurnBlockHash());
            }
            if (metadata.getBurnBlockTime() != null) {
                block.setBurnBlockTimestamp(Instant.ofEpochSecond(metadata.getBurnBlockTime()));
            }
            if (metadata.getMiner() != null) {
                block.setMinerAddress(metadata.getMiner());
            }
        }

        block.setDeleted(false);

        return block;
    }

    /**
     * Parse a transaction DTO to a StacksTransaction domain entity.
     */
    public StacksTransaction parseTransaction(TransactionDto transactionDto, StacksBlock block) {
        StacksTransaction transaction = new StacksTransaction();

        // Transaction identifier
        transaction.setTxId(transactionDto.getTransactionIdentifier().getHash());
        transaction.setBlock(block);

        TransactionMetadataDto metadata = transactionDto.getMetadata();
        if (metadata != null) {
            // Basic metadata
            transaction.setSender(metadata.getSender());
            transaction.setSuccess(metadata.getSuccess() != null ? metadata.getSuccess() : false);
            transaction.setSponsorAddress(metadata.getSponsor());

            // Fee
            if (metadata.getFee() != null) {
                transaction.setFeeRate(BigDecimal.valueOf(metadata.getFee()));
            }

            // Position/index
            if (metadata.getPosition() != null && metadata.getPosition().getIndex() != null) {
                transaction.setTxIndex(metadata.getPosition().getIndex());
            } else {
                transaction.setTxIndex(0);
            }

            // Nonce (parse from metadata)
            if (metadata.getNonce() != null) {
                transaction.setNonce(metadata.getNonce());
            } else {
                transaction.setNonce(0L);
            }

            // Execution cost
            if (metadata.getExecutionCost() != null) {
                ExecutionCostDto cost = metadata.getExecutionCost();
                transaction.setExecutionCostReadCount(cost.getReadCount());
                transaction.setExecutionCostReadLength(cost.getReadLength());
                transaction.setExecutionCostRuntime(cost.getRuntime());
                transaction.setExecutionCostWriteCount(cost.getWriteCount());
                transaction.setExecutionCostWriteLength(cost.getWriteLength());
            }

            // Raw result and raw transaction
            transaction.setRawResult(metadata.getResult());
            transaction.setRawTx(metadata.getRawTx());

            // Transaction type and kind-specific data
            if (metadata.getKind() != null) {
                String kindType = metadata.getKind().getType();
                transaction.setTxType(parseTransactionType(kindType));

                // Parse contract call or deployment
                if ("ContractCall".equalsIgnoreCase(kindType)) {
                    ContractCall contractCall = parseContractCall(metadata.getKind(), transaction);
                    transaction.setContractCall(contractCall);
                } else if ("ContractDeployment".equalsIgnoreCase(kindType)) {
                    ContractDeployment deployment = parseContractDeployment(metadata.getKind(), transaction);
                    transaction.setContractDeployment(deployment);
                }
            } else {
                transaction.setTxType(TransactionType.UNKNOWN);
            }

            // Parse events
            if (metadata.getReceipt() != null && metadata.getReceipt().getEvents() != null) {
                List<TransactionEvent> events = parseEvents(metadata.getReceipt().getEvents(), transaction);
                events.forEach(transaction::addEvent);
            }
        }

        transaction.setDeleted(false);

        return transaction;
    }

    /**
     * Parse transaction type from string.
     */
    private TransactionType parseTransactionType(String type) {
        if (type == null) return TransactionType.UNKNOWN;

        return switch (type.toUpperCase()) {
            case "CONTRACTCALL" -> TransactionType.CONTRACT_CALL;
            case "CONTRACTDEPLOYMENT" -> TransactionType.CONTRACT_DEPLOYMENT;
            case "TOKENTRANSFER" -> TransactionType.TOKEN_TRANSFER;
            case "COINBASE" -> TransactionType.COINBASE;
            case "POISONMICROBLOCK" -> TransactionType.POISON_MICROBLOCK;
            default -> TransactionType.UNKNOWN;
        };
    }

    /**
     * Parse contract call from transaction kind DTO.
     */
    private ContractCall parseContractCall(TransactionKindDto kindDto, StacksTransaction transaction) {
        ContractCall contractCall = new ContractCall();
        contractCall.setTransaction(transaction);

        Map<String, Object> data = kindDto.getData();
        if (data != null) {
            if (data.get("contract_identifier") != null) {
                contractCall.setContractIdentifier(data.get("contract_identifier").toString());
            }
            if (data.get("method") != null) {
                contractCall.setFunctionName(data.get("method").toString());
            }

            // Store full args as JSONB
            if (data.get("args") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> args = (Map<String, Object>) data.get("args");
                contractCall.setFunctionArgs(args);
            }
        }

        return contractCall;
    }

    /**
     * Parse contract deployment from transaction kind DTO.
     */
    private ContractDeployment parseContractDeployment(TransactionKindDto kindDto, StacksTransaction transaction) {
        ContractDeployment deployment = new ContractDeployment();
        deployment.setTransaction(transaction);

        Map<String, Object> data = kindDto.getData();
        if (data != null) {
            if (data.get("contract_identifier") != null) {
                deployment.setContractIdentifier(data.get("contract_identifier").toString());
            }
            if (data.get("contract_name") != null) {
                deployment.setContractName(data.get("contract_name").toString());
            }
            if (data.get("code") != null) {
                deployment.setSourceCode(data.get("code").toString());
            }

            // Store ABI as JSONB if available
            if (data.get("abi") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> abi = (Map<String, Object>) data.get("abi");
                deployment.setAbi(abi);
            }
        }

        return deployment;
    }

    /**
     * Parse events from receipt.
     */
    private List<TransactionEvent> parseEvents(List<EventDto> eventDtos, StacksTransaction transaction) {
        List<TransactionEvent> events = new ArrayList<>();

        for (int i = 0; i < eventDtos.size(); i++) {
            EventDto eventDto = eventDtos.get(i);
            TransactionEvent event = parseEvent(eventDto, transaction, i);
            if (event != null) {
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Parse a single event DTO to appropriate TransactionEvent subtype.
     * Uses type-safe enum parsing to prevent runtime typos.
     */
    private TransactionEvent parseEvent(EventDto eventDto, StacksTransaction transaction, int index) {
        String eventTypeString = eventDto.getType();
        if (eventTypeString == null) {
            log.warn("Event type is null, skipping event at index {}", index);
            return null;
        }

        Map<String, Object> data = eventDto.getData();
        if (data == null) {
            log.warn("Event data is null for type {}, skipping", eventTypeString);
            return null;
        }

        // Type-safe parsing: wire format → enum
        EventType eventType = EventType.fromWireFormat(eventTypeString);

        TransactionEvent event = switch (eventType) {
            case FT_TRANSFER -> parseFTTransferEvent(data);
            case FT_MINT -> parseFTMintEvent(data);
            case FT_BURN -> parseFTBurnEvent(data);
            case NFT_TRANSFER -> parseNFTTransferEvent(data);
            case NFT_MINT -> parseNFTMintEvent(data);
            case NFT_BURN -> parseNFTBurnEvent(data);
            case STX_TRANSFER -> parseSTXTransferEvent(data);
            case STX_MINT -> parseSTXMintEvent(data);
            case STX_BURN -> parseSTXBurnEvent(data);
            case STX_LOCK -> parseSTXLockEvent(data);
            case SMART_CONTRACT_EVENT -> parseSmartContractEvent(data);
            case UNKNOWN -> {
                log.warn("Unknown event type: {}", eventTypeString);
                yield null;
            }
        };

        if (event != null) {
            event.setTransaction(transaction);
            event.setEventIndex(index);

            // Extract contract identifier from data if available
            if (data.get("contract_identifier") != null) {
                event.setContractIdentifier(data.get("contract_identifier").toString());
            } else if (data.get("asset_identifier") != null) {
                // For FT/NFT events, extract contract from asset identifier
                String assetId = data.get("asset_identifier").toString();
                if (assetId.contains("::")) {
                    event.setContractIdentifier(assetId.substring(0, assetId.lastIndexOf("::")));
                }
            }
        }

        return event;
    }

    private FTTransferEvent parseFTTransferEvent(Map<String, Object> data) {
        FTTransferEvent event = new FTTransferEvent();
        event.setAssetIdentifier(getStringValue(data, "asset_identifier"));
        event.setAmount(getBigDecimalValue(data, "amount"));
        event.setSender(getStringValue(data, "sender"));
        event.setRecipient(getStringValue(data, "recipient"));
        return event;
    }

    private FTMintEvent parseFTMintEvent(Map<String, Object> data) {
        FTMintEvent event = new FTMintEvent();
        event.setAssetIdentifier(getStringValue(data, "asset_identifier"));
        event.setAmount(getBigDecimalValue(data, "amount"));
        event.setRecipient(getStringValue(data, "recipient"));
        return event;
    }

    private FTBurnEvent parseFTBurnEvent(Map<String, Object> data) {
        FTBurnEvent event = new FTBurnEvent();
        event.setAssetIdentifier(getStringValue(data, "asset_identifier"));
        event.setAmount(getBigDecimalValue(data, "amount"));
        event.setSender(getStringValue(data, "sender"));
        return event;
    }

    private NFTTransferEvent parseNFTTransferEvent(Map<String, Object> data) {
        NFTTransferEvent event = new NFTTransferEvent();
        event.setAssetIdentifier(getStringValue(data, "asset_identifier"));
        event.setAssetId(getStringValue(data, "value"));
        event.setSender(getStringValue(data, "sender"));
        event.setRecipient(getStringValue(data, "recipient"));
        return event;
    }

    private NFTMintEvent parseNFTMintEvent(Map<String, Object> data) {
        NFTMintEvent event = new NFTMintEvent();
        event.setAssetIdentifier(getStringValue(data, "asset_identifier"));
        event.setAssetId(getStringValue(data, "value"));
        event.setRecipient(getStringValue(data, "recipient"));
        return event;
    }

    private NFTBurnEvent parseNFTBurnEvent(Map<String, Object> data) {
        NFTBurnEvent event = new NFTBurnEvent();
        event.setAssetIdentifier(getStringValue(data, "asset_identifier"));
        event.setAssetId(getStringValue(data, "value"));
        event.setSender(getStringValue(data, "sender"));
        return event;
    }

    private STXTransferEvent parseSTXTransferEvent(Map<String, Object> data) {
        STXTransferEvent event = new STXTransferEvent();
        event.setAmount(getBigDecimalValue(data, "amount"));
        event.setSender(getStringValue(data, "sender"));
        event.setRecipient(getStringValue(data, "recipient"));
        return event;
    }

    private STXMintEvent parseSTXMintEvent(Map<String, Object> data) {
        STXMintEvent event = new STXMintEvent();
        event.setAmount(getBigDecimalValue(data, "amount"));
        event.setRecipient(getStringValue(data, "recipient"));
        return event;
    }

    private STXBurnEvent parseSTXBurnEvent(Map<String, Object> data) {
        STXBurnEvent event = new STXBurnEvent();
        event.setAmount(getBigDecimalValue(data, "amount"));
        event.setSender(getStringValue(data, "sender"));
        return event;
    }

    private STXLockEvent parseSTXLockEvent(Map<String, Object> data) {
        STXLockEvent event = new STXLockEvent();
        event.setAmount(getBigDecimalValue(data, "locked_amount"));
        event.setAddress(getStringValue(data, "address"));
        event.setUnlockHeight(getLongValue(data, "unlock_height"));
        return event;
    }

    private SmartContractEvent parseSmartContractEvent(Map<String, Object> data) {
        SmartContractEvent event = new SmartContractEvent();
        event.setTopic(getStringValue(data, "topic"));

        // Store the entire value as decoded JSON
        if (data.get("value") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) data.get("value");
            event.setValueDecoded(value);
        }

        event.setValueRaw(getStringValue(data, "raw_value"));
        return event;
    }

    // Helper methods for safe data extraction

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return BigDecimal.ZERO;

        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }

        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal from value: {}", value);
            return BigDecimal.ZERO;
        }
    }

    private Long getLongValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse Long from value: {}", value);
            return null;
        }
    }
}
