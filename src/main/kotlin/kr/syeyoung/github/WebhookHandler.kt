package kr.syeyoung.github

import dev.kord.common.Color
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.AllowedMentions
import dev.kord.common.entity.DiscordComponent
import dev.kord.common.entity.DiscordMessage
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.*
import dev.kord.core.behavior.channel.asChannelOf
import dev.kord.core.behavior.execute
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.message.create.WebhookMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.embed
import dev.kord.rest.json.request.ChannelModifyPatchRequest
import dev.kord.rest.json.request.EmbedRequest
import dev.kord.rest.route.Route
import dev.kord.rest.service.WebhookService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kr.syeyoung.*
import kr.syeyoung.github.data.IssueCommentEvent
import kr.syeyoung.github.data.IssueEvent
import kr.syeyoung.github.data.Label
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


fun findSuitableChannel(tags: List<Label>): LinkedChannel {
    for (tag in tags) {
        val res = tagMap[tag.name];
        if (res != null) return res;
    }
    return defaultchannel;
}


suspend fun recreateIssue(number: Long): Pair<Snowflake, Snowflake> {
    val issue = GithubAPI.getIssue(number);

    val dscdChannel = findSuitableChannel(issue.labels);

    val tags = kord.rest.channel.getChannel(dscdChannel.channelId).availableTags.value!!;
    println(tags)
    val tag = tags.filter { a -> issue.labels.any { it.name == a.name } }
        .map { it.id }
    println(tag)

    val message = dscdChannel.webhook.kord.rest.webhook.executeWebhook(
        webhookId = dscdChannel.webhookId,
        token = dscdChannel.webhookSecret,
        wait = true,
        builder = {
            this.threadName = issue.title
            this.content = issue.body
            this.avatarUrl = issue.user.avatarUrl
            this.username = issue.user.login
        }
    )!!;

    kord.rest.channel.patchThread(
        message.id, ChannelModifyPatchRequest(
            appliedTags = Optional(
                mutableListOf<Snowflake>() + tag
            )
        )
    )
    LinkManager.makeLink(issue.number, dscdChannel.channelId, message.id);
    dscdChannel.webhook.execute(dscdChannel.webhookSecret, threadId = message.id) {
        this.content = "This issue has been linked to github issues! [Here](${issue.htmlUrl})"
        this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
        this.username = "Github Issues"
    }

    val comments = GithubAPI.getComments(number)
    for (comment in comments) {
        if (comment.user.htmlUrl == "https://github.com/apps/dungeons-guide-issue-tracker"
            && !comment.body.startsWith("[")) continue
        val msg = dscdChannel.webhook.execute(dscdChannel.webhookSecret, threadId = message.id) {
            this.avatarUrl = comment.user.avatarUrl
            this.username = comment.user.login
            this.content = comment.body
        }

        LinkManager.linkMessage(issue.number, comment.id, message.id, msg.id);
    }
    GithubAPI.createComment(issue.number, "This issue has been relinked to new discord forum thread! [Here](https://discord.com/channels/${dscdChannel.webhook.guildId}/${message.channelId})");

    return Pair(dscdChannel.channelId, message.id)
}

