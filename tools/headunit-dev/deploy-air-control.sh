#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TELNET_EXEC="$SCRIPT_DIR/telnet-exec.sh"

AIR_CONTROL_DIR="${AIR_CONTROL_DIR:-$ROOT_DIR/cluster-widgets/air-control}"
REMOTE_HTML_PATH="${REMOTE_HTML_PATH:-/data/local/tmp/app.html}"
HEADUNIT_HOST="${HEADUNIT_HOST:-192.168.15.46}"
HTTP_PORT="${HTTP_PORT:-8766}"

require_file() {
  local file="$1"
  [[ -f "$file" ]] || {
    echo "[HavalDev] File not found: $file" >&2
    exit 1
  }
}

base64_single_line() {
  local file="$1"
  if command -v openssl >/dev/null 2>&1; then
    openssl base64 -A -in "$file"
  else
    base64 < "$file" | tr -d '\n'
  fi
}

local_http_host() {
  if [[ -n "${HEADUNIT_LOCAL_HOST:-}" ]]; then
    printf '%s\n' "$HEADUNIT_LOCAL_HOST"
    return
  fi

  local route_interface
  route_interface="$(route get "$HEADUNIT_HOST" 2>/dev/null | awk '/interface:/{print $2; exit}')"
  if [[ -n "$route_interface" ]]; then
    ipconfig getifaddr "$route_interface" 2>/dev/null && return
  fi

  ipconfig getifaddr en0 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}'
}

push_html_via_http() {
  local html_path="$1"
  local remote_path="$2"
  local host_ip
  local server_log
  local server_pid
  local url
  local remote_cmd

  host_ip="$(local_http_host)"
  if [[ -z "$host_ip" ]]; then
    echo "[HavalDev] Could not determine local host IP for HTTP deploy" >&2
    return 1
  fi

  server_log="$(mktemp)"
  python3 -m http.server "$HTTP_PORT" --bind 0.0.0.0 --directory "$(dirname "$html_path")" > "$server_log" 2>&1 &
  server_pid="$!"
  trap 'if [[ -n "${server_pid:-}" ]]; then kill "$server_pid" >/dev/null 2>&1 || true; fi; if [[ -n "${server_log:-}" ]]; then rm -f "$server_log"; fi' RETURN

  sleep 1
  if ! kill -0 "$server_pid" >/dev/null 2>&1; then
    echo "[HavalDev] Local HTTP server failed to start" >&2
    sed -n '1,80p' "$server_log" >&2
    return 1
  fi

  url="http://${host_ip}:${HTTP_PORT}/$(basename "$html_path")"
  echo "[HavalDev] Asking headunit to download $url"

  remote_cmd="rm -f '${remote_path}.tmp' '${remote_path}'; (curl -fsSL '$url' -o '${remote_path}.tmp' || wget -O '${remote_path}.tmp' '$url' || toybox wget -O '${remote_path}.tmp' '$url' || busybox wget -O '${remote_path}.tmp' '$url') && mv '${remote_path}.tmp' '${remote_path}' && chmod 644 '${remote_path}' && ls -l '${remote_path}'"
  "$TELNET_EXEC" "$remote_cmd" >/dev/null || true

  local expected_size
  expected_size="$(wc -c < "$html_path" | tr -d ' ')"
  for _ in $(seq 1 30); do
    local remote_size
    remote_size="$("$TELNET_EXEC" "wc -c '$remote_path' 2>/dev/null | awk '{print \\$1}'" 2>/dev/null | tr -cd '0-9' | tail -c 20)"
    if [[ "$remote_size" == "$expected_size" ]]; then
      echo "[HavalDev] Headunit downloaded app.html via HTTP"
      return 0
    fi
    sleep 1
  done

  echo "[HavalDev] Headunit did not complete app.html download from local HTTP server" >&2
  sed -n '1,80p' "$server_log" >&2
  return 1
}

