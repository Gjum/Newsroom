package io.github.gjum.discord.newsroom

import com.sun.istack.internal.logging.Logger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.awt.Color

private val logger = Logger.getLogger(Discord::class.java)

object Emotes {
	const val star = "⭐"
	const val joy = "\uD83D\uDE02"
}

private const val zeroWidthSpace = "\u200B"

class Discord(private val db: Database) : ListenerAdapter() {
	private val cmdPrefix = System.getenv("DISCORD_CMD_PREFIX") ?: "!"
	private val commands = mutableMapOf<String, Command>()
	private val reactionHandlers = mutableMapOf<String, ReactionAddHandler>()
	private val starChannelId = System.getenv("STAR_CHANNEL_ID") ?: null

	private var jda = JDABuilder(System.getenv("DISCORD_TOKEN")
		?: error("You must set the DISCORD_TOKEN environment variable"))
		.addEventListeners(this)
		.build()

	init {
		jda.awaitReady()
		logger.info("Logged in as ${jda.selfUser.asTag}")

		registerCommand(object : Command("help [command]", "Show usage and examples for the command, or list all commands.") {
			override fun handle(args: List<String>, event: MessageReceivedEvent) {
				val author = event.author
				val channel = event.channel
				if (args.isEmpty()) {
					event.channel.sendMessage("Available commands:\n"
						+ commands.values.asSequence()
						.filter { cmd -> cmd.canBeUsedBy(author) }
						.map { cmd -> getCmdHelpText(cmd) }
						.sorted()
						.distinct()
						.joinToString("\n")
					).queue()
					return
				}
				var cmdName = args.first().toLowerCase()
				if (cmdName.startsWith(cmdPrefix)) cmdName = cmdName.substring(cmdPrefix.length)
				val cmd = commands[cmdName]
				if (cmd == null) {
					channel.sendMessage(
						"Unknown command: " + codeSpan(args.first())
					).queue()
					return
				}
				if (!cmd.canBeUsedBy(author)) {
					channel.sendMessage(
						"No permission to use command: " + codeSpan(args.first())
					).queue()
					return
				}
				var text = getCmdHelpText(cmd)
				if (cmd.examples != null && cmd.examples.isNotEmpty()) {
					text += "\nExamples:\n    " + cmd.examples.replace("\n", "\n    ")
				}
				channel.sendMessage(text).queue()
			}

			private fun getCmdHelpText(cmd: Command): String {
				return codeSpan(cmdPrefix + cmd.usage) + " " + cmd.info
			}
		})
	}

	private fun registerCommand(command: Command) {
		commands[command.name.toLowerCase()] = command
	}

	override fun onMessageReceived(event: MessageReceivedEvent) {
		val channel = event.channel
		try {
			if (event.isWebhookMessage || event.author.isBot) return
			val author = event.author
			val contentRaw = event.message.contentRaw
			val hasPrefix = contentRaw.startsWith(cmdPrefix)
			// allow omitting prefix in private messaging channels
			if (channel !is PrivateChannel && !hasPrefix) return
			val cmdLineRaw = contentRaw.split("$|\n").first()
			var cmdLine = cmdLineRaw
			if (channel !is PrivateChannel) cmdLine = cmdLine.substring(cmdPrefix.length)
			val parts = cmdLine.split(" ")
			val command = commands[parts.first().toLowerCase()]
			if (command == null) {
				if (" " in cmdPrefix) {
					channel.sendMessage(":warning: Unknown command: " + codeSpan(cmdLineRaw)
						+ " Try " + codeSpan(cmdPrefix + "help") + " for a list of available commands."
					).queue()
				}
				// else: probably was not a command anyway, silently ignore
				return
			}
			if (!command.canBeUsedBy(author)) {
				if (" " in cmdPrefix) {
					channel.sendMessage(
						":warning: No permission to use command: " + codeSpan(cmdLineRaw)
					).queue()
				}
				// else: probably was not a command anyway, silently ignore
				return
			}
			val args = parts.drop(1)
			try {
				command.handle(args, event)
			} catch (e: UsageError) {
				event.channel.sendMessage(":warning: " + e.message +
					" Usage: " + codeSpan(cmdPrefix + command.usage)).queue()
			}
			val guildPrefix = if (event.isFromGuild) event.guild.toString() + "#" else "(PM) "
			logger.info("Ran command from message."
				+ " Author: " + italic(event.author.asTag)
				+ " Channel: " + italic(guildPrefix + event.channel.name + "(" + event.channel.id + ")")
				+ " Content: " + codeSpan(event.message.contentRaw))
		} catch (e: Exception) {
			e.printStackTrace()
			val guildPrefix = if (event.isFromGuild) event.guild.toString() + "#" else "(PM) "
			logger.warning("Failed handling Discord message. "
				+ escapeCodeSpan(e.toString())
				+ "\n Author: " + italic(event.author.asTag)
				+ " Channel: " + italic(guildPrefix + event.channel.name + "(" + event.channel.id + ")")
				+ " Content: " + codeSpan(event.message.contentRaw))
			channel.sendMessage(
				":warning: Error while running your command: " + codeSpan(e.toString())
			).queue()
		}
	}