class WebhookHandler {
    companion object {
        suspend fun onIssueOpened(element: IssueEvent) {
            val dscdChannel = findSuitableChannel(element.issue.labels);

            val tags = kord.rest.channel.getChannel(dscdChannel.channelId).availableTags.value!!;
            println(tags)
            val tag = tags.filter { a -> element.issue.labels.any { it.name == a.name } }
                .map { it.id }
            println(tag)

            val message = dscdChannel.webhook.kord.rest.webhook.executeWebhook(
                webhookId = dscdChannel.webhookId,
                token = dscdChannel.webhookSecret,
                wait = true,
                builder = {
                    this.threadName = element.issue.title
                    this.content = element.issue.body
                    this.avatarUrl = element.issue.user.avatarUrl
                    this.username = element.issue.user.login
                }
            )!!;
            kord.rest.channel.patchThread(
                message.id, ChannelModifyPatchRequest(
                    appliedTags = Optional(
                        mutableListOf<Snowflake>() + tag
                    )
                )
            )
            LinkManager.makeLink(element.issue.number, dscdChannel.channelId, message.id);

            GithubAPI.createComment(element.issue.number, "This issue has been linked to discord forum thread! [Here](https://discord.com/channels/${dscdChannel.webhook.guildId}/${message.channelId})");

            dscdChannel.webhook.execute(dscdChannel.webhookSecret, threadId = message.id) {
                this.content = "This issue has been linked to github issues! [Here](${element.issue.htmlUrl})"
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }

            kord.rest.channel.patchThread(message.id, ChannelModifyPatchRequest(appliedTags = tag.optional()));
        }
        suspend fun onIssueDeleted(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            LinkManager.unLink(element.issue.number, thread.first, thread.second );
            kord.rest.channel.deleteChannel(thread.second)
        }
        suspend fun onIssueEdited(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);

            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")


            kord.rest.channel.patchThread(thread.second, ChannelModifyPatchRequest(name = Optional(element.issue.title)), reason = "github update", )
            val root: Pair<Long, Long>? = LinkManager.getDiscordIdByTGithub(element.issue.number);
            if (root != null) {
                kord.rest.webhook.editWebhookMessage(
                    webhookId = channel.webhookId,
                    channel.webhookSecret,
                    threadId = Snowflake( root.first),
                    messageId = Snowflake(root.second)
                ) {
                    content = element.issue.body
                }
            }
        }
        suspend fun onIssueLocked(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(255,0,0);
                    this.title = "This issue has been locked!"
                    this.description = "Reason: ${element.issue.activeLockReason}"
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }

