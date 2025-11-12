package com.stacksmonitoring.infrastructure.parser;

import com.stacksmonitoring.api.dto.webhook.*;
import com.stacksmonitoring.domain.model.blockchain.*;
import com.stacksmonitoring.domain.valueobject.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ChainhookPayloadParser.
 */
class ChainhookPayloadParserTest {

    private ChainhookPayloadParser parser;
    private com.stacksmonitoring.infrastructure.mapper.ChainhookMapper chainhookMapper;

    @BeforeEach
    void setUp() {
        chainhookMapper = org.mapstruct.factory.Mappers.getMapper(com.stacksmonitoring.infrastructure.mapper.ChainhookMapper.class);
        parser = new ChainhookPayloadParser(chainhookMapper);
    }

    @Test
    void testParseBlock_WithBasicData_ShouldCreateBlock() {
        // Given
        BlockEventDto blockEventDto = createTestBlockEvent(
            100L,
            "0x1234567890abcdef",
            "0xparent123",
            1234567890L
        );

        // When
        StacksBlock block = parser.parseBlock(blockEventDto);

        // Then
        assertThat(block).isNotNull();
        assertThat(block.getBlockHash()).isEqualTo("0x1234567890abcdef");
        assertThat(block.getBlockHeight()).isEqualTo(100L);
        assertThat(block.getParentBlockHash()).isEqualTo("0xparent123");
        assertThat(block.getDeleted()).isFalse();
    }

    @Test
    void testParseBlock_WithMetadata_ShouldIncludeBurnBlockInfo() {
        // Given
        BlockEventDto blockEventDto = createTestBlockEvent(
            100L,
            "0x1234567890abcdef",
            "0xparent123",
            1234567890L
        );

        BlockMetadataDto metadata = new BlockMetadataDto();
        metadata.setBurnBlockHeight(99L);
        metadata.setBurnBlockHash("0xburn123");
        metadata.setBurnBlockTime(1234567800L);
        metadata.setMiner("SP2ABC123");
        blockEventDto.setMetadata(metadata);

        // When
        StacksBlock block = parser.parseBlock(blockEventDto);

        // Then
        assertThat(block.getBurnBlockHeight()).isEqualTo(99L);
        assertThat(block.getBurnBlockHash()).isEqualTo("0xburn123");
        assertThat(block.getMinerAddress()).isEqualTo("SP2ABC123");
    }

    @Test
    void testParseTransaction_WithContractCall_ShouldCreateContractCall() {
        // Given
        StacksBlock block = new StacksBlock();
        TransactionDto txDto = createTestTransaction("0xtx123", "SP123", true);

        // Add contract call metadata
        TransactionKindDto kindDto = new TransactionKindDto();
        kindDto.setType("ContractCall");
        Map<String, Object> data = new HashMap<>();
        data.put("contract_identifier", "SP123.my-contract");
        data.put("method", "transfer");
        Map<String, Object> args = new HashMap<>();
        args.put("amount", "1000");
        args.put("recipient", "SP456");
        data.put("args", args);
        kindDto.setData(data);
        txDto.getMetadata().setKind(kindDto);

        // When
        StacksTransaction transaction = parser.parseTransaction(txDto, block);

        // Then
        assertThat(transaction).isNotNull();
        assertThat(transaction.getTxId()).isEqualTo("0xtx123");
        assertThat(transaction.getSender()).isEqualTo("SP123");
        assertThat(transaction.getSuccess()).isTrue();
        assertThat(transaction.getTxType()).isEqualTo(TransactionType.CONTRACT_CALL);

        assertThat(transaction.getContractCall()).isNotNull();
        assertThat(transaction.getContractCall().getContractIdentifier()).isEqualTo("SP123.my-contract");
        assertThat(transaction.getContractCall().getFunctionName()).isEqualTo("transfer");
        assertThat(transaction.getContractCall().getFunctionArgs()).isNotNull();
    }

