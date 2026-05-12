#!/bin/sh
set -eu

if [ "${1:-}" = "--self-test" ]; then
    echo "obsidiangate-remote-deploy: ok"
    exit 0
fi

STAGE_DIR="${1:?stage dir is required}"
SERVER_JAR="${2:?server jar file name is required}"
SERVER_MODS_DIR="${3:?server mods dir is required}"
WEB_ROOT="${4:?web root is required}"
SERVICE_NAME="${5:?service name is required}"
SKIP_RESTART="${6:-0}"

case "$STAGE_DIR" in
    /home/*) ;;
    *)
        echo "Stage directory must be under /home: $STAGE_DIR" >&2
        exit 2
        ;;
esac

case "$SERVER_MODS_DIR" in
    /home/*) ;;
    *)
        echo "Server mods dir must be under /home: $SERVER_MODS_DIR" >&2
        exit 2
        ;;
esac

case "$WEB_ROOT" in
    /var/www/*) ;;
    *)
        echo "Web root must be under /var/www: $WEB_ROOT" >&2
        exit 2
        ;;
esac

if [ ! -d "$STAGE_DIR/client" ]; then
    echo "Missing staged client directory: $STAGE_DIR/client" >&2
    exit 3
fi

if [ ! -f "$STAGE_DIR/manifest.json" ]; then
    echo "Missing staged manifest: $STAGE_DIR/manifest.json" >&2
    exit 3
fi

if [ ! -f "$STAGE_DIR/$SERVER_JAR" ]; then
    echo "Missing staged server jar: $STAGE_DIR/$SERVER_JAR" >&2
    exit 3
fi

install -d "$SERVER_MODS_DIR"
install -m 644 "$STAGE_DIR/$SERVER_JAR" "$SERVER_MODS_DIR/$SERVER_JAR"

install -d "$WEB_ROOT"
if command -v rsync >/dev/null 2>&1; then
    install -d "$WEB_ROOT/client"
    rsync -a --delete "$STAGE_DIR/client/" "$WEB_ROOT/client/"
else
    rm -rf "$WEB_ROOT/client"
    install -d "$WEB_ROOT/client"
    cp -a "$STAGE_DIR/client/." "$WEB_ROOT/client/"
fi

install -m 644 "$STAGE_DIR/manifest.json" "$WEB_ROOT/manifest.json"
sha256sum "$SERVER_MODS_DIR/$SERVER_JAR"
sha256sum "$WEB_ROOT/manifest.json"

if [ "$SKIP_RESTART" != "1" ]; then
    systemctl restart "$SERVICE_NAME"
    systemctl status "$SERVICE_NAME" --no-pager -l
fi
