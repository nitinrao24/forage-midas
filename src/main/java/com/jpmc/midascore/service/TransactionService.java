package com.jpmc.midascore.service;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final UserRecordRepository userRepo;
    private final TransactionRecordRepository txRepo;
    private final RestTemplate restTemplate;

    @Value("${incentive.api.url:http://localhost:8080/incentive}")
    private String incentiveUrl;

    public TransactionService(UserRecordRepository userRepo,
                              TransactionRecordRepository txRepo,
                              RestTemplate restTemplate) {
        this.userRepo = userRepo;
        this.txRepo = txRepo;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public void process(Transaction dto) {
        if (dto == null) return;

        long senderId = dto.getSenderId();
        long recipientId = dto.getRecipientId();
        float amount = dto.getAmount();

        if (amount <= 0f) {
            log.debug("Discarding tx: non-positive amount {}", amount);
            return;
        }

        Optional<UserRecord> senderOpt = userRepo.findById(senderId);
        Optional<UserRecord> recipientOpt = userRepo.findById(recipientId);

        if (senderOpt.isEmpty() || recipientOpt.isEmpty()) {
            log.debug("Discarding tx: sender or recipient not found (s={}, r={})", senderId, recipientId);
            return;
        }

        UserRecord sender = senderOpt.get();
        UserRecord recipient = recipientOpt.get();

        if (sender.getBalance() < amount) {
            log.debug("Discarding tx: insufficient funds (senderId={}, balance={}, amount={})",
                    senderId, sender.getBalance(), amount);
            return;
        }

        // --- Incentive API with tiny retry (to survive jar startup race) ---
        float inc = 0f;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Transaction> entity = new HttpEntity<>(dto, headers);

        int attempts = 0;
        while (attempts < 3) {
            attempts++;
            try {
                ResponseEntity<Incentive> resp = restTemplate.postForEntity(incentiveUrl, entity, Incentive.class);
                Incentive body = resp.getBody();
                inc = (body != null) ? Math.max(0f, body.getAmount()) : 0f;
                break;
            } catch (Exception ex) {
                log.warn("Incentive API call attempt {} failed ({}). Will{} retry. URL={}, dto={}",
                        attempts, ex.getMessage(), (attempts < 3 ? "" : " not"), incentiveUrl, dto);
                if (attempts < 3) {
                    try { Thread.sleep(200L); } catch (InterruptedException ignored) {}
                }
            }
        }

        // apply balances
        sender.setBalance(sender.getBalance() - amount);             // do NOT deduct incentive
        recipient.setBalance(recipient.getBalance() + amount + inc); // add incentive to recipient
        userRepo.save(sender);
        userRepo.save(recipient);

        // persist transaction record (with incentive)
        TransactionRecord rec = new TransactionRecord(sender, recipient, amount, inc);
        txRepo.save(rec);

        log.debug("Recorded tx: sender={} recipient={} amount={} incentive={}", senderId, recipientId, amount, inc);
    }
}
