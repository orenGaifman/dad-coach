#!/bin/sh
# Dad Coach backend for the lab on :8397 - lab DB (prod schema snapshot, Flyway off), the lab platform on :8396,
# WhatsApp to the fake Meta on :9399. Local-only keys (the same values as run-platform.sh).
HERE=$(cd "$(dirname "$0")" && pwd)
export JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}
export SPRING_PROFILES_ACTIVE=${LAB_PROFILE:-local} PORT=8397
export DB_URL=jdbc:postgresql://localhost:55482/dadcoach DB_USERNAME=postgres DB_PASSWORD=postgres
export WORKFLOW_PLATFORM_ENABLED=true WORKFLOW_PLATFORM_BASE_URL=http://localhost:8396
export WORKFLOW_PLATFORM_API_KEY=qa-dc-worker-key-0123456789abcdef WORKFLOW_PLATFORM_WORKER_KEY=dad_3 WORKFLOW_PLATFORM_WORKFLOW_KEY=dad-coach-3
export WORKFLOW_PLATFORM_ADMIN_API_KEY=qa-admin-key-0123456789abcdef
export WORKFLOW_PLATFORM_CALLBACK_ENABLED=true WORKFLOW_PLATFORM_CALLBACK_API_KEY=qa-dc-tool-key-0123456789abcdef0123456789
export TOOL_API_KEY=qa-dc-tool-key-0123456789abcdef0123456789 DADCOACH_PROACTIVE_MESSAGES_OWNER=PLATFORM
export DADCOACH_ADMIN_API_KEY=qa-dc-admin-key-0123456789abcdef DADCOACH_OPS_API_KEY=qa-dc-ops-key-0123456789abcdef
export WHATSAPP_PHONE_NUMBER_ID=qa-phone-id WHATSAPP_ACCESS_TOKEN=qa-token WHATSAPP_VERIFY_TOKEN=qa-verify
export WHATSAPP_WEBHOOK_SECRET=qa-webhook-secret WHATSAPP_PHONE_NUMBER=+972552961164 WHATSAPP_WABA_ID=qa-waba
export WEB_BASE_URL=${WEB_BASE_URL:-http://localhost:5397}
exec "$JAVA_HOME/bin/java" -jar "$HERE/.run/backend.jar" \
  --spring.datasource.url=$DB_URL --spring.datasource.username=postgres --spring.datasource.password=postgres \
  --spring.flyway.enabled=true --spring.jpa.hibernate.ddl-auto=validate \
  --server.port=8397 --dad-coach.whatsapp.verify-token=qa-verify \
  --dad-coach.whatsapp.api-base-url=http://localhost:9399 --spring.main.lazy-initialization=false
