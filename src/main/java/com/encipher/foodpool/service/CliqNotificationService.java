package com.encipher.foodpool.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CliqNotificationService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Channel API endpoint
    @Value("${app.cliq.channel-url:https://cliq.zoho.in/company/60023432224/api/v2/channelsbyname/foodtest/message}")
    private String cliqChannelUrl;
    
    // OAuth token
    @Value("${app.cliq.oauth-token:1001.df6a6e9f3491348c1dc1ff8f876f655b.ac81841f303707e3ab74160a50d8d240}")
    private String oauthToken;
    
    @Value("${app.base-url:http://food.management.encipherhealth.com}")
    private String appBaseUrl;
    
    // ============== AUTO NOTIFICATIONS ==============
    
    public void notifyPoolStarted(int durationHours, LocalDate foodDate) {
        String dateStr = foodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"));
        String message = "[FOOD POOL] Survey Started\n\n" +
                "Register now for food on " + dateStr + "\n" +
                "Survey closes in: " + durationHours + " hours\n\n" +
                "Vote Now: " + appBaseUrl + "/pool";
        
        sendNotification(message);
    }
    
    public void notifyPoolStarted(int durationHours) {
        notifyPoolStarted(durationHours, LocalDate.now().plusDays(1));
    }
    
    public void notifyPoolClosingSoon() {
        String message = "[FOOD POOL] Closing Soon\n\n" +
                "Only 1 hour left to register for tomorrow's food!\n\n" +
                "If you haven't voted yet, do it now!\n" +
                "Vote: " + appBaseUrl + "/pool";
        
        sendNotification(message);
    }
    
    public void notify50PercentConsumed(long collected, long total) {
        String message = "[FOOD POOL] 50% Collected\n\n" +
                "Half of the food has been collected!\n" +
                "Status: " + collected + "/" + total + " meals collected\n\n" +
                "If you haven't had your meal yet, come and get it soon!";
        
        sendNotification(message);
    }
    
    public void notify70PercentConsumed(long collected, long total) {
        String message = "[FOOD POOL] 70% Collected - Hurry!\n\n" +
                "Most of the food has been collected!\n" +
                "Status: " + collected + "/" + total + " meals collected\n\n" +
                "Come and collect your food NOW!";
        
        sendNotification(message);
    }
    
    public void notifyPoolClosed(long vegCount, long nonvegCount) {
        String message = "[FOOD POOL] Voting Closed\n\n" +
                "Final Count:\n" +
                "- Veg: " + vegCount + "\n" +
                "- Non-Veg: " + nonvegCount + "\n" +
                "- Total: " + (vegCount + nonvegCount) + "\n\n" +
                "Food will be served soon!";
        
        sendNotification(message);
    }
    
    // ============== MANUAL NOTIFICATIONS ==============
    
    public void sendParticipateReminder(LocalDate foodDate) {
        String dateStr = foodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"));
        String message = "[REMINDER] Vote for Your Meal\n\n" +
                "Don't forget to register for food on " + dateStr + "\n\n" +
                "Veg or Non-Veg - Make your choice!\n\n" +
                "Vote Now: " + appBaseUrl + "/pool\n\n" +
                "Survey closes soon!";
        
        sendNotification(message);
    }
    
    public void sendEatReminder(long remaining) {
        String message = "[FOOD POOL] Food Has Arrived!\n\n" +
                "Your meal is ready and waiting!\n" +
                "Remaining meals: " + remaining + "\n\n" +
                "Come to the cafeteria and collect your food!\n\n" +
                "Scan QR: " + appBaseUrl + "/scan";
        
        sendNotification(message);
    }
    
    public void sendLastCallReminder() {
        String message = "[LAST CALL] Pool Closing Soon!\n\n" +
                "Only a few minutes left to vote!\n\n" +
                "If you want food tomorrow, VOTE NOW!\n\n" +
                "Link: " + appBaseUrl + "/pool";
        
        sendNotification(message);
    }
    
    public void sendCustomNotification(String customMessage) {
        String message = "[FOOD POOL] Announcement\n\n" + customMessage;
        sendNotification(message);
    }
    
    // ============== CORE SEND METHOD ==============
    
    private void sendNotification(String message) {
        if (oauthToken == null || oauthToken.isEmpty()) {
            log.warn("Cliq OAuth token not configured. Message not sent.");
            return;
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Zoho-oauthtoken " + oauthToken);
            
            // Zoho Cliq channel message format
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            log.info("Sending Cliq notification to channel: foodtest");
            log.debug("URL: {}", cliqChannelUrl);
            
            ResponseEntity<String> response = restTemplate.postForEntity(cliqChannelUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Cliq notification sent successfully");
            } else {
                log.error("Cliq notification failed: {} - {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Notification failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending Cliq notification: {}", e.getMessage());
            throw new RuntimeException("Failed to send notification: " + e.getMessage());
        }
    }
}
