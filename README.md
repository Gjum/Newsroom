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

### Run with Docker

Create a `.env` file containing the environment variables above.

Then build and start the container:

    ./gradlew installDist
	docker-compose up -d --build

To see the application logs:

	docker-compose logs -tf
