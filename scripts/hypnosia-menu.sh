#!/usr/bin/env bash
set -euo pipefail

LICENSE_CLI="${LICENSE_CLI:-/usr/local/bin/hypnosia-license}"
BACKUP_DIR="${BACKUP_DIR:-/opt/hypnosia/backups}"
DATA_FILE="${HYPNOSIA_LICENSE_DATA:-/opt/hypnosia/data/licenses.tsv}"

pause() {
  printf '\nPress Enter to continue...'
  read -r _
}

read_required() {
  local prompt="$1"
  local value=""
  while [ -z "$value" ]; do
    printf '%s' "$prompt"
    read -r value
  done
  printf '%s' "$value"
}

create_license() {
  local role expires key
  printf 'Role (USER/PREMIUM/QA/ADMIN/OWNER): '
  read -r role
  role="${role:-USER}"

  printf 'Expires YYYY-MM-DD or never [never]: '
  read -r expires
  expires="${expires:-never}"

  printf 'Custom 32-char key, empty = generate: '
  read -r key

  if [ -n "$key" ]; then
    "$LICENSE_CLI" create "$role" "$expires" "$key"
  else
    "$LICENSE_CLI" create "$role" "$expires"
  fi
}

show_license() {
  local key
  key="$(read_required 'License key: ')"
  "$LICENSE_CLI" show "$key"
}

set_role() {
  local key role
  key="$(read_required 'License key: ')"
  role="$(read_required 'New role: ')"
  "$LICENSE_CLI" role "$key" "$role"
}

set_expires() {
  local key expires
  key="$(read_required 'License key: ')"
  expires="$(read_required 'New expires YYYY-MM-DD or never: ')"
  "$LICENSE_CLI" expires "$key" "$expires"
}

toggle_license() {
  local action key
  action="$1"
  key="$(read_required 'License key: ')"
  "$LICENSE_CLI" "$action" "$key"
}

backup_data() {
  mkdir -p "$BACKUP_DIR"
  local stamp target
  stamp="$(date -u +%Y%m%d-%H%M%S)"
  target="$BACKUP_DIR/licenses-$stamp.tsv"
  if [ -f "$DATA_FILE" ]; then
    cp "$DATA_FILE" "$target"
    chmod 600 "$target"
    echo "Backup created: $target"
  else
    echo "No data file found: $DATA_FILE"
  fi
}

service_status() {
  systemctl --no-pager --full status hypnosia-license nginx | sed -n '1,120p'
}

show_logs() {
  journalctl -u hypnosia-license -n 80 --no-pager
}

restart_service() {
  systemctl restart hypnosia-license
  systemctl is-active hypnosia-license
}

while true; do
  clear
  cat <<'MENU'
Hypnosia License Console
========================
1) List licenses
2) Create license
3) Show license
4) Change role
5) Change expiration
6) Reset HWID
7) Disable license
8) Enable license
9) Delete license
10) Service status
11) Show last logs
12) Restart license service
13) Backup license data
0) Exit
MENU

  printf '\nSelect: '
  read -r choice
  printf '\n'

  case "$choice" in
    1) "$LICENSE_CLI" list; pause ;;
    2) create_license; pause ;;
    3) show_license; pause ;;
    4) set_role; pause ;;
    5) set_expires; pause ;;
    6) toggle_license reset-hwid; pause ;;
    7) toggle_license disable; pause ;;
    8) toggle_license enable; pause ;;
    9) toggle_license delete; pause ;;
    10) service_status; pause ;;
    11) show_logs; pause ;;
    12) restart_service; pause ;;
    13) backup_data; pause ;;
    0) exit 0 ;;
    *) echo "Unknown option"; pause ;;
  esac
done
