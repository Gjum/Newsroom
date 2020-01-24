# Newsroom

Discord bot for easy collaborative journalism.

## Usage

Build the JAR and fetch dependencies:

    ./gradlew installDist

Configure by setting environment variables:

    DISCORD_TOKEN=<token of bot account>
    DB_FILE=sqlite:newsroom.sqlite
    DISCORD_CMD_PREFIX=! # optional
    STAR_CHANNEL_ID=123098123098213098 # optional

Start the server:

    java -cp "build/install/newsroom/lib/*" io.github.gjum.discord.newsroom.MainKt
