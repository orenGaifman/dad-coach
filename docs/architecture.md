# Architecture

Version 0.1 is a modular Spring Boot monolith. WhatsApp integration is isolated behind `WhatsAppService`. Incoming webhook payloads enter through `WhatsAppWebhookController`. PostgreSQL is managed with Flyway. AI coaching and registration will be added as separate modules without changing the public WhatsApp boundary.
