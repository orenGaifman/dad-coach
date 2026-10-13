#!/bin/sh
# The AI Workflow Platform for the Dad Coach lab (jar built from a worktree of ai-workflow-platform origin/main).
# Local-only keys; the real model (ANTHROPIC_API_KEY from ~/repos/ai-workflow-platform/.env).
set -e
PLATFORM_DIR=${PLATFORM_DIR:-$HOME/repos/ai-workflow-platform}   # a jar with the product registry (platform P2, 2026-10-13+)
export ANTHROPIC_API_KEY=$(grep ^ANTHROPIC_API_KEY= $HOME/repos/ai-workflow-platform/.env | cut -d= -f2-)
export JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}
export PORT=${PLATFORM_PORT:-8396} DATABASE_URL=jdbc:postgresql://localhost:55483/${PLATFORM_DB:-workflow_platform} DATABASE_USERNAME=postgres DATABASE_PASSWORD=postgres
export CONVERSATION_REVIEW_AUTO_ENABLED=false ADMIN_API_KEY=qa-admin-key-0123456789abcdef
export DAD_COACH_ENABLED=true DAD_COACH_BASE_URL=http://localhost:${BACKEND_PORT:-8397} DAD_COACH_API_KEY=qa-dc-tool-key-0123456789abcdef0123456789
export WORKFLOW_PRODUCTS_DADCOACH_APIKEY=qa-dc-worker-key-0123456789abcdef
# worker keys come from the platform registry (workflow.products.dadcoach.worker-keys = dad_3)
export WORKFLOW_PRODUCTS_DADCOACH_CALLBACK_BASEURL=http://localhost:${BACKEND_PORT:-8397}
export WORKFLOW_PRODUCTS_DADCOACH_CALLBACK_APIKEY=qa-dc-tool-key-0123456789abcdef0123456789
export AGENT_CLAUDE_TURN_EFFORT=medium WHATSAPP_GATEWAY_ENABLED=false
exec "$JAVA_HOME/bin/java" -jar "${PLATFORM_JAR:-$PLATFORM_DIR/target/workflow-platform-1.0.0-SNAPSHOT.jar}"
