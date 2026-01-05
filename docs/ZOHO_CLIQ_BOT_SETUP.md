# Zoho Cliq Bot Setup Guide

This document provides step-by-step instructions for setting up the Food Pool Bot in Zoho Cliq.

## Prerequisites

- Admin access to your organization's Zoho Cliq
- Access to Zoho Developer Console

## Step 1: Create the Bot

1. Go to **Zoho Cliq Admin Panel** (https://cliq.zoho.com/admin)
2. Navigate to **Bots & Tools** > **Bots**
3. Click **Create Bot**
4. Fill in the bot details:
   - **Bot Name**: Food Pool Bot
   - **Description**: Vote for your daily meals and receive food pool notifications
   - **Bot Image**: Upload a food/meal related icon
   - **Bot Handle**: @foodpool

## Step 2: Configure Incoming Webhook

1. In the bot settings, go to **Incoming Webhook**
2. Click **Create Webhook**
3. Copy the generated webhook URL
4. Add this URL to your application's configuration:

```properties
# application-prod.properties
app.cliq.webhook-url=<PASTE_WEBHOOK_URL_HERE>
app.cliq.bot-webhook-url=<PASTE_WEBHOOK_URL_HERE>
```

## Step 3: Configure Bot Commands (Optional)

If you want to enable slash commands, set up the following:

1. Go to **Commands** tab in bot settings
2. Add the following commands:

### /foodpool status
- **Description**: Check your voting status
- **Handler URL**: `https://food.management.encipherhealth.com/api/bot/command`

### /foodpool vote
- **Description**: Vote for your meal (e.g., /foodpool vote veg)
- **Handler URL**: `https://food.management.encipherhealth.com/api/bot/command`

### /foodpool help
- **Description**: Show help information
- **Handler URL**: `https://food.management.encipherhealth.com/api/bot/command`

## Step 4: Configure Button Actions

For interactive buttons to work:

1. Go to **Functions** tab
2. Create a function named `handleVote`:

```javascript
response = Map();
data = arguments.get("data");
food = data.get("food");
user = arguments.get("user");
email = user.get("email");

// Make API call to your server
apiResponse = invokeurl
[
    url: "https://food.management.encipherhealth.com/api/bot/action"
    type: POST
    headers: {"Content-Type": "application/json"}
    body: {"food": food, "email": email}
];

response.put("text", apiResponse.get("text"));
return response;
```

3. Link this function to the button actions in your notifications

## Step 5: Configure Bot Handler (Optional)

For processing messages sent directly to the bot:

1. Go to **Handlers** tab
2. Set the **Message Handler URL**: `https://food.management.encipherhealth.com/api/bot/command`

## Step 6: Test the Bot

1. In Zoho Cliq, start a conversation with @foodpool
2. Type "help" to see available commands
3. Test voting: type "vote veg" or "vote nonveg"

## Configuration Reference

Add these to your `application-prod.properties`:

```properties
# Zoho Cliq Integration
app.cliq.webhook-url=YOUR_INCOMING_WEBHOOK_URL
app.cliq.bot-webhook-url=YOUR_BOT_WEBHOOK_URL
app.cliq.bot-webhook-secret=YOUR_SECRET_KEY

# Application URL (for links in messages)
app.base-url=https://food.management.encipherhealth.com
```

## Security Considerations

1. **Webhook Secret**: Set a secret key to validate incoming requests:
   ```java
   @Value("${app.cliq.bot-webhook-secret:}")
   private String webhookSecret;
   ```

2. **IP Whitelisting**: Consider whitelisting Zoho's IP addresses in your firewall

3. **HTTPS**: Always use HTTPS for webhook endpoints

## Notification Types

The bot sends the following notifications:

| Event | Notification |
|-------|--------------|
| Pool Started | Announcement with Veg/Non-Veg buttons |
| 1 Hour Before Close | Reminder to vote |
| Pool Closed | Final count summary |
| Food Arrived | Reminder to collect |
| 50% Collected | Progress update |
| 70% Collected | Urgency reminder |
| Grace Request | Admin notification |
| Collection Complete | Thank you message |

## Troubleshooting

### Bot not responding
- Check if webhook URL is correctly configured
- Verify the application is running and accessible
- Check application logs for errors

### Buttons not working
- Ensure functions are properly configured in Zoho Cliq
- Verify the function handler URL is correct
- Check CORS settings if applicable

### Messages not being sent
- Verify webhook URL is correct
- Check if the bot has required permissions
- Review application logs for HTTP errors

## Support

For issues with the bot integration, contact:
- Application Team: Check application logs
- Zoho Cliq: https://help.zoho.com/portal/en/kb/cliq

