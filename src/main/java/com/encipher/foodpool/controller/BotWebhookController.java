package com.encipher.foodpool.controller;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for handling Zoho Cliq bot webhook requests.
 * This handles votes submitted through the bot interface and AI-powered chat.
 */
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@Slf4j
public class BotWebhookController {
    
    private final EmployeeService employeeService;
    private final FoodPoolService foodPoolService;
    private final CliqNotificationService cliqNotificationService;
    private final AiChatService aiChatService;
    
    @Value("${app.cliq.bot-webhook-secret:}")
    private String webhookSecret;
    
    /**
     * Handle vote from bot button click
     * Expected payload: { "email": "user@email.com", "food": "veg|nonveg" }
     */
    @PostMapping("/vote")
    public ResponseEntity<?> handleBotVote(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Bot vote received: {}", payload);
            
            // Extract user email from payload
            String email = extractEmail(payload);
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "text", "Unable to identify user. Please vote through the web portal."
                ));
            }
            
            // Extract food choice
            String foodType = extractFoodType(payload);
            if (foodType == null || (!foodType.equals("veg") && !foodType.equals("nonveg"))) {
                return ResponseEntity.badRequest().body(Map.of(
                    "text", "Invalid food choice. Please select Veg or Non-Veg."
                ));
            }
            
            // Find employee
            Employee employee = employeeService.findByEmail(email).orElse(null);
            if (employee == null) {
                return ResponseEntity.ok(Map.of(
                    "text", "You are not registered in the system. Please contact admin."
                ));
            }
            
            // Check if pool is open
            if (!foodPoolService.isPoolOpen()) {
                return ResponseEntity.ok(Map.of(
                    "text", "The food survey is currently closed. Please wait for the next survey."
                ));
            }
            
            // Register vote
            FoodPool pool = foodPoolService.registerPool(employee, foodType);
            
            String message = String.format(
                "Vote recorded!\n\nName: %s\nChoice: %s\nFood Date: %s\n\nThank you for participating!",
                employee.getName(),
                foodType.toUpperCase(),
                pool.getFoodDate()
            );
            
            return ResponseEntity.ok(Map.of("text", message));
            
        } catch (Exception e) {
            log.error("Error processing bot vote: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "text", "Error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Handle slash commands from the bot
     * Commands: /foodpool status, /foodpool vote veg, /foodpool vote nonveg
     */
    @PostMapping("/command")
    public ResponseEntity<?> handleBotCommand(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Bot command received: {}", payload);
            
            String email = extractEmail(payload);
            String command = extractCommand(payload);
            
            if (email == null || email.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "text", "Unable to identify user."
                ));
            }
            
            Employee employee = employeeService.findByEmail(email).orElse(null);
            if (employee == null) {
                return ResponseEntity.ok(Map.of(
                    "text", "You are not registered in the system."
                ));
            }
            
            // Parse command
            if (command == null || command.isEmpty() || command.equals("help")) {
                return ResponseEntity.ok(Map.of(
                    "text", getHelpMessage()
                ));
            }
            
            if (command.equals("status")) {
                return handleStatusCommand(employee);
            }
            
            if (command.startsWith("vote ")) {
                String foodType = command.substring(5).trim().toLowerCase();
                return handleVoteCommand(employee, foodType);
            }
            
            return ResponseEntity.ok(Map.of(
                "text", "Unknown command. Type 'help' for available commands."
            ));
            
        } catch (Exception e) {
            log.error("Error processing bot command: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "text", "Error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Webhook for button actions from Zoho Cliq
     */
    @PostMapping("/action")
    public ResponseEntity<?> handleButtonAction(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Bot action received: {}", payload);
            
            // Extract action data
            @SuppressWarnings("unchecked")
            Map<String, Object> actionData = (Map<String, Object>) payload.get("data");
            if (actionData == null) {
                return ResponseEntity.ok(Map.of("text", "Invalid action data"));
            }
            
            String food = (String) actionData.get("food");
            String email = extractEmail(payload);
            
            if (food == null || email == null) {
                return ResponseEntity.ok(Map.of("text", "Missing required data"));
            }
            
            // Process the vote
            Employee employee = employeeService.findByEmail(email).orElse(null);
            if (employee == null) {
                return ResponseEntity.ok(Map.of("text", "User not found"));
            }
            
            if (!foodPoolService.isPoolOpen()) {
                return ResponseEntity.ok(Map.of("text", "Survey is closed"));
            }
            
            FoodPool pool = foodPoolService.registerPool(employee, food);
            
            return ResponseEntity.ok(Map.of(
                "text", String.format("Vote recorded: %s for %s", food.toUpperCase(), pool.getFoodDate())
            ));
            
        } catch (Exception e) {
            log.error("Error processing button action: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("text", "Error: " + e.getMessage()));
        }
    }
    
    // ============== HELPER METHODS ==============
    
    private String extractEmail(Map<String, Object> payload) {
        // Try different paths where email might be
        if (payload.containsKey("email")) {
            return (String) payload.get("email");
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) payload.get("user");
        if (user != null && user.containsKey("email")) {
            return (String) user.get("email");
        }
        
        // Zoho Cliq specific paths
        @SuppressWarnings("unchecked")
        Map<String, Object> access = (Map<String, Object>) payload.get("access");
        if (access != null && access.containsKey("email")) {
            return (String) access.get("email");
        }
        
        return null;
    }
    
    private String extractFoodType(Map<String, Object> payload) {
        if (payload.containsKey("food")) {
            return ((String) payload.get("food")).toLowerCase();
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data != null && data.containsKey("food")) {
            return ((String) data.get("food")).toLowerCase();
        }
        
        return null;
    }
    
    private String extractCommand(Map<String, Object> payload) {
        if (payload.containsKey("command")) {
            return ((String) payload.get("command")).toLowerCase().trim();
        }
        
        if (payload.containsKey("text")) {
            return ((String) payload.get("text")).toLowerCase().trim();
        }
        
        return "";
    }
    
    private ResponseEntity<?> handleStatusCommand(Employee employee) {
        MenuConfig menu = foodPoolService.getTodayMenu();
        LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
        
        boolean poolOpen = menu.isPoolOpen();
        boolean hasVoted = foodPoolService.hasPooledForFoodDate(employee.getEmployeeId(), foodDate);
        
        String status;
        if (!poolOpen) {
            status = "Food survey is currently CLOSED.";
        } else if (hasVoted) {
            String choice = foodPoolService.getPoolForFoodDate(employee.getEmployeeId(), foodDate)
                    .map(FoodPool::getFoodType)
                    .map(String::toUpperCase)
                    .orElse("UNKNOWN");
            status = String.format("You have voted: %s\nFood Date: %s\n\nYou can change your choice while survey is open.", 
                    choice, foodDate);
        } else {
            status = String.format("Survey is OPEN!\nFood Date: %s\n\nYou haven't voted yet. Reply with:\n- vote veg\n- vote nonveg", 
                    foodDate);
        }
        
        return ResponseEntity.ok(Map.of("text", status));
    }
    
    private ResponseEntity<?> handleVoteCommand(Employee employee, String foodType) throws Exception {
        if (!foodType.equals("veg") && !foodType.equals("nonveg")) {
            return ResponseEntity.ok(Map.of(
                "text", "Invalid choice. Use: vote veg OR vote nonveg"
            ));
        }
        
        if (!foodPoolService.isPoolOpen()) {
            return ResponseEntity.ok(Map.of(
                "text", "Survey is currently closed."
            ));
        }
        
        FoodPool pool = foodPoolService.registerPool(employee, foodType);
        
        return ResponseEntity.ok(Map.of(
            "text", String.format("Vote recorded: %s\nFood Date: %s\n\nThank you!", 
                    foodType.toUpperCase(), pool.getFoodDate())
        ));
    }
    
    private String getHelpMessage() {
        return "Food Pool Bot Commands:\n\n" +
               "- status : Check your current status and survey info\n" +
               "- vote veg : Vote for vegetarian meal\n" +
               "- vote nonveg : Vote for non-vegetarian meal\n" +
               "- help : Show this help message\n\n" +
               "Or just chat with me naturally - I'm powered by AI!";
    }
    
    /**
     * AI-powered chat endpoint - handles natural language conversations
     * This is the main entry point for the Zoho Cliq Message Handler
     */
    @PostMapping("/chat")
    public ResponseEntity<?> handleChat(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Bot chat received: {}", payload);
            
            String message = extractMessage(payload);
            String email = extractEmail(payload);
            String userName = extractUserName(payload);
            
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.ok(Map.of("text", "I didn't catch that. What did you say?"));
            }
            
            // Get AI response
            String aiResponse = aiChatService.chat(message, userName != null ? userName : "friend");
            
            // Check if AI detected a command
            Optional<Map<String, String>> actionOpt = aiChatService.parseAction(aiResponse);
            
            if (actionOpt.isPresent()) {
                Map<String, String> action = actionOpt.get();
                String actionType = action.get("action");
                
                // Find employee for command execution
                Employee employee = null;
                if (email != null && !email.isEmpty()) {
                    employee = employeeService.findByEmail(email).orElse(null);
                }
                
                switch (actionType) {
                    case "vote":
                        if (employee == null) {
                            return ResponseEntity.ok(Map.of("text", 
                                "I'd love to vote for you, but I can't find you in the system. Are you sure you're registered?"));
                        }
                        String foodType = action.get("food");
                        return handleVoteWithFlair(employee, foodType);
                        
                    case "status":
                        if (employee == null) {
                            return ResponseEntity.ok(Map.of("text", 
                                "Can't check status for a ghost! You're not in the system. Talk to admin."));
                        }
                        return handleStatusWithFlair(employee);
                        
                    case "help":
                        return ResponseEntity.ok(Map.of("text", getHelpMessageWithFlair()));
                        
                    default:
                        break;
                }
            }
            
            // Return AI's conversational response
            return ResponseEntity.ok(Map.of("text", aiResponse));
            
        } catch (Exception e) {
            log.error("Error in chat: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("text", 
                "My circuits got a bit fried there. Try again? Or maybe poke the dev team."));
        }
    }
    
    /**
     * Handle reminder responses - when user replies to a reminder notification
     */
    @PostMapping("/reminder-response")
    public ResponseEntity<?> handleReminderResponse(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Reminder response received: {}", payload);
            
            String email = extractEmail(payload);
            String response = extractMessage(payload);
            
            if (email == null || response == null) {
                return ResponseEntity.ok(Map.of("text", "I couldn't process that. Try typing 'vote veg' or 'vote nonveg'."));
            }
            
            Employee employee = employeeService.findByEmail(email).orElse(null);
            if (employee == null) {
                return ResponseEntity.ok(Map.of("text", "Who are you? I don't recognize you. Contact admin!"));
            }
            
            // Parse response for vote intent
            String resp = response.toLowerCase();
            String foodType = null;
            
            if (resp.contains("nonveg") || resp.contains("non-veg") || resp.contains("non veg") || resp.contains("meat")) {
                foodType = "nonveg";
            } else if (resp.contains("veg") || resp.contains("vegetarian") || resp.contains("green")) {
                foodType = "veg";
            }
            
            if (foodType != null) {
                return handleVoteWithFlair(employee, foodType);
            }
            
            // Let AI handle it
            String aiResponse = aiChatService.chat(response, employee.getName());
            return ResponseEntity.ok(Map.of("text", aiResponse));
            
        } catch (Exception e) {
            log.error("Error processing reminder response: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("text", "Oops, something went wrong. Try the website?"));
        }
    }
    
    private String extractMessage(Map<String, Object> payload) {
        if (payload.containsKey("message")) {
            return (String) payload.get("message");
        }
        if (payload.containsKey("text")) {
            return (String) payload.get("text");
        }
        return null;
    }
    
    private String extractUserName(Map<String, Object> payload) {
        if (payload.containsKey("name")) {
            return (String) payload.get("name");
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) payload.get("user");
        if (user != null) {
            if (user.containsKey("first_name")) {
                return (String) user.get("first_name");
            }
            if (user.containsKey("name")) {
                return (String) user.get("name");
            }
        }
        return null;
    }
    
    private ResponseEntity<?> handleVoteWithFlair(Employee employee, String foodType) {
        try {
            if (!foodPoolService.isPoolOpen()) {
                return ResponseEntity.ok(Map.of("text", 
                    "The survey is closed. You missed it! Set an alarm next time, maybe?"));
            }
            
            FoodPool pool = foodPoolService.registerPool(employee, foodType);
            
            String[] responses = {
                String.format("Done! You're down for %s on %s. Your stomach will thank me later.", 
                    foodType.toUpperCase(), pool.getFoodDate()),
                String.format("Boom! %s vote locked in for %s. I'm basically your food secretary now.", 
                    foodType.toUpperCase(), pool.getFoodDate()),
                String.format("%s it is! Registered for %s. Try not to drool thinking about it.", 
                    foodType.toUpperCase(), pool.getFoodDate()),
                String.format("Vote recorded: %s for %s. You're welcome. I accept gratitude in the form of... actually, I'm a bot, I don't need anything.", 
                    foodType.toUpperCase(), pool.getFoodDate())
            };
            
            return ResponseEntity.ok(Map.of("text", responses[(int)(System.currentTimeMillis() % responses.length)]));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("text", "Error: " + e.getMessage() + "\n\nEven I make mistakes. Shocking, I know."));
        }
    }
    
    private ResponseEntity<?> handleStatusWithFlair(Employee employee) {
        MenuConfig menu = foodPoolService.getTodayMenu();
        LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : LocalDate.now().plusDays(1);
        
        boolean poolOpen = menu.isPoolOpen();
        boolean hasVoted = foodPoolService.hasPooledForFoodDate(employee.getEmployeeId(), foodDate);
        
        String status;
        if (!poolOpen) {
            status = "Survey's closed. You had your chance! Just kidding, there'll be another one. Probably.";
        } else if (hasVoted) {
            String choice = foodPoolService.getPoolForFoodDate(employee.getEmployeeId(), foodDate)
                    .map(FoodPool::getFoodType)
                    .map(String::toUpperCase)
                    .orElse("UNKNOWN");
            status = String.format("You already voted %s for %s. Changed your mind? Just vote again, I won't judge. Much.", 
                    choice, foodDate);
        } else {
            status = String.format("Survey is OPEN for %s! You haven't voted yet. Seriously? Get on it! Type 'vote veg' or 'vote nonveg'.", 
                    foodDate);
        }
        
        return ResponseEntity.ok(Map.of("text", status));
    }
    
    private String getHelpMessageWithFlair() {
        return "Oh, you need help? Alright, here's what I can do:\n\n" +
               "- status : I'll tell you if voting is open (and judge you if you haven't voted)\n" +
               "- vote veg : For the plant lovers\n" +
               "- vote nonveg : For everyone else\n" +
               "- Or just chat naturally - say 'I want veg' or 'put me down for nonveg'\n\n" +
               "I'm AI-powered, so try me. Ask for a joke. I dare you.";
    }
}

