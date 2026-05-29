#!/usr/bin/env bash

HEADUNIT_PRIMARY_HOST="${HEADUNIT_PRIMARY_HOST:-172.20.10.2}"
HEADUNIT_FALLBACK_HOST="${HEADUNIT_FALLBACK_HOST:-192.168.15.166}"
HEADUNIT_PRIMARY_LOCAL_HOST="${HEADUNIT_PRIMARY_LOCAL_HOST:-172.20.10.5}"
HEADUNIT_FALLBACK_LOCAL_HOST="${HEADUNIT_FALLBACK_LOCAL_HOST:-192.168.15.71}"
HEADUNIT_PORT="${HEADUNIT_PORT:-23}"

headunit_can_connect() {
  local host="$1"
  local port="${2:-$HEADUNIT_PORT}"
  local timeout="${3:-1}"

  python3 - "$host" "$port" "$timeout" <<'PY' >/dev/null 2>&1
import socket
import sys

host = sys.argv[1]
port = int(sys.argv[2])
timeout = float(sys.argv[3])

sock = socket.socket()
sock.settimeout(timeout)
try:
    sock.connect((host, port))
except OSError:
    sys.exit(1)
finally:
    sock.close()
PY
}

resolve_headunit_defaults() {
  local explicit_host="${HEADUNIT_HOST:-}"
  local explicit_local_host="${HEADUNIT_LOCAL_HOST:-}"

  if [[ -z "$explicit_host" ]]; then
    if headunit_can_connect "$HEADUNIT_PRIMARY_HOST" "$HEADUNIT_PORT" 0.8; then
      HEADUNIT_HOST="$HEADUNIT_PRIMARY_HOST"
    else
      HEADUNIT_HOST="$HEADUNIT_FALLBACK_HOST"
    fi
  fi

  if [[ -z "$explicit_local_host" ]]; then
    if [[ "$HEADUNIT_HOST" == "$HEADUNIT_FALLBACK_HOST" ]]; then
      HEADUNIT_LOCAL_HOST="$HEADUNIT_FALLBACK_LOCAL_HOST"
    elif [[ "$HEADUNIT_HOST" == "$HEADUNIT_PRIMARY_HOST" ]]; then
      HEADUNIT_LOCAL_HOST="$HEADUNIT_PRIMARY_LOCAL_HOST"
    fi
  fi

  export HEADUNIT_HOST HEADUNIT_LOCAL_HOST HEADUNIT_PORT
}
