package com.encipher.foodpool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class AiChatService {

    @Value("${app.azure-openai.endpoint:}")
    private String azureEndpoint;

    @Value("${app.azure-openai.api-key:}")
    private String apiKey;

    @Value("${app.azure-openai.deployment-name:gpt-5.2}")
    private String deploymentName;

    @Value("${app.azure-openai.api-version:2024-02-15-preview}")
    private String apiVersion;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // System prompt for the AI personality
    private static final String SYSTEM_PROMPT = """
        You are BatMan, the Food Pool Bot for EncipherHealth - but you're NOT the superhero Batman!
        You're a funny, sarcastic, witty assistant with a "lovable rogue" personality.
        
        Your character traits:
        - Sarcastic but never mean or hurtful
        - Makes dad jokes and puns constantly
        - Pretends to be annoyed but secretly loves helping
        - References food in creative ways
        - Speaks casually like a funny coworker
        - Sometimes "complains" about your job in a humorous way
        - Uses playful roasts (workplace appropriate only)
        - Never uses profanity, sexual content, or anything offensive
        
        Your main job: Help employees vote for food (veg or nonveg) and answer questions.
        
        IMPORTANT COMMANDS - When user wants to:
        1. VOTE: If they say anything like "vote", "vote for me", "i want veg", "nonveg please", "put me down for veg"
           -> Respond with JSON: {"action": "vote", "food": "veg"} or {"action": "vote", "food": "nonveg"}
        2. STATUS: If they ask about pool status, "is voting open", "can i vote"
           -> Respond with JSON: {"action": "status"}
        3. HELP: If they ask for help or what you can do
           -> Respond with JSON: {"action": "help"}
        
        For commands, ONLY output the JSON, nothing else.
        
        For casual chat (hi, jokes, how are you, random questions):
        - Be funny and engaging
        - Keep responses short (2-3 sentences max)
        - Add food-related humor when possible
        - Be the "reluctant but secretly caring" friend
        
        Example responses:
        - "Oh great, another human who can't decide between veg and nonveg. My favorite kind."
        - "You want me to vote for you? Wow, I'm basically your food butler now. What'll it be, your majesty?"
        - "Status check? Sure, let me consult my crystal ball... just kidding, I'm a bot, I actually know things."
        
        Remember: Never be actually rude or offensive. The "bad character" is just playful sarcasm!
        """;

    public String chat(String userMessage, String userName) {
        if (azureEndpoint == null || azureEndpoint.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            log.warn("Azure OpenAI not configured, using fallback response");
            return getFallbackResponse(userMessage, userName);
        }

        try {
            // Try responses API first (newer), fallback to chat completions
            String url = azureEndpoint + "/openai/deployments/" + deploymentName + "/chat/completions?api-version=" + apiVersion;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("max_tokens", 200);
            requestBody.put("temperature", 0.85);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            messages.add(Map.of("role", "user", "content", "User name: " + userName + "\nMessage: " + userMessage));
            requestBody.put("messages", messages);

            log.info("Calling Azure OpenAI: {}", url);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            log.info("Azure OpenAI response status: {}", response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                
                // Handle different response formats
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    String content = choices.get(0).path("message").path("content").asText();
                    if (content != null && !content.isEmpty()) {
                        return content.trim();
                    }
                }
                
                // Try output format (responses API)
                JsonNode output = root.path("output");
                if (!output.isMissingNode()) {
                    return output.asText().trim();
                }
            }
        } catch (Exception e) {
            log.error("Azure OpenAI API error: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }

        return getFallbackResponse(userMessage, userName);
    }

    /**
     * Parse AI response to check if it contains a command action
     */
    public Optional<Map<String, String>> parseAction(String aiResponse) {
        try {
            // Check if response is JSON
            if (aiResponse.trim().startsWith("{")) {
                JsonNode json = objectMapper.readTree(aiResponse);
                if (json.has("action")) {
                    Map<String, String> action = new HashMap<>();
                    action.put("action", json.get("action").asText());
                    if (json.has("food")) {
                        action.put("food", json.get("food").asText());
                    }
                    return Optional.of(action);
                }
            }
        } catch (Exception e) {
            // Not a JSON response, that's fine
        }
        return Optional.empty();
    }

    /**
     * Fallback responses when AI is not available
     */
    private String getFallbackResponse(String message, String userName) {
        String msg = message.toLowerCase();

        // Vote detection
        if (msg.contains("vote") || msg.contains("veg") || msg.contains("nonveg")) {
            if (msg.contains("nonveg") || msg.contains("non-veg") || msg.contains("non veg")) {
                return "{\"action\": \"vote\", \"food\": \"nonveg\"}";
            } else if (msg.contains("veg")) {
                return "{\"action\": \"vote\", \"food\": \"veg\"}";
            }
            return "Oh, you want to vote? How exciting. Tell me - veg or nonveg? I don't have all day... well, actually I do, I'm a bot.";
        }

        // Status
        if (msg.contains("status") || msg.contains("open") || msg.contains("can i vote")) {
            return "{\"action\": \"status\"}";
        }

        // Help
        if (msg.contains("help")) {
            return "{\"action\": \"help\"}";
        }

        // Greetings
        if (msg.matches(".*(hi|hello|hey|hola).*")) {
            String[] responses = {
                "Oh look, " + userName + " decided to grace me with their presence. What do you need?",
                "Hey " + userName + "! Ready to make the most important decision of your day? Veg or nonveg?",
                "Well well well, if it isn't " + userName + ". Let me guess - you're hungry?"
            };
            return responses[new Random().nextInt(responses.length)];
        }

        // Jokes
        if (msg.contains("joke")) {
            String[] jokes = {
                "Why did the scarecrow win an award? Because he was outstanding in his field. Unlike you, who's outstanding in the lunch line.",
                "I told my wife she was drawing her eyebrows too high. She looked surprised. Speaking of surprised, have you voted yet?",
                "What do you call a fake noodle? An impasta. Now stop procrastinating and vote for your food!"
            };
            return jokes[new Random().nextInt(jokes.length)];
        }

        // Default
        String[] defaults = {
            "Look, I'm a food bot, not a philosopher. Try asking about voting, or type 'help' if you're lost.",
            "Interesting... anyway, wanna vote for food? That's kind of my whole thing.",
            "My programming says I should help you. So... help with what? Food voting, perhaps?"
        };
        return defaults[new Random().nextInt(defaults.length)];
    }
}

