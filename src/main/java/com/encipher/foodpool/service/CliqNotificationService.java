package com.encipher.foodpool.service;

import com.encipher.foodpool.model.Employee;
import com.encipher.foodpool.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CliqNotificationService {
    
    private final EmployeeRepository employeeRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${app.cliq.webhook-url:}")
    private String cliqWebhookUrl;
    
    @Value("${app.cliq.bot-token:}")
    private String cliqBotToken;
    
    @Value("${app.base-url:http://20.102.40.235:8888}")
    private String appBaseUrl;
    
    /**
     * Send notification when pool starts
     */
    public void notifyPoolStarted(int durationHours) {
        String message = "🍽️ *Food Pool Survey Started!*\n\n" +
                "The food pool is now open for registration.\n" +
                "Duration: *" + durationHours + " hours*\n\n" +
                "👉 [Register Now](" + appBaseUrl + "/pool)\n\n" +
                "Don't miss out on your meal! 🥗🍗";
        
        sendToAllEmployees(message);
    }
    
    /**
     * Send notification 1 hour before pool closes
     */
    public void notifyPoolClosingSoon() {
        String message = "⏰ *Food Pool Closing Soon!*\n\n" +
                "The food pool will close in *1 hour*.\n\n" +
                "If you haven't registered yet, do it now!\n" +
                "👉 [Register Now](" + appBaseUrl + "/pool)";
        
        sendToAllEmployees(message);
    }
    
    /**
     * Send notification when 50% food consumed
     */
    public void notify50PercentConsumed(long collected, long total) {
        String message = "🍽️ *Food Update - 50% Consumed*\n\n" +
                "Half of the food has been collected!\n" +
                "📊 " + collected + "/" + total + " meals collected\n\n" +
                "If you haven't had your lunch yet, *go and have it soon!* 🏃‍♂️";
        
        sendToAllEmployees(message);
    }
    
    /**
     * Send notification when 70% food consumed
     */
    public void notify70PercentConsumed(long collected, long total) {
        String message = "⚠️ *Food is About to Finish!*\n\n" +
                "70% of food has been collected!\n" +
                "📊 " + collected + "/" + total + " meals collected\n\n" +
                "*Go and have your lunch NOW!* 🏃‍♂️💨";
        
        sendToAllEmployees(message);
    }
    
    /**
     * Send notification when pool is closed
     */
    public void notifyPoolClosed(long vegCount, long nonvegCount) {
        String message = "🔒 *Food Pool Closed*\n\n" +
                "Today's food pool has ended.\n\n" +
                "📊 Final Count:\n" +
                "🥗 Veg: " + vegCount + "\n" +
                "🍗 Non-Veg: " + nonvegCount + "\n" +
                "📌 Total: " + (vegCount + nonvegCount);
        
        sendToAllEmployees(message);
    }
    
    /**
     * Send message to all active employees via Cliq
     */
    private void sendToAllEmployees(String message) {
        if (cliqWebhookUrl == null || cliqWebhookUrl.isEmpty()) {
            log.warn("Cliq webhook URL not configured. Message: {}", message);
            sendViaCliqChannel(message);
            return;
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(cliqWebhookUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Cliq notification sent successfully");
            } else {
                log.error("Cliq notification failed: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending Cliq notification: {}", e.getMessage());
        }
    }
    
    /**
     * Send to Cliq channel using incoming webhook
     */
    private void sendViaCliqChannel(String message) {
        // This is a fallback - log the message for now
        log.info("CLIQ NOTIFICATION (webhook not configured): {}", message);
    }
    
    /**
     * Send personal message to specific employee
     */
    public void sendToEmployee(Employee employee, String message) {
        if (employee.getEmail() == null) return;
        
        // For personal messages, you'd use Cliq Bot API
        // For now, we'll rely on channel/webhook notifications
        log.debug("Personal notification to {}: {}", employee.getEmail(), message);
    }
}

