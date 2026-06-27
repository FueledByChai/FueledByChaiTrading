#!/bin/bash
# Start/stop/status for the market-data collector daemon.
#
# Layout (mirrors the chaiwala-mm-generic.sh conventions):
#   /home/bitnami/apps/market-data-collector/
#     market-data-collector.jar   <- replaced on deploy
#     collector.properties        <- instruments + root dir
#     collector.sh                <- this script
#     logs/collector-stdout.log   <- nohup'd stdout/stderr (JVM death notes,
#                                    hs_err summaries — do NOT /dev/null it)
#   collector.root.dir should point OUTSIDE this dir (and outside any build
#   target/) e.g. /home/bitnami/market-data — a stray `mvn clean` or redeploy
#   must never be able to touch the dataset.

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
JAR="$APP_HOME/market-data-collector.jar"
PROPS="$APP_HOME/collector.properties"
PID_FILE="$APP_HOME/collector.pid"
LOG_DIR="$APP_HOME/logs"
STDOUT_LOG="$LOG_DIR/collector-stdout.log"

# --enable-native-access: zstd-jni (parquet compression) loads a native lib;
# future JDKs hard-block restricted native access without this flag.
JAVA_OPTS="--enable-native-access=ALL-UNNAMED -Dfueledbychai.run.proxy=true"

is_running() {
    [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null
}

case "$1" in
    start)
        if is_running; then
            echo "Collector already running (pid $(cat "$PID_FILE"))"
            exit 0
        fi
        mkdir -p "$LOG_DIR"
        # Rotate the previous stdout log so a crash note is never overwritten.
        [ -f "$STDOUT_LOG" ] && mv "$STDOUT_LOG" "$STDOUT_LOG.prev"
        cd "$APP_HOME" || exit 1
        nohup java $JAVA_OPTS -jar "$JAR" "$PROPS" >> "$STDOUT_LOG" 2>&1 &
        echo $! > "$PID_FILE"
        sleep 2
        if is_running; then
            echo "Collector started (pid $(cat "$PID_FILE")), stdout -> $STDOUT_LOG"
        else
            echo "Collector FAILED to start — check $STDOUT_LOG"
            tail -20 "$STDOUT_LOG"
            exit 1
        fi
        ;;
    stop)
        if ! is_running; then
            echo "Collector not running"
            rm -f "$PID_FILE"
            exit 0
        fi
        PID=$(cat "$PID_FILE")
        echo "Stopping collector (pid $PID) — SIGTERM triggers the parquet flush hook..."
        kill "$PID"
        for _ in $(seq 1 30); do
            kill -0 "$PID" 2>/dev/null || break
            sleep 1
        done
        if kill -0 "$PID" 2>/dev/null; then
            echo "Still alive after 30s, SIGKILL (open part files will lose their tail)"
            kill -9 "$PID"
        fi
        rm -f "$PID_FILE"
        echo "Stopped."
        ;;
    status)
        if is_running; then
            echo "Collector running (pid $(cat "$PID_FILE"))"
            tail -5 "$STDOUT_LOG" 2>/dev/null
        else
            echo "Collector not running"
        fi
        ;;
    logs)
        tail -f "$STDOUT_LOG"
        ;;
    *)
        echo "Usage: $0 {start|stop|status|logs}"
        exit 1
        ;;
esac