    @Test
    void testParseTransaction_WithContractDeployment_ShouldCreateDeployment() {
        // Given
        StacksBlock block = new StacksBlock();
        TransactionDto txDto = createTestTransaction("0xtx456", "SP789", true);

        // Add contract deployment metadata
        TransactionKindDto kindDto = new TransactionKindDto();
        kindDto.setType("ContractDeployment");
        Map<String, Object> data = new HashMap<>();
        data.put("contract_identifier", "SP789.new-contract");
        data.put("contract_name", "new-contract");
        data.put("code", "(define-public (hello) (ok \"world\"))");
        kindDto.setData(data);
        txDto.getMetadata().setKind(kindDto);

        // When
        StacksTransaction transaction = parser.parseTransaction(txDto, block);

        // Then
        assertThat(transaction.getTxType()).isEqualTo(TransactionType.CONTRACT_DEPLOYMENT);
        assertThat(transaction.getContractDeployment()).isNotNull();
        assertThat(transaction.getContractDeployment().getContractIdentifier()).isEqualTo("SP789.new-contract");
        assertThat(transaction.getContractDeployment().getContractName()).isEqualTo("new-contract");
        assertThat(transaction.getContractDeployment().getSourceCode()).contains("define-public");
    }

    @Test
    void testParseEvent_FTTransfer_ShouldCreateFTTransferEvent() {
        // Given
        EventDto eventDto = new EventDto();
        eventDto.setType("FT_TRANSFER_EVENT");

        Map<String, Object> data = new HashMap<>();
        data.put("asset_identifier", "SP123.token::my-token");
        data.put("amount", "5000");
        data.put("sender", "SP111");
        data.put("recipient", "SP222");
        eventDto.setData(data);

        StacksTransaction transaction = new StacksTransaction();

        // When
        TransactionEvent event = parser.parseEvent(eventDto, transaction, 0);

        // Then
        assertThat(event).isInstanceOf(FTTransferEvent.class);
        FTTransferEvent ftEvent = (FTTransferEvent) event;
        assertThat(ftEvent.getAssetIdentifier()).isEqualTo("SP123.token::my-token");
        assertThat(ftEvent.getAmount()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(ftEvent.getSender()).isEqualTo("SP111");
        assertThat(ftEvent.getRecipient()).isEqualTo("SP222");
    }

    @Test
    void testParseEvent_NFTTransfer_ShouldCreateNFTTransferEvent() {
        // Given
        EventDto eventDto = new EventDto();
        eventDto.setType("NFT_TRANSFER_EVENT");

        Map<String, Object> data = new HashMap<>();
        data.put("asset_identifier", "SP123.nft::my-nft");
        data.put("value", "42");
        data.put("sender", "SP111");
        data.put("recipient", "SP222");
        eventDto.setData(data);

        StacksTransaction transaction = new StacksTransaction();

        // When
        TransactionEvent event = parser.parseEvent(eventDto, transaction, 0);

        // Then
        assertThat(event).isInstanceOf(NFTTransferEvent.class);
        NFTTransferEvent nftEvent = (NFTTransferEvent) event;
        assertThat(nftEvent.getAssetIdentifier()).isEqualTo("SP123.nft::my-nft");
        assertThat(nftEvent.getAssetId()).isEqualTo("42");
        assertThat(nftEvent.getSender()).isEqualTo("SP111");
        assertThat(nftEvent.getRecipient()).isEqualTo("SP222");
    }

    @Test
    void testParseEvent_STXTransfer_ShouldCreateSTXTransferEvent() {
        // Given
        EventDto eventDto = new EventDto();
        eventDto.setType("STX_TRANSFER_EVENT");

        Map<String, Object> data = new HashMap<>();
        data.put("amount", "1000000");
        data.put("sender", "SP111");
        data.put("recipient", "SP222");
        eventDto.setData(data);

        StacksTransaction transaction = new StacksTransaction();

        // When
        TransactionEvent event = parser.parseEvent(eventDto, transaction, 0);

        // Then
        assertThat(event).isInstanceOf(STXTransferEvent.class);
        STXTransferEvent stxEvent = (STXTransferEvent) event;
        assertThat(stxEvent.getAmount()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(stxEvent.getSender()).isEqualTo("SP111");
        assertThat(stxEvent.getRecipient()).isEqualTo("SP222");
    }

    @Test
    void testParseEvent_SmartContractEvent_ShouldCreateSmartContractEvent() {
        // Given
        EventDto eventDto = new EventDto();
        eventDto.setType("SMART_CONTRACT_LOG");

        Map<String, Object> data = new HashMap<>();
        data.put("contract_identifier", "SP123.contract");
        data.put("topic", "print");
        Map<String, Object> value = new HashMap<>();
        value.put("message", "Hello World");
        data.put("value", value);
        eventDto.setData(data);

        StacksTransaction transaction = new StacksTransaction();

        // When
        TransactionEvent event = parser.parseEvent(eventDto, transaction, 0);

        // Then
        assertThat(event).isInstanceOf(SmartContractEvent.class);
        SmartContractEvent scEvent = (SmartContractEvent) event;
        assertThat(scEvent.getTopic()).isEqualTo("print");
        assertThat(scEvent.getValueDecoded()).isNotNull();
        assertThat(scEvent.getValueDecoded().get("message")).isEqualTo("Hello World");
    }

    @Test
    void testParseTransaction_WithExecutionCost_ShouldIncludeCostMetrics() {
        // Given
        StacksBlock block = new StacksBlock();
        TransactionDto txDto = createTestTransaction("0xtx999", "SP999", true);

        ExecutionCostDto costDto = new ExecutionCostDto();
        costDto.setReadCount(10L);
        costDto.setReadLength(1000L);
        costDto.setRuntime(5000L);
        costDto.setWriteCount(5L);
        costDto.setWriteLength(500L);
        txDto.getMetadata().setExecutionCost(costDto);

        // When
        StacksTransaction transaction = parser.parseTransaction(txDto, block);

        // Then
        assertThat(transaction.getExecutionCostReadCount()).isEqualTo(10L);
        assertThat(transaction.getExecutionCostReadLength()).isEqualTo(1000L);
        assertThat(transaction.getExecutionCostRuntime()).isEqualTo(5000L);
        assertThat(transaction.getExecutionCostWriteCount()).isEqualTo(5L);
        assertThat(transaction.getExecutionCostWriteLength()).isEqualTo(500L);
    }

    // ========== P2-2: Nonce Parsing Tests ==========

    @Test
    void testParseTransaction_WithNonce_ShouldParseCorrectly() {
        // Given
        StacksBlock block = new StacksBlock();
        TransactionDto txDto = createTestTransaction("0xtx123", "SP123", true);
        txDto.getMetadata().setNonce(42L);

        // When
        StacksTransaction transaction = parser.parseTransaction(txDto, block);

        // Then
        assertThat(transaction.getNonce()).isEqualTo(42L);
    }

    @Test
    void testParseTransaction_WithoutNonce_ShouldDefaultToZero() {
        // Given
        StacksBlock block = new StacksBlock();
        TransactionDto txDto = createTestTransaction("0xtx456", "SP456", true);
        txDto.getMetadata().setNonce(null); // Nonce not provided

        // When
        StacksTransaction transaction = parser.parseTransaction(txDto, block);

        // Then
        assertThat(transaction.getNonce()).isEqualTo(0L);
    }

    @Test
    void testParseTransaction_WithLargeNonce_ShouldHandleCorrectly() {
        // Given
        StacksBlock block = new StacksBlock();
        TransactionDto txDto = createTestTransaction("0xtx789", "SP789", true);
        txDto.getMetadata().setNonce(999999L);

        // When
        StacksTransaction transaction = parser.parseTransaction(txDto, block);

        // Then
        assertThat(transaction.getNonce()).isEqualTo(999999L);
    }

    // ========== P2-3: BlockMetadataDto New Fields Tests ==========

    @Test
    void testParseBlock_WithConsensusHash_ShouldParse() {
        // Given
        BlockEventDto blockEventDto = createTestBlockEvent(100L, "0xblock", "0xparent", 1234567890L);
        BlockMetadataDto metadata = new BlockMetadataDto();
        metadata.setConsensusHash("0x3c4e5f6a7b8c9d0e");
        blockEventDto.setMetadata(metadata);

        // When
        StacksBlock block = parser.parseBlock(blockEventDto);

        // Then - consensusHash not yet stored in StacksBlock entity (future enhancement)
        // This test ensures parsing doesn't fail when field is present
        assertThat(block).isNotNull();
        assertThat(block.getBlockHash()).isEqualTo("0xblock");
    }

    @Test
    void testParseBlock_WithParentBurnBlockFields_ShouldParse() {
        // Given
        BlockEventDto blockEventDto = createTestBlockEvent(100L, "0xblock", "0xparent", 1234567890L);
        BlockMetadataDto metadata = new BlockMetadataDto();
        metadata.setParentBurnBlockHash("0xparent_burn");
        metadata.setParentBurnBlockTime(1234567800L);
        blockEventDto.setMetadata(metadata);

        // When
        StacksBlock block = parser.parseBlock(blockEventDto);

        // Then - parent burn block fields not yet stored in entity (future enhancement)
        // This test ensures parsing doesn't fail when fields are present
        assertThat(block).isNotNull();
    }

    @Test
    void testParseBlock_WithAllNewMetadataFields_ShouldNotFail() {
        // Given
        BlockEventDto blockEventDto = createTestBlockEvent(100L, "0xblock", "0xparent", 1234567890L);
        BlockMetadataDto metadata = new BlockMetadataDto();
        metadata.setBurnBlockHeight(99L);
        metadata.setBurnBlockHash("0xburn");
        metadata.setBurnBlockTime(1234567800L);
        metadata.setMiner("SP2ABC");
        metadata.setConsensusHash("0xconsensus");
        metadata.setParentBurnBlockHash("0xparent_burn");
        metadata.setParentBurnBlockTime(1234567700L);
        blockEventDto.setMetadata(metadata);

        // When
        StacksBlock block = parser.parseBlock(blockEventDto);

        // Then - all fields parse without error
        assertThat(block).isNotNull();
        assertThat(block.getBurnBlockHeight()).isEqualTo(99L);
        assertThat(block.getBurnBlockHash()).isEqualTo("0xburn");
        assertThat(block.getMinerAddress()).isEqualTo("SP2ABC");
    }

    // ========== P2-4: Type-Safe Event Parsing Tests ==========

    @Test
    void testParseEvent_WithEventSuffix_ShouldUseFromWireFormat() {
        // Given - using "_EVENT" suffix (old format)
        EventDto eventDto = new EventDto();
        eventDto.setType("FT_TRANSFER_EVENT");
        Map<String, Object> data = new HashMap<>();
        data.put("asset_identifier", "STX");
        data.put("amount", "1000");
        data.put("sender", "SP111");
        data.put("recipient", "SP222");
        eventDto.setData(data);
        StacksTransaction transaction = new StacksTransaction();

        // When
        TransactionEvent event = parser.parseEvent(eventDto, transaction, 0);

        // Then - should parse correctly via EventType.fromWireFormat()
        assertThat(event).isInstanceOf(FTTransferEvent.class);
    }

    @Test
    void testParseEvent_WithoutEventSuffix_ShouldUseFromWireFormat() {
        // Given - without "_EVENT" suffix (new format)
        EventDto eventDto = new EventDto();
        eventDto.setType("FT_TRANSFER");
        Map<String, Object> data = new HashMap<>();
        data.put("asset_identifier", "STX");
        data.put("amount", "1000");
        data.put("sender", "SP111");
        data.put("recipient", "SP222");
        eventDto.setData(data);
        StacksTransaction transaction = new StacksTransaction();

        // When
        TransactionEvent event = parser.parseEvent(eventDto, transaction, 0);

        // Then - should parse correctly via EventType.fromWireFormat()
        assertThat(event).isInstanceOf(FTTransferEvent.class);
    }

    @Test
    void testParseEvent_UnknownType_ShouldReturnNull() {
        // Given
        EventDto eventDto = new EventDto();
        eventDto.setType("UNKNOWN_INVALID_TYPE");
        Map<String, Object> data = new HashMap<>();
        eventDto.setData(data);
        StacksTransaction transaction = new StacksTransaction();

        // When
        TransactionEvent event = parser.parseEvent(eventDto, transaction, 0);

        // Then - unknown types return null (logged as warning)
        assertThat(event).isNull();
    }

    // Helper methods

    private BlockEventDto createTestBlockEvent(Long height, String hash, String parentHash, Long timestamp) {
        BlockEventDto blockEvent = new BlockEventDto();

        BlockIdentifierDto identifier = new BlockIdentifierDto();
        identifier.setIndex(height);
        identifier.setHash(hash);
        blockEvent.setBlockIdentifier(identifier);

        if (parentHash != null) {
            BlockIdentifierDto parentIdentifier = new BlockIdentifierDto();
            parentIdentifier.setHash(parentHash);
            blockEvent.setParentBlockIdentifier(parentIdentifier);
        }

        blockEvent.setTimestamp(timestamp);

        return blockEvent;
    }

    private TransactionDto createTestTransaction(String txId, String sender, Boolean success) {
        TransactionDto txDto = new TransactionDto();

        TransactionIdentifierDto identifier = new TransactionIdentifierDto();
        identifier.setHash(txId);
        txDto.setTransactionIdentifier(identifier);

        TransactionMetadataDto metadata = new TransactionMetadataDto();
        metadata.setSender(sender);
        metadata.setSuccess(success);
        metadata.setFee("1000");

        PositionDto position = new PositionDto();
        position.setIndex(0);
        metadata.setPosition(position);

        txDto.setMetadata(metadata);

        return txDto;
    }
}
