#!/bin/bash
set -e # exit on error
cd "$(dirname $BASH_SOURCE)" # change working directory to script location

HOST="$1"
[ -z "$HOST" ] && echo 'Usage: ./deploy.sh <hostname>' && exit 1

./gradlew installDist || exit 1

rsync -av build/install/newsroom/lib/ "$HOST":~newsroom-bot/newsroom/lib/
scp .env "$HOST":~newsroom-bot/newsroom
ssh "$HOST" sudo systemctl restart newsroom-bot
ssh "$HOST" sudo journalctl -u newsroom-bot --utc -f -n 30
