package com.stacksmonitoring.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stacksmonitoring.api.dto.webhook.*;
import com.stacksmonitoring.application.usecase.ProcessChainhookPayloadUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for WebhookController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProcessChainhookPayloadUseCase processChainhookPayloadUseCase;

    @Test
    void testHandleChainhookWebhook_WithValidPayload_ShouldReturnAccepted() throws Exception {
        // Given
        ChainhookPayloadDto payload = createTestChainhookPayload();

        ProcessChainhookPayloadUseCase.ProcessingResult result = new ProcessChainhookPayloadUseCase.ProcessingResult();
        result.success = true;
        result.applyCount = 1;
        result.rollbackCount = 0;

        when(processChainhookPayloadUseCase.processPayload(any())).thenReturn(result);

        // When & Then
        mockMvc.perform(post("/api/v1/webhook/chainhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.message").exists());

        // Verify async processing was triggered (may take time to execute)
        // Note: We can't easily verify async execution in this test
    }

    @Test
    void testHandleChainhookWebhook_WithMinimalPayload_ShouldAccept() throws Exception {
        // Given
        ChainhookPayloadDto payload = new ChainhookPayloadDto();
        payload.setApply(new ArrayList<>());
        payload.setRollback(new ArrayList<>());

        ProcessChainhookPayloadUseCase.ProcessingResult result = new ProcessChainhookPayloadUseCase.ProcessingResult();
        result.success = true;

        when(processChainhookPayloadUseCase.processPayload(any())).thenReturn(result);

        // When & Then
        mockMvc.perform(post("/api/v1/webhook/chainhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void testHandleChainhookWebhook_WithComplexPayload_ShouldProcess() throws Exception {
        // Given
        ChainhookPayloadDto payload = createComplexTestPayload();

        ProcessChainhookPayloadUseCase.ProcessingResult result = new ProcessChainhookPayloadUseCase.ProcessingResult();
        result.success = true;
        result.applyCount = 2;
        result.rollbackCount = 1;

        when(processChainhookPayloadUseCase.processPayload(any())).thenReturn(result);

        // When & Then
        mockMvc.perform(post("/api/v1/webhook/chainhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void testWebhookHealth_ShouldReturnUp() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/webhook/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("webhook"));
    }

    @Test
    void testHandleChainhookWebhook_WithInvalidJson_ShouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/webhook/chainhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .andExpect(status().isBadRequest());
    }

    // Helper methods

    private ChainhookPayloadDto createTestChainhookPayload() {
        ChainhookPayloadDto payload = new ChainhookPayloadDto();

        // Chainhook metadata
        ChainhookMetadataDto metadata = new ChainhookMetadataDto();
        metadata.setUuid("test-chainhook-uuid");
        payload.setChainhook(metadata);

        // Apply events
        List<BlockEventDto> applyEvents = new ArrayList<>();
        BlockEventDto blockEvent = createBlockEvent(100L, "0xblock100");
        applyEvents.add(blockEvent);
        payload.setApply(applyEvents);

        // No rollback events
        payload.setRollback(new ArrayList<>());

        return payload;
    }

    private ChainhookPayloadDto createComplexTestPayload() {
        ChainhookPayloadDto payload = new ChainhookPayloadDto();

        // Chainhook metadata
        ChainhookMetadataDto metadata = new ChainhookMetadataDto();
        metadata.setUuid("complex-test-uuid");
        payload.setChainhook(metadata);

        // Apply events with transactions
        List<BlockEventDto> applyEvents = new ArrayList<>();

        BlockEventDto block1 = createBlockEvent(100L, "0xblock100");
        block1.setTransactions(List.of(
            createTransaction("0xtx1", "SP123"),
            createTransaction("0xtx2", "SP456")
        ));
        applyEvents.add(block1);

        BlockEventDto block2 = createBlockEvent(101L, "0xblock101");
        block2.setTransactions(List.of(
            createTransaction("0xtx3", "SP789")
        ));
        applyEvents.add(block2);

        payload.setApply(applyEvents);

        // Rollback events
        List<BlockEventDto> rollbackEvents = new ArrayList<>();
        rollbackEvents.add(createBlockEvent(99L, "0xblock99"));
        payload.setRollback(rollbackEvents);

        return payload;
    }

    private BlockEventDto createBlockEvent(Long height, String hash) {
        BlockEventDto blockEvent = new BlockEventDto();

        BlockIdentifierDto identifier = new BlockIdentifierDto();
        identifier.setIndex(height);
        identifier.setHash(hash);
        blockEvent.setBlockIdentifier(identifier);

        BlockIdentifierDto parentIdentifier = new BlockIdentifierDto();
        parentIdentifier.setHash("0xparent" + (height - 1));
        blockEvent.setParentBlockIdentifier(parentIdentifier);

        blockEvent.setTimestamp(1234567890L);

        BlockMetadataDto metadata = new BlockMetadataDto();
        metadata.setBurnBlockHeight(height - 1);
        metadata.setBurnBlockHash("0xburn" + (height - 1));
        blockEvent.setMetadata(metadata);

        blockEvent.setTransactions(new ArrayList<>());

        return blockEvent;
    }

    private TransactionDto createTransaction(String txId, String sender) {
        TransactionDto txDto = new TransactionDto();

        TransactionIdentifierDto identifier = new TransactionIdentifierDto();
        identifier.setHash(txId);
        txDto.setTransactionIdentifier(identifier);

        TransactionMetadataDto metadata = new TransactionMetadataDto();
        metadata.setSender(sender);
        metadata.setSuccess(true);
        metadata.setFee("1000");

        PositionDto position = new PositionDto();
        position.setIndex(0);
        metadata.setPosition(position);

        // Add transaction kind
        TransactionKindDto kind = new TransactionKindDto();
        kind.setType("ContractCall");
        Map<String, Object> data = new HashMap<>();
        data.put("contract_identifier", "SP123.contract");
        data.put("method", "transfer");
        kind.setData(data);
        metadata.setKind(kind);

        // Add receipt with empty events
        TransactionReceiptDto receipt = new TransactionReceiptDto();
        receipt.setEvents(new ArrayList<>());
        metadata.setReceipt(receipt);

        txDto.setMetadata(metadata);

        return txDto;
    }
}