	override fun onMessageReactionAdd(event: MessageReactionAddEvent) {
		if (event.reaction.isSelf) return
		val handler = reactionHandlers[event.user.id]
		if (handler != null && event.messageId == handler.messageId) {
			reactionHandlers.remove(event.user.id)
			handler.accept(event)
			return
		}
		val emoteName = event.reactionEmote.name
		if (event.channel.id != starChannelId && (emoteName == Emotes.star || emoteName == Emotes.joy || emoteName.all(Char::isLetterOrDigit))) {
			val starredMsg = event.channel.retrieveMessageById(event.reaction.messageId).complete()
			val reaction = starredMsg.reactions.firstOrNull { it.reactionEmote.name == emoteName } ?: return
			if (reaction.count >= 4) {
				starChannelId ?: return
				val starChannel = jda.getGuildChannelById(starChannelId)
				if (starChannel !is TextChannel) return // misconfigured
				val reactUsers = event.reaction.retrieveUsers().complete()
				if (reactUsers.contains(jda.selfUser)) {
					return // already posted to starChannel
				}
				event.channel.addReactionById(event.messageId, Emotes.star).complete()
				val embed = EmbedBuilder()
					.setColor(Color.YELLOW)
					.setAuthor(starredMsg.author.asTag, starredMsg.jumpUrl, starredMsg.author.avatarUrl)
					.setDescription(starredMsg.contentRaw)
					.setTimestamp(starredMsg.timeCreated)
				starredMsg.attachments.firstOrNull { it.isImage }
					?.also { embed.setImage(it.url) }
				if (event.reactionEmote.isEmote) {
					val emote = event.guild.retrieveEmote(event.reactionEmote.emote).complete()
					if (emote != null) embed.setFooter(zeroWidthSpace, emote.imageUrl)
				}
				starChannel.sendMessage(embed.build()).queue()
			}
			return
		}
	}

	private fun describeChannel(channel: MessageChannel): String {
		val guildPrefix = if (channel is TextChannel) channel.guild.toString() + "#" else "(PM)"
		return guildPrefix + channel.name + "(" + channel.id + ")"
	}

	private fun escape(s: String): String {
		return s.replace("_", "\\_")
			.replace("*", "\\*")
			.replace(":", "\\:")
			.replace("`", "\\`")
	}

	private fun escapeCodeSpan(s: String): String {
		return s.replace("`", "\\`")
	}

	private fun italic(s: String): String {
		return "*" + escape(s) + "*"
	}

	private fun bold(s: String): String {
		return "**" + escape(s) + "**"
	}

	private fun codeSpan(s: String): String {
		return "`" + escapeCodeSpan(s) + "`"
	}
}

private abstract class Command(val usage: String, val info: String) {
	val name: String = usage.split(" ").first()
	val examples: String? = null

	abstract fun handle(args: List<String>, event: MessageReceivedEvent)

	fun canBeUsedBy(user: User): Boolean {
		return true // XXX permission management
	}
}

private class UsageError(message: String) : Throwable(message)

private abstract class ReactionAddHandler(val messageId: String) {
	val createdAt = System.currentTimeMillis()

	abstract fun accept(event: MessageReactionAddEvent?)
}
