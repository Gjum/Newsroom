package io.github.gjum.discord.newsroom

import io.github.gjum.discord.newsroom.StoryState.*
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.`java-time`.timestamp
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.time.Duration
import java.time.Instant

fun getDriverForDbUrl(dbUrl: String) = when (dbUrl.split(":").first()) {
	"sqlite" -> "org.sqlite.JDBC"
	"postgresql", "postgres" -> "org.postgresql.Driver"
	else -> error("Failed to guess driver for database URL: $dbUrl")
}

class Database(dbUrl: String = "sqlite::memory:", driver: String = getDriverForDbUrl(dbUrl)) {
	val db = Database.connect(
		"jdbc:" + dbUrl.replace("postgres://([^:@]+):([^@]+)@(.+)$".toRegex(),
			"postgresql://$3?user=$1&password=$2"),
		driver = driver)

	init {
		// SQLite only allows TRANSACTION_SERIALIZABLE or TRANSACTION_READ_UNCOMMITTED
		TransactionManager.manager.defaultIsolationLevel = TRANSACTION_SERIALIZABLE
		transaction(db) {
			SchemaUtils.createMissingTablesAndColumns(
				Stories, Reviews)
		}
	}

	fun dropAndRecreateTables() = transaction(db) {
		SchemaUtils.drop(Stories, Reviews)
		SchemaUtils.createMissingTablesAndColumns(
			Stories, Reviews)
	}
}

val Query.stories get() = Story.wrapRows(this)
val Query.reviews get() = Review.wrapRows(this)

enum class StoryState { INCOMPLETE, READY, PUBLISHED, DISCARDED }

object Stories : IntIdTable() {
	val createdTime = timestamp("createdTime")
		.apply { defaultValueFun = Instant::now }
	val creator = discordUser("creator")
		.default("")
	val title = text("title")
		.default("")
	val content = text("content")
		.default("")
	val lastEditTime = timestamp("lastEditTime")
		.apply { defaultValueFun = Instant::now }
	val state = enumerationByName("state", 30, StoryState::class)
		.default(INCOMPLETE)
	val assignee = discordUser("assignee").nullable()
	val assignTime = timestamp("assignTime").nullable()
	val doneTime = timestamp("doneTime").nullable() // set when published or discarded
	val duplicateOf = reference("duplicateOf", Stories).nullable() // set when discarded
}

class Story(id: EntityID<Int>) : IntEntity(id) {
	companion object : IntEntityClass<Story>(Stories) {
		fun findUnassigned() = dependsOnTables.slice(dependsOnColumns).select {
			Stories.assignee.isNull()
				.and(Stories.doneTime.isNull())
		}.orderBy(Stories.lastEditTime to SortOrder.ASC)

		/**
		 * Find all stories that are [READY] but have less than [maxReviews] reviews,
		 * returning stories with the most reviews first ("almost done").
		 */
		fun findReviewable(maxReviews: Int) = (Stories leftJoin Reviews)
			.slice(Stories.columns + Reviews.id.countDistinct())
			.selectAll()
			.groupBy(Stories.id)
			.having {
				(Stories.state eq READY)
					.and(Reviews.id.countDistinct() less maxReviews)
			}
			.withDistinct()
			.orderBy(
				Reviews.id.countDistinct() to SortOrder.DESC,
				Stories.lastEditTime to SortOrder.ASC)
	}

	var createdTime by Stories.createdTime
	var creator by Stories.creator
	var title by Stories.title
	var content by Stories.content
	var lastEditTime by Stories.lastEditTime
	var state by Stories.state
	var assignee by Stories.assignee
	var assignTime by Stories.assignTime
	var doneTime by Stories.doneTime
	var duplicateOf by Stories.duplicateOf

	val reviews by Review referrersOn Reviews.story
}

object Reviews : IntIdTable() {
	val createdTime = timestamp("createdTime")
		.apply { defaultValueFun = Instant::now }
	val story = reference("story", Stories)
	val reviewer = discordUser("reviewer")
	val accepted = bool("accepted")
	val content = text("content")
		.default("")
}

class Review(id: EntityID<Int>) : IntEntity(id) {
	companion object : IntEntityClass<Review>(Reviews)

	var createdTime by Reviews.createdTime
	var story by Reviews.story
	var reviewer by Reviews.reviewer
	var accepted by Reviews.accepted
	var content by Reviews.content
}

fun Table.discordUser(name: String) = text(name) // TODO discordUser column type - get user from jdb instance
