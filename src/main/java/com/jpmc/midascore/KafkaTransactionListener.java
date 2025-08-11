package com.jpmc.midascore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class KafkaTransactionListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionListener.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TransactionService transactionService;

    public KafkaTransactionListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @KafkaListener(
            topics = "${general.kafka-topic}",
            groupId = "midas-core"
    )
    public void onMessage(@Payload String json) {
        try {
            Transaction tx = MAPPER.readValue(json, Transaction.class);

            // (Optional) quick visibility while running tests
            log.info("Consumed tx: senderId={}, recipientId={}, amount={}",
                    tx.getSenderId(), tx.getRecipientId(), tx.getAmount());

            transactionService.process(tx);
        } catch (Exception e) {
            log.error("Failed to parse or process message: {}", json, e);
        }
    }
}