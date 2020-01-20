package io.github.gjum.discord.newsroom

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection.TRANSACTION_SERIALIZABLE

class Database(dbUrl: String = "jdbc:sqlite::memory:", driver: String = "org.sqlite.JDBC") {
	private val db = Database.connect(dbUrl, driver = driver)

	init {
		// SQLite only allows TRANSACTION_SERIALIZABLE or TRANSACTION_READ_UNCOMMITTED
		TransactionManager.manager.defaultIsolationLevel = TRANSACTION_SERIALIZABLE
		// XXX create tables etc.
	}
}
