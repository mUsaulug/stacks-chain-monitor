package com.stacksmonitoring.infrastructure.parser;

import com.stacksmonitoring.api.dto.webhook.*;
import com.stacksmonitoring.domain.model.blockchain.*;
import com.stacksmonitoring.domain.valueobject.EventType;
import com.stacksmonitoring.infrastructure.mapper.ChainhookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for converting Chainhook webhook DTOs to domain entities.
 * Uses MapStruct for compile-time type-safe mapping (13x faster than reflection).
 *
 * BEFORE (P2-1): Manual field-by-field mapping (300+ lines)
 * AFTER: MapStruct declarative mapping (100 lines)
 *
 * Reference: CLAUDE.md P2-1 (MapStruct Integration)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChainhookPayloadParser {

    private final ChainhookMapper chainhookMapper;

    /**
     * Parse a block event DTO to a StacksBlock domain entity.
     * Uses MapStruct for automatic mapping.
     */
    public StacksBlock parseBlock(BlockEventDto blockEventDto) {
        return chainhookMapper.toStacksBlock(blockEventDto);
    }

    /**
     * Parse a transaction DTO to a StacksTransaction domain entity.
     * Uses MapStruct for base mapping, manual parsing for complex relationships.
     */
    public StacksTransaction parseTransaction(TransactionDto transactionDto, StacksBlock block) {
        // MapStruct handles base field mapping
        StacksTransaction transaction = chainhookMapper.toStacksTransaction(transactionDto);
        transaction.setBlock(block);

        TransactionMetadataDto metadata = transactionDto.getMetadata();
        if (metadata != null && metadata.getKind() != null) {
            String kindType = metadata.getKind().getType();

            // Parse contract call or deployment using MapStruct
            if ("ContractCall".equalsIgnoreCase(kindType)) {
                ContractCall contractCall = chainhookMapper.toContractCall(metadata.getKind());
                contractCall.setTransaction(transaction);
                transaction.setContractCall(contractCall);
            } else if ("ContractDeployment".equalsIgnoreCase(kindType)) {
                ContractDeployment deployment = chainhookMapper.toContractDeployment(metadata.getKind());
                deployment.setTransaction(transaction);
                transaction.setContractDeployment(deployment);
            }

            // Parse events
            if (metadata.getReceipt() != null && metadata.getReceipt().getEvents() != null) {
                List<TransactionEvent> events = parseEvents(metadata.getReceipt().getEvents(), transaction);
                events.forEach(transaction::addEvent);
            }
        }

        return transaction;
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
     * Uses MapStruct for type-safe mapping and EventType.fromWireFormat() for type resolution.
     * Package-private for testing.
     */
    TransactionEvent parseEvent(EventDto eventDto, StacksTransaction transaction, int index) {
        String eventTypeString = eventDto.getType();
        if (eventTypeString == null) {
            log.warn("Event type is null, skipping event at index {}", index);
            return null;
        }

        if (eventDto.getData() == null) {
            log.warn("Event data is null for type {}, skipping", eventTypeString);
            return null;
        }

        // Type-safe parsing: wire format → enum → MapStruct mapper
        EventType eventType = EventType.fromWireFormat(eventTypeString);

        TransactionEvent event = switch (eventType) {
            case FT_TRANSFER -> chainhookMapper.toFTTransferEvent(eventDto);
            case FT_MINT -> createFTMintEvent(eventDto);
            case FT_BURN -> createFTBurnEvent(eventDto);
            case NFT_TRANSFER -> chainhookMapper.toNFTTransferEvent(eventDto);
            case NFT_MINT -> createNFTMintEvent(eventDto);
            case NFT_BURN -> createNFTBurnEvent(eventDto);
            case STX_TRANSFER -> chainhookMapper.toSTXTransferEvent(eventDto);
            case STX_MINT -> createSTXMintEvent(eventDto);
            case STX_BURN -> createSTXBurnEvent(eventDto);
            case STX_LOCK -> createSTXLockEvent(eventDto);
            case SMART_CONTRACT_EVENT -> chainhookMapper.toSmartContractEvent(eventDto);
            case UNKNOWN -> {
                log.warn("Unknown event type: {}", eventTypeString);
                yield null;
            }
        };

        if (event != null) {
            event.setTransaction(transaction);
            event.setEventIndex(index);
        }

        return event;
    }

    // Simple event creation helpers (for events not yet in MapStruct)
    // TODO: Consider moving these to MapStruct mapper if they become more complex

    private FTMintEvent createFTMintEvent(EventDto eventDto) {
        FTMintEvent event = new FTMintEvent();
        event.setAssetIdentifier(chainhookMapper.extractAssetIdentifier(eventDto.getData()));
        event.setAmount(chainhookMapper.extractAmount(eventDto.getData()));
        event.setRecipient(chainhookMapper.extractRecipient(eventDto.getData()));
        event.setContractIdentifier(chainhookMapper.extractContractFromAsset(eventDto.getData()));
        event.setEventType(EventType.FT_MINT);
        event.setDeleted(false);
        return event;
    }

    private FTBurnEvent createFTBurnEvent(EventDto eventDto) {
        FTBurnEvent event = new FTBurnEvent();
        event.setAssetIdentifier(chainhookMapper.extractAssetIdentifier(eventDto.getData()));
        event.setAmount(chainhookMapper.extractAmount(eventDto.getData()));
        event.setSender(chainhookMapper.extractSender(eventDto.getData()));
        event.setContractIdentifier(chainhookMapper.extractContractFromAsset(eventDto.getData()));
        event.setEventType(EventType.FT_BURN);
        event.setDeleted(false);
        return event;
    }

    private NFTMintEvent createNFTMintEvent(EventDto eventDto) {
        NFTMintEvent event = new NFTMintEvent();
        event.setAssetIdentifier(chainhookMapper.extractAssetIdentifier(eventDto.getData()));
        event.setRawValue(chainhookMapper.extractValue(eventDto.getData()));
        event.setRecipient(chainhookMapper.extractRecipient(eventDto.getData()));
        event.setContractIdentifier(chainhookMapper.extractContractFromAsset(eventDto.getData()));
        event.setEventType(EventType.NFT_MINT);
        event.setDeleted(false);
        return event;
    }

    private NFTBurnEvent createNFTBurnEvent(EventDto eventDto) {
        NFTBurnEvent event = new NFTBurnEvent();
        event.setAssetIdentifier(chainhookMapper.extractAssetIdentifier(eventDto.getData()));
        event.setRawValue(chainhookMapper.extractValue(eventDto.getData()));
        event.setSender(chainhookMapper.extractSender(eventDto.getData()));
        event.setContractIdentifier(chainhookMapper.extractContractFromAsset(eventDto.getData()));
        event.setEventType(EventType.NFT_BURN);
        event.setDeleted(false);
        return event;
    }

    private STXMintEvent createSTXMintEvent(EventDto eventDto) {
        STXMintEvent event = new STXMintEvent();
        event.setAmount(chainhookMapper.extractAmount(eventDto.getData()));
        event.setRecipient(chainhookMapper.extractRecipient(eventDto.getData()));
        event.setEventType(EventType.STX_MINT);
        event.setDeleted(false);
        return event;
    }

    private STXBurnEvent createSTXBurnEvent(EventDto eventDto) {
        STXBurnEvent event = new STXBurnEvent();
        event.setAmount(chainhookMapper.extractAmount(eventDto.getData()));
        event.setSender(chainhookMapper.extractSender(eventDto.getData()));
        event.setEventType(EventType.STX_BURN);
        event.setDeleted(false);
        return event;
    }

    private STXLockEvent createSTXLockEvent(EventDto eventDto) {
        STXLockEvent event = new STXLockEvent();
        event.setLockAmount(chainhookMapper.extractAmount(eventDto.getData()));
        event.setLockedAddress(chainhookMapper.extractStringValue(eventDto.getData(), "address"));

        // Extract unlock_height as Long
        Object unlockHeight = eventDto.getData().get("unlock_height");
        if (unlockHeight instanceof Number) {
            event.setUnlockHeight(((Number) unlockHeight).longValue());
        }

        event.setEventType(EventType.STX_LOCK);
        event.setDeleted(false);
        return event;
    }
}
