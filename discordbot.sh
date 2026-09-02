#!/usr/bin/env bash

HOME_PATH="$(dirname "$(readlink -f "$0")")"
APP_NAME="$(basename "$0" .sh)"
JAR_FILE="DiscordBot.jar"

APP_CONFIG_FILE="${HOME_PATH}/config/application.yml"

COMMANDLINE="java \
            -Dcom.sun.management.jmxremote \
            -Dcom.sun.management.jmxremote.port=9074 \
            -Dcom.sun.management.jmxremote.local.only=false \
            -Dcom.sun.management.jmxremote.authenticate=false \
            -Dcom.sun.management.jmxremote.ssl=false \
            -Xms200m \
            -Xmx900m \
            -XX:+UnlockExperimentalVMOptions \
            -XX:+UseG1GC \
            -XX:MaxGCPauseMillis=200 \
            -XX:ParallelGCThreads=8 \
            -XX:ConcGCThreads=4 \
            -XX:+ScavengeBeforeFullGC \
            -XX:+DisableExplicitGC \
            --enable-native-access=ALL-UNNAMED \
            -Dlogging.config=$HOME_PATH/config/logback.xml \
            -Dapp.config=$APP_CONFIG_FILE \
            -Dspring.config.additional-location=optional:file:$HOME_PATH/config/ \
            -jar $HOME_PATH/$JAR_FILE"

function start_helper() {
    nohup $1 > /dev/null 2>&1 &
}

function start_debug_helper() {
    echo "${APP_NAME^^} STARTED IN DEBUG MODE. LOGFILE LOCATION: ${HOME_PATH}/${APP_NAME}_debug.log)"
    nohup $1 > "${APP_NAME}_debug.log" 2>&1 &
}

function check_run() {
    echo "$(pgrep -f $1)"
}

function start_app() {
    export CURRENT_DIR="$(pwd)"
    cd "$HOME_PATH" || exit 1

    if [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]]; then
        ATTEMPTS=0

        until [[ -n "$(check_run $HOME_PATH/$JAR_FILE)" ]]; do
            if (( ATTEMPTS < 3 )); then
                echo "Trying to start $APP_NAME..."

                if [[ $2 == "debug" ]]; then
                    start_debug_helper "$COMMANDLINE"
                else
                    start_helper "$COMMANDLINE"
                fi

                sleep 5
                ATTEMPTS=$((ATTEMPTS+1))
            else
                echo "Can not start $APP_NAME!"
                exit 1
            fi
        done

        echo "$APP_NAME now running with following PIDs: $(check_run $HOME_PATH/$JAR_FILE)"
    else
        echo "$APP_NAME already running. Current PIDs: $(check_run $HOME_PATH/$JAR_FILE)"
    fi

    cd "$CURRENT_DIR" || exit 1
}

function status_app() {
    if [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]]; then
        echo "$APP_NAME with path \"$HOME_PATH/$JAR_FILE\" is not running"
    else
        echo "$APP_NAME is running with path \"$HOME_PATH/$JAR_FILE\" and PID: $(check_run $HOME_PATH/$JAR_FILE)"
    fi
}

function stop_helper() {
    kill $1
}

function stop_force() {
    kill -9 $1
}

function stop_app() {
    if [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]]; then
        echo "$APP_NAME is not running"

    elif [[ $2 == "force" ]]; then
        echo "Force stopping these $APP_NAME PIDs: $(check_run $HOME_PATH/$JAR_FILE)"
        stop_force "$(check_run $HOME_PATH/$JAR_FILE)"
        sleep 5
    else
        COUNT=0
        ATTEMPTS=0
        echo "Stopping these $APP_NAME PIDs: $(check_run $HOME_PATH/$JAR_FILE)"
        stop_helper "$(check_run $HOME_PATH/$JAR_FILE)"
        sleep 5

        until [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]]; do
            if (( ATTEMPTS < 2 )); then
                if (( COUNT < 3 )); then
                    echo "Performing 5 seconds timeout..."
                    sleep 5
                    COUNT=$((COUNT+1))
                else
                    echo "One more attempt to stop $APP_NAME correctly: $(check_run $HOME_PATH/$JAR_FILE)"
                    stop_helper "$(check_run $HOME_PATH/$JAR_FILE)"
                    sleep 5
                    COUNT=0
                    ATTEMPTS=$((ATTEMPTS+1))
                fi
            else
                echo "Can not stop these PIDs of $APP_NAME correctly: $(check_run $HOME_PATH/$JAR_FILE)...Forced stop!"
                stop_force "$(check_run $HOME_PATH/$JAR_FILE)"
                sleep 5
                break
            fi
        done
    fi

    if [[ -z "$(check_run $HOME_PATH/$JAR_FILE)" ]]; then
        echo "$APP_NAME app PID file does not exists"
    else
        echo "PIDs $(check_run $HOME_PATH/$JAR_FILE) WAS NOT STOPPED!!!"
    fi
}

case "$1" in
    start)
        start_app $@
        ;;
    status)
        status_app
        ;;
    stop)
        stop_app $@
        ;;
    restart)
        echo "Performing $APP_NAME restart"
        stop_app
        start_app $@
        ;;
    *)
        echo "usage: service {start|start debug|stop|restart|stop force}" >&2
        exit 0
        ;;
esac
