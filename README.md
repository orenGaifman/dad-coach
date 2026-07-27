# Dad Coach AI — v0.1

WhatsApp-first backend foundation for an AI coach for fathers.

## Requirements

- Java 21
- Maven 3.9+
- Docker Desktop

## Run

```bash
docker compose up -d
cd backend
mvn spring-boot:run
```

In IntelliJ, add these environment variables to `DadCoachApplication`:

```text
WHATSAPP_PHONE_NUMBER_ID=your_phone_number_id
WHATSAPP_ACCESS_TOKEN=your_new_access_token
WHATSAPP_VERIFY_TOKEN=dad-coach-secret
```

Never commit the access token. The token visible in a screenshot should be regenerated.

## Send a text message

Text messages are allowed while the customer-service window is open. Outside that window, Meta requires an approved template.

```bash
curl -X POST http://localhost:8080/api/whatsapp/messages/text \
  -H 'Content-Type: application/json' \
  -d '{"to":"972503020551","message":"Hello from Dad Coach AI"}'
```

## Webhook verification

Public callback URL:

```text
https://YOUR_PUBLIC_HOST/webhooks/whatsapp
```

Verify token:

```text
dad-coach-secret
```

Localhost cannot receive Meta webhooks directly. Use a secure tunnel or deploy the backend before configuring the callback.

## Health

```text
GET http://localhost:8080/actuator/health
```
