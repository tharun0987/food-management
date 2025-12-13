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
    
    @Value("${app.base-url:http://food.management.encipherhealth.com}")
    private String appBaseUrl;
    
    /**
     * Send notification when pool starts
     */
    public void notifyPoolStarted(int durationHours) {
        String message = "🍽️ *Food Pool Survey Started!*\n\n" +
                "The food pool is now open for registration.\n" +
                "⏱️ Duration: *" + durationHours + " hours*\n\n" +
                "👉 Register Now: " + appBaseUrl + "/pool\n\n" +
                "Don't miss out on your meal! 🥗🍗";
        
        sendNotification(message);
    }
    
    /**
     * Send notification 1 hour before pool closes
     */
    public void notifyPoolClosingSoon() {
        String message = "⏰ *Food Pool Closing Soon!*\n\n" +
                "The food pool will close in *1 hour*.\n\n" +
                "If you haven't registered yet, do it now!\n" +
                "👉 Register: " + appBaseUrl + "/pool";
        
        sendNotification(message);
    }
    
    /**
     * Send notification when 50% food consumed
     */
    public void notify50PercentConsumed(long collected, long total) {
        String message = "🍽️ *Food Update - 50% Consumed*\n\n" +
                "Half of the food has been collected!\n" +
                "📊 " + collected + "/" + total + " meals collected\n\n" +
                "If you haven't had your lunch yet, *go and have it soon!* 🏃‍♂️";
        
        sendNotification(message);
    }
    
    /**
     * Send notification when 70% food consumed
     */
    public void notify70PercentConsumed(long collected, long total) {
        String message = "⚠️ *Food is About to Finish!*\n\n" +
                "70% of food has been collected!\n" +
                "📊 " + collected + "/" + total + " meals collected\n\n" +
                "*Go and have your lunch NOW!* 🏃‍♂️💨";
        
        sendNotification(message);
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
        
        sendNotification(message);
    }
    
    /**
     * Send message via Cliq webhook
     */
    private void sendNotification(String message) {
        if (cliqWebhookUrl == null || cliqWebhookUrl.isEmpty()) {
            log.warn("Cliq webhook URL not configured. Message not sent: {}", message);
            return;
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Zoho Cliq webhook expects "text" field for the message
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            
            // Optional: Add bot name
            Map<String, Object> bot = new HashMap<>();
            bot.put("name", "Food Pool Bot");
            bot.put("image", "https://img.icons8.com/color/96/meal.png");
            payload.put("bot", bot);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            log.info("Sending Cliq notification to webhook...");
            ResponseEntity<String> response = restTemplate.postForEntity(cliqWebhookUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✓ Cliq notification sent successfully");
            } else {
                log.error("✗ Cliq notification failed: {} - {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("✗ Error sending Cliq notification: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Test the webhook connection
     */
    public boolean testWebhook() {
        if (cliqWebhookUrl == null || cliqWebhookUrl.isEmpty()) {
            return false;
        }
        
        try {
            String testMessage = "🔔 *Food Pool Bot Connected!*\n\nThis is a test message to confirm the webhook is working.";
            sendNotification(testMessage);
            return true;
        } catch (Exception e) {
            log.error("Webhook test failed: {}", e.getMessage());
            return false;
        }
    }
}
