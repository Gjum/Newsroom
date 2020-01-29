#!/bin/bash
set -e # exit on error
cd "$(dirname $BASH_SOURCE)" # change working directory to script location

HOST="$1"
[ -z "$HOST" ] && echo 'missing arg <hostname>' && exit 1

./gradlew installDist || exit 1
docker build -t gjum/newsroom_bot:latest .
docker image save -o newsroom_bot.tar gjum/newsroom_bot:latest
ssh "$HOST" "mkdir -p newsroom/"
ssh "$HOST" "[ ! -f newsroom/production.yml ] || mv -v newsroom/production.yml newsroom/old.production.yml"
rsync -avz production.yml newsroom_bot.tar "$HOST":newsroom/
ssh "$HOST" "docker load < newsroom/newsroom_bot.tar"
ssh "$HOST" "[ ! -f newsroom/old.production.yml ] || docker-compose -f newsroom/old.production.yml down"
ssh "$HOST" docker-compose -f newsroom/production.yml up -d
ssh "$HOST" docker-compose -f newsroom/production.yml logs -tf
