#!/bin/bash
set -e # exit on error
cd "$(dirname $BASH_SOURCE)" # change working directory to script location

HOST="$1"
[ -z "$HOST" ] && echo 'Usage: ./install-remote.sh <hostname>' && exit 1

echo "Set up your SSH config with a 'Host $HOST' and make sure your user has sudo rights on that host. Press Enter to proceed ..."
read

ssh "$HOST" sudo useradd -m newsroom-bot # create `newsroom-bot` user, group, and home directory
ssh "$HOST" sudo usermod '"$USER"' -aG newsroom-bot
ssh "$HOST" sudo chmod g+rwx ~newsroom-bot/
ssh "$HOST" mkdir -p ~newsroom-bot/newsroom/
ssh "$HOST" sudo chown -R newsroom-bot:newsroom-bot ~newsroom-bot/newsroom
scp newsroom-bot.service start.sh .env "$HOST":~newsroom-bot/newsroom
ssh "$HOST" sudo chmod +x ~newsroom-bot/newsroom/start.sh
ssh "$HOST" sudo cp ~newsroom-bot/newsroom/newsroom-bot.service /etc/systemd/system/newsroom-bot.service
ssh "$HOST" sudo systemctl enable newsroom-bot # start automatically after reboot
./deploy.sh "$HOST" # build, upload, and start the bot