            kord.rest.channel.patchThread(thread.second, ChannelModifyPatchRequest(locked = element.issue.locked.optional(), archived = (element.issue.state == "closed").optional()))
        }
        suspend fun onIssueUnlocked(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(0,255,0);
                    this.title = "This issue has been unlocked!"
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }

            kord.rest.channel.patchThread(thread.second, ChannelModifyPatchRequest(locked = element.issue.locked.optional(), archived = (element.issue.state == "closed").optional()))
        }
        suspend fun onIssueReopened(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(0,255,0);
                    this.title = "This issue has been reopened!"

                    if (element.issue.stateReason != null)
                        this.description = "Reason: ${element.issue.stateReason}"
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }

            kord.rest.channel.patchThread(thread.second, ChannelModifyPatchRequest(locked = element.issue.locked.optional(), archived = (element.issue.state == "closed").optional()))
        }
        suspend fun onIssueClosed(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(255,0,0);
                    this.title = "This issue has been closed!"
                    if (element.issue.stateReason != null)
                        this.description = "Reason: ${element.issue.stateReason}"
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }

            kord.rest.channel.patchThread(thread.second, ChannelModifyPatchRequest(locked = element.issue.locked.optional(), archived = (element.issue.state == "closed").optional()))
        }
        suspend fun onIssueLabeled(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number)
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            val shouldBeInChannel = findSuitableChannel(element.issue.labels);
            if (shouldBeInChannel == channel) {
                channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                    this.embed {
                        author {
                            this.icon = element.sender.avatarUrl;
                            this.name = element.sender.login;
                            this.url = element.sender.htmlUrl;
                        }
                        this.color = Color(0, 255, 0);
                        this.title = "Label Assigned"
                        this.description = element.label?.name
                    }
                    this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                    this.username = "Github Issues"
                }

                // put tags?
                val tags = kord.rest.channel.getChannel(channel.channelId).availableTags.value!!;
                val currentTags = kord.rest.channel.getChannel(thread.second).appliedTags.value;
                if ((currentTags?.size ?: 100) >= 5) return

                val tag = tags.find { element.label?.name == it.name } ?: return;
                kord.rest.channel.patchThread(
                    thread.second, ChannelModifyPatchRequest(
                        appliedTags = Optional(
                            mutableListOf<Snowflake>() + currentTags!! + tag.id
                        )
                    )
                )
            } else {
                kord.rest.channel.deleteChannel(thread.second, "Tag Changed: Applying rule ${channel.configurationName}")
                recreateIssue(element.issue.number)
            }
        }
        suspend fun onIssueUnlabeled(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number)
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")
            val shouldBeInChannel = findSuitableChannel(element.issue.labels);
            if (shouldBeInChannel == channel) {
                channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                    this.embed {
                        author {
                            this.icon = element.sender.avatarUrl;
                            this.name = element.sender.login;
                            this.url = element.sender.htmlUrl;
                        }
                        this.color = Color(255, 255, 0);
                        this.title = "Label Removed"
                        this.description = element.label?.name
                    }
                    this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                    this.username = "Github Issues"
                }
                // put tags?
                val tags = kord.rest.channel.getChannel(channel.channelId).availableTags.value!!;
                val currentTags = kord.rest.channel.getChannel(thread.second).appliedTags.value;
                if ((currentTags?.size ?: 100) >= 5) return

                val tag = tags.find { element.label?.name == it.name } ?: return;
                if (!(currentTags.orEmpty().contains(tag.id))) return;

                kord.rest.channel.patchThread(
                    thread.second, ChannelModifyPatchRequest(
                        appliedTags = Optional(
                            mutableListOf<Snowflake>() + currentTags.orEmpty() - tag.id
                        )
                    )
                )
            } else {
                kord.rest.channel.deleteChannel(thread.second, "Tag Changed: Applying rule ${channel.configurationName}")
                recreateIssue(element.issue.number)
            }
        }


        suspend fun onIssuePinned(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(0,255,0);
                    this.title = "This issue has been pinned!"
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }
        }
        suspend fun onIssueUnpinned(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(255,255,0);
                    this.title = "This issue has been unpinned!"
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }
        }
        suspend fun onIssueMilestoned(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(0,255,0);
                    this.title = "Milestone added!"
                    this.description = element.issue.milestone?.title;
                    footer {
                        this.text = "[link](${element.issue.milestone?.htmlUrl})"
                    }
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }
        }
        suspend fun onIssueDemilestoned(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(255,255,0);
                    this.title = "Milestone removed"
                    this.description = element.milestone?.title;
                    footer {
                        this.text = "[link](${element.milestone?.htmlUrl})"
                    }
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }
        }
        suspend fun onIssueAssigned(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(0,255,0);
                    this.title = "This issue has been assigned to"
                    this.description = element.assignee?.login;
                    footer {
                        this.icon = element.assignee?.avatarUrl
                        this.text = "${element.assignee?.login} [link](${element.assignee?.htmlUrl})"
                    }
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }
        }
        suspend fun onIssueUnassigned(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(255,255,0);
                    this.title = "This issue has been unassigned tfrom"
                    this.description = element.assignee?.login;
                    footer {
                        this.icon = element.assignee?.avatarUrl
                        this.text = "${element.assignee?.login} [link](${element.assignee?.htmlUrl})"
                    }
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }
        }
        suspend fun onIssueTransferred(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(255,255,0);
                    this.title = "This issue has been transferred to another repository (???)"
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }
            kord.rest.channel.patchThread(thread.second, ChannelModifyPatchRequest(locked = true.optional(), archived = true.optional()))
        }

        suspend fun onCommentCreated(element: IssueCommentEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            val channel = channelIdMap[thread.first] ?: throw IllegalStateException("$thread is not registered?")

            val msg = channel.webhook.execute(channel.webhookSecret, threadId = thread.second) {
                this.avatarUrl = element.sender.avatarUrl
                this.username = element.sender.login
                this.content = element.comment.body
            }

            LinkManager.linkMessage(element.issue.number, element.comment.id, thread.second, msg.id);
        }
        suspend fun onCommentDeleted(element: IssueCommentEvent) {
            val message = LinkManager.getMessageIdByGithub(element.issue.number , element.comment.id)

            kord.rest.channel.deleteMessage(message.first, messageId = message.second)
        }
        suspend fun onCommentEdited(element: IssueCommentEvent) {
            val message = LinkManager.getMessageIdByGithub(element.issue.number, element.comment.id)
            val parent = (kord.getChannel(message.first) as ThreadChannel).parentId;
            val channel = channelIdMap[
                parent
            ] ?: throw IllegalStateException("${message.first}'s parent Channel ${parent} not registered?")

            kord.rest.webhook.editWebhookMessage(
                webhookId = channel.webhook.id,
                token = channel.webhookSecret,
                messageId = message.second,
                threadId = message.first
            ) {
                this.content = element.comment.body
            }
        }
    }


}