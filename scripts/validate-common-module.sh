#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  scripts/validate-common-module.sh <module-dir>

Description:
  Validates that a commons exchange module has the baseline AI-facing structure:
  - module README
  - required ServiceLoader registrations
  - test classes

  If the module exposes a shared I*WebSocketApi/I*WebsocketApi interface, this
  script also requires an ExchangeWebSocketApiProvider registration.
EOF
}

log_info() {
    printf '[INFO] %s\n' "$*"
}

log_warn() {
    printf '[WARN] %s\n' "$*"
}

log_error() {
    printf '[ERROR] %s\n' "$*" >&2
}

require_file() {
    local file_path="$1"
    local description="$2"
    if [[ -f "${file_path}" ]]; then
        log_info "Found ${description}: ${file_path}"
        return 0
    fi
    log_error "Missing ${description}: ${file_path}"
    return 1
}

if [[ $# -ne 1 ]]; then
    usage
    exit 1
fi

if [[ "${1}" == "--help" || "${1}" == "-h" ]]; then
    usage
    exit 0
fi

MODULE_DIR="${1%/}"
if [[ ! -d "${MODULE_DIR}" ]]; then
    log_error "Module directory not found: ${MODULE_DIR}"
    exit 1
fi

FAILURES=0
SERVICES_DIR="${MODULE_DIR}/src/main/resources/META-INF/services"
MAIN_JAVA_DIR="${MODULE_DIR}/src/main/java"
TEST_JAVA_DIR="${MODULE_DIR}/src/test/java"

log_info "Validating ${MODULE_DIR}"

if [[ "${MODULE_DIR##*/}" != *-common-api ]]; then
    log_warn "Module name does not end with -common-api: ${MODULE_DIR##*/}"
fi

require_file "${MODULE_DIR}/pom.xml" "module POM" || FAILURES=$((FAILURES + 1))
require_file "${MODULE_DIR}/README.md" "module README" || FAILURES=$((FAILURES + 1))
require_file "${SERVICES_DIR}/com.fueledbychai.util.ExchangeRestApiProvider" "REST provider service registration" || FAILURES=$((FAILURES + 1))
require_file "${SERVICES_DIR}/com.fueledbychai.util.TickerRegistryProvider" "ticker registry service registration" || FAILURES=$((FAILURES + 1))
require_file "${SERVICES_DIR}/com.fueledbychai.util.ExchangeCapabilitiesProvider" "exchange capabilities service registration" || FAILURES=$((FAILURES + 1))

HAS_SHARED_WS_API=0
if [[ -d "${MAIN_JAVA_DIR}" ]] && rg --files "${MAIN_JAVA_DIR}" | rg -q '/I[^/]*(WebSocket|Websocket)Api\.java$'; then
    HAS_SHARED_WS_API=1
fi

if [[ "${HAS_SHARED_WS_API}" -eq 1 ]]; then
    require_file "${SERVICES_DIR}/com.fueledbychai.util.ExchangeWebSocketApiProvider" "websocket provider service registration" || FAILURES=$((FAILURES + 1))
else
    log_info "No shared websocket API interface detected; websocket provider registration is optional for this module"
fi

TEST_COUNT=0
if [[ -d "${TEST_JAVA_DIR}" ]]; then
    TEST_COUNT="$(rg --files "${TEST_JAVA_DIR}" | rg 'Test\.java$' | wc -l | tr -d ' ')"
fi

if [[ "${TEST_COUNT}" -gt 0 ]]; then
    log_info "Found ${TEST_COUNT} test class(es) under ${TEST_JAVA_DIR}"
else
    log_error "No test classes found under ${TEST_JAVA_DIR}"
    FAILURES=$((FAILURES + 1))
fi

if [[ "${FAILURES}" -gt 0 ]]; then
    log_error "Validation failed with ${FAILURES} issue(s)"
    exit 1
fi

log_info "Validation passed"