push_html() {
  local html_path="$1"
  local remote_path="$2"
  local remote_b64="${remote_path}.b64"
  local chunk
  local batch_file
  local batch_output
  local netcat_bin

  echo "[HavalDev] Sending app.html to $HEADUNIT_HOST:$remote_path"

  if push_html_via_http "$html_path" "$remote_path"; then
    return
  fi

  echo "[HavalDev] HTTP deploy failed, falling back to Telnet chunk upload"

  if command -v nc >/dev/null 2>&1; then
    netcat_bin="nc"
  elif command -v netcat >/dev/null 2>&1; then
    netcat_bin="netcat"
  else
    echo "[HavalDev] netcat not found, falling back to telnet-exec chunk mode"
    "$TELNET_EXEC" "rm -f '$remote_b64' '$remote_path'"
    while IFS= read -r chunk; do
      "$TELNET_EXEC" "printf '%s' '$chunk' >> '$remote_b64'"
    done < <(base64_single_line "$html_path" | fold -w 700)
    "$TELNET_EXEC" "if command -v base64 >/dev/null 2>&1; then base64 -d '$remote_b64' > '$remote_path'; elif command -v busybox >/dev/null 2>&1; then busybox base64 -d '$remote_b64' > '$remote_path'; else echo 'missing base64 on target' >&2; exit 1; fi"
    "$TELNET_EXEC" "chmod 644 '$remote_path' && ls -l '$remote_path' && rm -f '$remote_b64'"
    return
  fi

  batch_file="$(mktemp)"
  batch_output="$(mktemp)"
  trap 'rm -f "$batch_file" "$batch_output"' RETURN

  {
    printf "rm -f '%s' '%s'\n" "$remote_b64" "$remote_path"
    while IFS= read -r chunk; do
      printf "printf '%%s' '%s' >> '%s'\n" "$chunk" "$remote_b64"
    done < <(base64_single_line "$html_path" | fold -w 700)
    printf "if command -v base64 >/dev/null 2>&1; then base64 -d '%s' > '%s'; elif command -v busybox >/dev/null 2>&1; then busybox base64 -d '%s' > '%s'; else echo 'missing base64 on target' >&2; exit 1; fi\n" "$remote_b64" "$remote_path" "$remote_b64" "$remote_path"
    printf "chmod 644 '%s' && ls -l '%s' && rm -f '%s'\n" "$remote_path" "$remote_path" "$remote_b64"
    printf "echo __HAVALDEV_DONE__\n"
  } > "$batch_file"

  if [[ "$netcat_bin" == "nc" ]]; then
    {
      sed 's/$/\r/' "$batch_file"
      sleep 1
    } | "$netcat_bin" -w 3 "$HEADUNIT_HOST" 23 > "$batch_output" 2>/dev/null || true
  else
    {
      sed 's/$/\r/' "$batch_file"
      sleep 1
    } | "$netcat_bin" -w 3 "$HEADUNIT_HOST" 23 > "$batch_output" 2>/dev/null || true
  fi

  if ! LC_ALL=C grep -q "__HAVALDEV_DONE__" "$batch_output"; then
    echo "[HavalDev] Upload command batch did not confirm completion" >&2
    echo "[HavalDev] Retrying with telnet-exec chunk mode"
    "$TELNET_EXEC" "rm -f '$remote_b64' '$remote_path'"
    while IFS= read -r chunk; do
      "$TELNET_EXEC" "printf '%s' '$chunk' >> '$remote_b64'"
    done < <(base64_single_line "$html_path" | fold -w 700)
    "$TELNET_EXEC" "if command -v base64 >/dev/null 2>&1; then base64 -d '$remote_b64' > '$remote_path'; elif command -v busybox >/dev/null 2>&1; then busybox base64 -d '$remote_b64' > '$remote_path'; else echo 'missing base64 on target' >&2; exit 1; fi"
    "$TELNET_EXEC" "chmod 644 '$remote_path' && ls -l '$remote_path' && rm -f '$remote_b64'"
  fi
}

echo "[HavalDev] Building air-control frontend"
cd "$AIR_CONTROL_DIR"

if command -v bun >/dev/null 2>&1; then
  bun run build
else
  echo "[HavalDev] bun not found, using npm run build"
  npm run build
fi

HTML_PATH="$AIR_CONTROL_DIR/dist/app.html"
if [[ ! -f "$HTML_PATH" ]]; then
  HTML_PATH="$(find "$AIR_CONTROL_DIR" -maxdepth 3 -type f -name 'app.html' | sort | head -n 1)"
fi

require_file "$HTML_PATH"
push_html "$HTML_PATH" "$REMOTE_HTML_PATH"

echo "[HavalDev] Hot deploy complete. APK was not reinstalled."
echo "[HavalDev] To reload HTML, restart only the app/screen, for example:"
echo "  ./tools/headunit-dev/headunit.sh exec \"am force-stop br.com.redesurftank.havalshisuku && am start -n br.com.redesurftank.havalshisuku/.SplashActivity\""
