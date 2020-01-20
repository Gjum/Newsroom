package io.github.gjum.discord.newsroom

fun main() {
	val dbUrl = System.getenv("DATABASE_URL") ?: "sqlite:newsroom.sqlite"
	val db = Database(dbUrl)
	val discord = Discord(db)
}
