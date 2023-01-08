package kr.syeyoung.discord

import dev.kord.core.Kord
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.execute
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.event.channel.ChannelUpdateEvent
import dev.kord.core.event.channel.thread.ThreadChannelCreateEvent
import dev.kord.core.event.channel.thread.ThreadChannelDeleteEvent
import dev.kord.core.event.channel.thread.ThreadUpdateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.core.on
import kotlinx.coroutines.flow.first
import kr.syeyoung.*
import kr.syeyoung.github.GithubAPI
import java.awt.TrayIcon.MessageType
import java.util.OptionalInt


fun Kord.registerHandlers() {
    this.on<MessageCreateEvent> {
        if (this.member?.id == kord.selfId) return@on;
        if (this.message.author == null) return@on;
        EventHandler.onMessageCreate(this)
    }
    this.on<MessageDeleteEvent> {
        EventHandler.onMessageDelete(this)
    }
    this.on<MessageUpdateEvent> {
        if (this.getMessage().author?.id == kord.selfId) return@on;
        EventHandler.onMessageUpdate(this)
    }
    this.on<ThreadChannelCreateEvent> {
        if (this.channel.ownerId == kord.selfId) return@on;
        if (!channelIdMap.containsKey(this.channel.parentId)) return@on;
        EventHandler.onThreadChannelCreate(this)
    }
    this.on<ThreadChannelDeleteEvent> {
        if (!channelIdMap.containsKey(this.channel.parentId)) return@on;
        EventHandler.onThreadChannelDelete(this)
    }
    this.on<ThreadUpdateEvent> {
        if (!channelIdMap.containsKey(this.channel.parentId)) return@on;
        EventHandler.onThreadChannelUpdate(this)
    }
}
object EventHandler {
    suspend fun onMessageCreate(event: MessageCreateEvent) {
        if (event.message.type != dev.kord.common.entity.MessageType.Default) return;

        val threadId = LinkManager.getThreadIdByDiscord(event.message.channel.asChannelOf<ThreadChannel>().parentId,
            event.message.channelId);

        val id = GithubAPI.createComment(threadId, """
            [`${event.member?.tag} | ${event.member?.displayName}`](https://discordapp.com/channels/@me/${event.member?.id}): ${event.message.content}
            ${event.message.attachments.map { "${if (it.isImage)  "!" else ""}[${it.filename}](${it.url})" }.joinToString("\n") }
        """.trimIndent());

        LinkManager.linkMessage(threadId, id, event.message.channelId, event.message.id)
    }
    suspend fun onMessageUpdate(event: MessageUpdateEvent) {
        val tgithub = LinkManager.getGithubIdByTDiscord(event.message.channelId, event.messageId);
        if (tgithub != null) {
            val member = event.getMessage().getAuthorAsMember();
            GithubAPI.recontent(tgithub, """
            [`${member?.tag} | ${member?.displayName}`](https://discordapp.com/channels/@me/${member?.id}): ${event.new.content.value}
            ${event.new.attachments.value?.map { "![${it.filename}](${it.proxyUrl})" }?.joinToString("\n") }
        """.trimIndent());
        } else {
            val threadId = LinkManager.getGithubIdByDiscord(event.message.channelId, event.messageId);

            val member = event.getMessage().getAuthorAsMember();
            GithubAPI.editComment(threadId.first, threadId.second, """
            [`${member?.tag} | ${member?.displayName}`](https://discordapp.com/channels/@me/${member?.id}): ${event.new.content.value}
            ${event.new.attachments.value?.map { "${if (it.height != dev.kord.common.entity.optional.OptionalInt.Missing)  "!" else ""}[${it.filename}](${it.url})" }?.joinToString("\n") }
        """.trimIndent());
        }
    }
    suspend fun onMessageDelete(event: MessageDeleteEvent) {
        val threadId = LinkManager.getGithubIdByDiscord(event.channelId, event.messageId);

        GithubAPI.deleteComment(threadId.first, threadId.second);
    }

    suspend fun onThreadChannelCreate(threadChannelCreateEvent: ThreadChannelCreateEvent) {
        val name = threadChannelCreateEvent.channel.owner.asMember(kord.guilds.first().id);
        val firstMsg = threadChannelCreateEvent.channel.messages.first();

        val targetConfiguration = channelIdMap.get(threadChannelCreateEvent.channel.parentId) ?: return;

        val tags = kord.rest.channel.getForumChannel(threadChannelCreateEvent.channel.parentId).availableTags.value!!;
        var tagStr = kord.rest.channel.getForumChannel(threadChannelCreateEvent.channel.id).appliedTags
            .value.orEmpty().map { a -> tags.find { b -> b.id.value == a } }
            .map { it?.name ?: "" }
        tagStr = (tagStr.toSet() + targetConfiguration.tags).toList()


        val id = GithubAPI.createIssue(threadChannelCreateEvent.channel.name,
            """
            [`${name.tag} | ${name.displayName}`](https://discordapp.com/channels/@me/${name.id}): ${firstMsg.content} 
                ${firstMsg.attachments.map { "${if (it.isImage)  "!" else ""}[${it.filename}](${it.url})" }.joinToString("\n")}
            """.trimIndent(), tagStr
        );

        LinkManager.makeLink(id.number, parentId = threadChannelCreateEvent.channel.parentId, threadId = threadChannelCreateEvent.channel.id);
        LinkManager.linkTMessage(id.number, threadChannelCreateEvent.channel.id, firstMsg.id)

        GithubAPI.createComment(id.number, "This issue has been linked to discord forum thread! [Here](https://discord.com/channels/${threadChannelCreateEvent.channel.parentId}/${threadChannelCreateEvent.channel.id})");

        targetConfiguration.webhook.execute(targetConfiguration.webhookSecret, threadId = threadChannelCreateEvent.channel.id) {
            this.content = "This issue has been linked to github issues! [Here](https://github.com/Dungeons-Guide/Skyblock-Dungeons-Guide/issues/${id.number})"
            this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
            this.username = "Github Issues"
        }

    }

    suspend fun onThreadChannelDelete(threadChannelDeleteEvent: ThreadChannelDeleteEvent) {
        print("Can't delete issue")
    }

    suspend fun onThreadChannelUpdate(threadUpdateEvent: ThreadUpdateEvent) {
        val targetConfiguration = channelIdMap.get(threadUpdateEvent.channel.parentId) ?: return;

        val tags = kord.rest.channel.getForumChannel(threadUpdateEvent.channel.parentId).availableTags.value!!;
        val tagStr = (kord.rest.channel.getForumChannel(threadUpdateEvent.channel.id).appliedTags
            .value.orEmpty().map { a -> tags.find { b -> b.id.value == a } }
            .map { it?.name ?: "" }).toSet() + targetConfiguration.tags

        GithubAPI.retitle(LinkManager.getThreadIdByDiscord(threadUpdateEvent.channel.parentId, threadUpdateEvent.channel.id),
            threadUpdateEvent.channel.name,
            tagStr.toList()
        );
    }
}