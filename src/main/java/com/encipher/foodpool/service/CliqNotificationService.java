package com.encipher.foodpool.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CliqNotificationService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${app.cliq.channel-url:https://cliq.zoho.in/company/60023432224/api/v2/channelsbyname/foodtest/message}")
    private String cliqChannelUrl;
    
    @Value("${app.cliq.api-token:1001.df6a6e9f3491348c1dc1ff8f876f655b.ac81841f303707e3ab74160a50d8d240}")
    private String cliqApiToken;
    
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
     * Send message to Cliq channel
     */
    private void sendNotification(String message) {
        if (cliqApiToken == null || cliqApiToken.isEmpty()) {
            log.warn("Cliq API token not configured. Message not sent.");
            return;
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Zoho-oauthtoken " + cliqApiToken);
            
            // Zoho Cliq channel message format
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            
            // Optional: Add card for better formatting
            Map<String, Object> card = new HashMap<>();
            card.put("title", "🍽️ Food Pool Bot");
            card.put("theme", "modern-inline");
            payload.put("card", card);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            log.info("Sending Cliq notification to channel: foodtest");
            ResponseEntity<String> response = restTemplate.postForEntity(cliqChannelUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✓ Cliq notification sent successfully to channel");
            } else {
                log.error("✗ Cliq notification failed: {} - {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("✗ Error sending Cliq notification: {}", e.getMessage());
            // Log full error for debugging
            log.debug("Full error: ", e);
        }
    }
    
    /**
     * Test the notification
     */
    public boolean testNotification() {
        try {
            String testMessage = "🔔 *Food Pool Bot Connected!*\n\nThis is a test message to confirm notifications are working.";
            sendNotification(testMessage);
            return true;
        } catch (Exception e) {
            log.error("Test notification failed: {}", e.getMessage());
            return false;
        }
    }
}
