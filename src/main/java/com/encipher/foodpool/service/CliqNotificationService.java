package com.encipher.foodpool.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CliqNotificationService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${app.cliq.webhook-url:}")
    private String webhookUrl;
    
    @Value("${app.cliq.bot-message-url:}")
    private String botMessageUrl;
    
    @Value("${app.base-url:https://food.management.encipherhealth.com}")
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
    
    // ============== INTERACTIVE NOTIFICATIONS ==============
    
    /**
     * Send pool started notification with interactive vote buttons
     */
    public void notifyPoolStartedWithButtons(int durationHours, LocalDate foodDate) {
        String dateStr = foodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"));
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "[FOOD POOL] Survey Started for " + dateStr);
        
        // Card with buttons
        Map<String, Object> card = new HashMap<>();
        card.put("title", "Vote for Your Meal");
        card.put("theme", "modern-inline");
        payload.put("card", card);
        
        // Buttons
        List<Map<String, Object>> buttons = new ArrayList<>();
        
        Map<String, Object> vegButton = new HashMap<>();
        vegButton.put("label", "VEG");
        vegButton.put("type", "+");
        vegButton.put("action", Map.of(
            "type", "invoke.function",
            "data", Map.of("food", "veg")
        ));
        buttons.add(vegButton);
        
        Map<String, Object> nonvegButton = new HashMap<>();
        nonvegButton.put("label", "NON-VEG");
        nonvegButton.put("type", "+");
        nonvegButton.put("action", Map.of(
            "type", "invoke.function",
            "data", Map.of("food", "nonveg")
        ));
        buttons.add(nonvegButton);
        
        Map<String, Object> webButton = new HashMap<>();
        webButton.put("label", "Vote Online");
        webButton.put("type", "");
        webButton.put("action", Map.of(
            "type", "open.url",
            "data", Map.of("web", appBaseUrl + "/pool")
        ));
        buttons.add(webButton);
        
        payload.put("buttons", buttons);
        
        sendRichNotification(payload);
    }
    
    /**
     * Send collection acknowledgement to user
     */
    public void sendCollectionAcknowledge(String employeeName) {
        String message = "[FOOD POOL] Meal Collected\n\n" +
                "Hi " + employeeName + "!\n\n" +
                "Your meal has been collected successfully.\n" +
                "Enjoy your food! Thank you for participating.";
        
        sendNotification(message);
    }
    
    /**
     * Send one hour warning notification
     */
    public void sendOneHourWarning(LocalDate foodDate) {
        String dateStr = foodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd"));
        String message = "[FOOD POOL] 1 Hour Left!\n\n" +
                "Only 1 hour remaining to vote for food on " + dateStr + "!\n\n" +
                "If you haven't voted yet, do it NOW!\n\n" +
                "Vote: " + appBaseUrl + "/pool";
        
        sendNotification(message);
    }
    
    /**
     * Send reminder to users who haven't voted
     */
    public void sendVoteReminderToNonVoters(List<String> emails) {
        String message = "[FOOD POOL] Reminder to Vote\n\n" +
                "You haven't voted for tomorrow's food yet.\n" +
                "The survey will close soon!\n\n" +
                "Vote Now: " + appBaseUrl + "/pool";
        
        // This would need integration with Zoho Cliq personal messaging
        // For now, send to channel
        sendNotification(message);
    }
    
    /**
     * Send grace request notification to admins
     */
    public void notifyGraceRequest(String employeeName, LocalDate foodDate, String foodType) {
        String message = "[FOOD POOL] Grace Request\n\n" +
                "New late vote request received:\n" +
                "Employee: " + employeeName + "\n" +
                "Food Date: " + foodDate + "\n" +
                "Choice: " + foodType.toUpperCase() + "\n\n" +
                "Review in admin dashboard: " + appBaseUrl + "/admin";
        
        sendNotification(message);
    }
    
    /**
     * Send grace request approval notification
     */
    public void notifyGraceApproved(String employeeName, LocalDate foodDate) {
        String message = "[FOOD POOL] Grace Request Approved\n\n" +
                "Good news, " + employeeName + "!\n" +
                "Your late vote request for " + foodDate + " has been approved.\n" +
                "Your meal has been added to the order.";
        
        sendNotification(message);
    }
    
    /**
     * Send grace request rejection notification
     */
    public void notifyGraceRejected(String employeeName, LocalDate foodDate, String reason) {
        String message = "[FOOD POOL] Grace Request Rejected\n\n" +
                "Hi " + employeeName + ",\n" +
                "Your late vote request for " + foodDate + " has been rejected.\n" +
                "Reason: " + reason + "\n\n" +
                "Please make sure to vote on time for future food pools.";
        
        sendNotification(message);
    }
    
    // ============== CORE SEND METHODS ==============
    
    private void sendNotification(String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Cliq webhook URL not configured");
            throw new RuntimeException("Webhook URL not configured");
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Bot incoming webhook format
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            log.info("Sending notification to Cliq bot...");
            
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Notification sent successfully");
            } else {
                log.error("Notification failed: {} - {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage());
            throw new RuntimeException("Failed to send notification: " + e.getMessage());
        }
    }
    
    private void sendRichNotification(Map<String, Object> payload) {
        String url = botMessageUrl != null && !botMessageUrl.isEmpty() ? botMessageUrl : webhookUrl;
        
        if (url == null || url.isEmpty()) {
            log.warn("Cliq webhook URL not configured");
            throw new RuntimeException("Webhook URL not configured");
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            log.info("Sending rich notification to Cliq...");
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Rich notification sent successfully");
            } else {
                log.error("Rich notification failed: {} - {}", response.getStatusCode(), response.getBody());
                // Fallback to simple notification
                sendNotification(payload.getOrDefault("text", "Notification").toString());
            }
        } catch (Exception e) {
            log.error("Error sending rich notification: {}, falling back to simple", e.getMessage());
            // Fallback to simple notification
            sendNotification(payload.getOrDefault("text", "Notification").toString());
        }
    }
}
