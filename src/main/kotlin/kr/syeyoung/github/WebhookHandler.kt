package kr.syeyoung.github

import dev.kord.common.Color
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.AllowedMentions
import dev.kord.common.entity.DiscordComponent
import dev.kord.common.entity.DiscordMessage
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.*
import dev.kord.core.behavior.execute
import dev.kord.rest.NamedFile
import dev.kord.rest.builder.message.create.WebhookMessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.json.request.ChannelModifyPatchRequest
import dev.kord.rest.json.request.EmbedRequest
import dev.kord.rest.route.Route
import dev.kord.rest.service.WebhookService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kr.syeyoung.*
import kr.syeyoung.discord.ForumThreadModifyPatchRequest
import kr.syeyoung.discord.getForumChannel
import kr.syeyoung.discord.patchForumThread
import kr.syeyoung.github.data.IssueCommentEvent
import kr.syeyoung.github.data.IssueEvent
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


@Serializable
public data class WebhookExecuteRequest2(
    val content: Optional<String> = Optional.Missing(),
    val username: Optional<String> = Optional.Missing(),
    @SerialName("avatar_url")
    val avatar: Optional<String> = Optional.Missing(),
    val tts: OptionalBoolean = OptionalBoolean.Missing,
    val embeds: Optional<List<EmbedRequest>> = Optional.Missing(),
    @SerialName("allowed_mentions")
    val allowedMentions: Optional<AllowedMentions> = Optional.Missing(),
    val components: Optional<List<DiscordComponent>> = Optional.Missing(),
    @SerialName("thread_name")
    var threadName: Optional<String> = Optional.Missing(),
    @SerialName("applied_tags")
    var appliedTags: Optional<List<Snowflake>> = Optional.Missing()
)


public data class MultiPartWebhookExecuteRequest2(
    val request: WebhookExecuteRequest2,
    val files: List<NamedFile> = emptyList(),
)
fun WebhookMessageCreateBuilder.toRequest2(): MultiPartWebhookExecuteRequest2 {
    return MultiPartWebhookExecuteRequest2(
        WebhookExecuteRequest2(
            content = Optional(content).coerceToMissing(),
            username = Optional(username).coerceToMissing(),
            avatar = Optional(avatarUrl).coerceToMissing(),
            tts = Optional(tts).coerceToMissing().toPrimitive(),
            embeds = Optional(embeds).mapList { it.toRequest() },
            allowedMentions = Optional(allowedMentions).coerceToMissing().map { it.build() },
            components = Optional(components).coerceToMissing().mapList { it.build() }
        ),
        files
    )
}
@OptIn(ExperimentalContracts::class, KordUnsafe::class)
public suspend fun WebhookService.executeWebhookCreatingThread(
    webhookId: Snowflake,
    token: String,
    wait: Boolean? = null,
    threadName: String,
    tags: List<Snowflake>,
    builder: WebhookMessageCreateBuilder.() -> Unit
): DiscordMessage? {
    contract {
        callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
    }

    return kord.rest.unsafe(Route.ExecuteWebhookPost) {
        keys[Route.WebhookId] = webhookId
        keys[Route.WebhookToken] = token
        wait?.let { parameter("wait", it) }
        val request = WebhookMessageCreateBuilder().apply(builder).toRequest2()
        request.request.threadName = Optional(threadName)
        request.request.appliedTags = Optional(tags)
        body(WebhookExecuteRequest2.serializer(), request.request)
        request.files.forEach { file(it) }
    }
}
class WebhookHandler {
    companion object {
        suspend fun onIssueOpened(element: IssueEvent) {
            val tags = kord.rest.channel.getForumChannel(forumChannel.id).availableTags.value!!;
            println(tags)
            val tag = tags.filter { a -> element.issue.labels.any { it.name == a.name } }
                .map { it.id.value!! }
            println(tag)

            val message = webhook.kord.rest.webhook.executeWebhookCreatingThread(
                webhookId = webhook.id,
                token = webhook_token,
                wait = true,
                threadName = element.issue.title,
                builder = {
                    this.content = element.issue.body
                    this.avatarUrl = element.issue.user.avatarUrl
                    this.username = element.issue.user.login
                },
                tags = tag
            )!!;
            LinkManager.makeLink(element.issue.number, message.id);

            GithubAPI.createComment(element.issue.number, "This issue has been linked to discord forum thread! [Here](https://discord.com/channels/${message.thread.value?.parentId?.value}/${message.thread.value?.id})");

            webhook.execute(webhook_token, threadId = message.id) {
                this.content = "This issue has been linked to github issues! [Here](${element.issue.htmlUrl})"
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }

            kord.rest.channel.patchForumThread(message.id, ForumThreadModifyPatchRequest(appliedTags = tag.optional()));
        }
        suspend fun onIssueDeleted(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            LinkManager.unLink(element.issue.number, thread );
            kord.rest.channel.deleteChannel(thread)
        }
        suspend fun onIssueEdited(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);
            kord.rest.channel.patchThread(thread, ChannelModifyPatchRequest(name = Optional(element.issue.title)), reason = "github update", )
            val root: Pair<Long, Long>? = LinkManager.getDiscordIdByTGithub(element.issue.number);
            if (root != null) {
                kord.rest.webhook.editWebhookMessage(
                    webhookId = webhook.id,
                    webhook_token,
                    threadId = Snowflake( root.first),
                    messageId = Snowflake(root.second)
                ) {
                    content = element.issue.body
                }
            }
        }
        suspend fun onIssueLocked(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);

            webhook.execute(webhook_token, threadId = thread) {
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

            kord.rest.channel.patchThread(thread, ChannelModifyPatchRequest(locked = element.issue.locked.optional(), archived = (element.issue.state == "closed").optional()))
        }
        suspend fun onIssueUnlocked(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);

            webhook.execute(webhook_token, threadId = thread) {
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

            kord.rest.channel.patchThread(thread, ChannelModifyPatchRequest(locked = element.issue.locked.optional(), archived = (element.issue.state == "closed").optional()))
        }
        suspend fun onIssueReopened(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);

            webhook.execute(webhook_token, threadId = thread) {
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

            kord.rest.channel.patchThread(thread, ChannelModifyPatchRequest(locked = element.issue.locked.optional(), archived = (element.issue.state == "closed").optional()))
        }
        suspend fun onIssueClosed(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);

            webhook.execute(webhook_token, threadId = thread) {
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

            kord.rest.channel.patchThread(thread, ChannelModifyPatchRequest(locked = element.issue.locked.optional(), archived = (element.issue.state == "closed").optional()))
        }
        suspend fun onIssueLabeled(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number)


            webhook.execute(webhook_token, threadId = thread) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(0,255,0);
                    this.title = "Label Assigned"
                    this.description= element.label?.name
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }

            // put tags?
            val tags = kord.rest.channel.getForumChannel(forumChannel.id).availableTags.value!!;
            val currentTags = kord.rest.channel.getForumChannel(thread);
            if ((currentTags.appliedTags.value?.size ?: 100) >= 5) return

            val tag = tags.find { element.label?.name == it.name } ?: return;
            kord.rest.channel.patchForumThread(thread, ForumThreadModifyPatchRequest(appliedTags = Optional(
                mutableListOf<Snowflake>() + currentTags.appliedTags.value!! + tag.id.value!!
            ))
            )
        }
        suspend fun onIssueUnlabeled(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number)


            webhook.execute(webhook_token, threadId = thread) {
                this.embed {
                    author {
                        this.icon = element.sender.avatarUrl;
                        this.name = element.sender.login;
                        this.url = element.sender.htmlUrl;
                    }
                    this.color = Color(255,255,0);
                    this.title = "Label Removed"
                    this.description= element.label?.name
                }
                this.avatarUrl = "https://avatars.githubusercontent.com/u/87838487?s=80&v=4"
                this.username = "Github Issues"
            }
            // put tags?
            val tags = kord.rest.channel.getForumChannel(forumChannel.id).availableTags.value!!;
            val currentTags = kord.rest.channel.getForumChannel(thread);
            if ((currentTags.appliedTags.value?.size ?: 100) >= 5) return

            val tag = tags.find { element.label?.name == it.name } ?: return;
            if (!(currentTags.appliedTags.value.orEmpty().contains(tag.id.value))) return;

            kord.rest.channel.patchForumThread(thread, ForumThreadModifyPatchRequest(appliedTags = Optional(
                mutableListOf<Snowflake>() + currentTags.appliedTags.value.orEmpty() - tag.id.value!!
            ))
            )
        }
        suspend fun onIssuePinned(element: IssueEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);

            webhook.execute(webhook_token, threadId = thread) {
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

            webhook.execute(webhook_token, threadId = thread) {
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

            webhook.execute(webhook_token, threadId = thread) {
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

            webhook.execute(webhook_token, threadId = thread) {
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

            webhook.execute(webhook_token, threadId = thread) {
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

            webhook.execute(webhook_token, threadId = thread) {
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

            webhook.execute(webhook_token, threadId = thread) {
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
            kord.rest.channel.patchThread(thread, ChannelModifyPatchRequest(locked = true.optional(), archived = true.optional()))
        }

        suspend fun onCommentCreated(element: IssueCommentEvent) {
            val thread = LinkManager.getThreadIdByGithub(element.issue.number);

            val msg = webhook.execute(webhook_token, threadId = thread) {
                this.avatarUrl = element.sender.avatarUrl
                this.username = element.sender.login
                this.content = element.comment.body
            }

            LinkManager.linkMessage(element.issue.number, element.comment.id, thread, msg.id);
        }
        suspend fun onCommentDeleted(element: IssueCommentEvent) {
            val message = LinkManager.getMessageIdByGithub(element.issue.number , element.comment.id)

            kord.rest.channel.deleteMessage(message.first, messageId = message.second)
        }
        suspend fun onCommentEdited(element: IssueCommentEvent) {
            val message = LinkManager.getMessageIdByGithub(element.issue.number, element.comment.id)

            kord.rest.webhook.editWebhookMessage(
                webhookId = webhook.id,
                token = webhook_token,
                messageId = message.second,
                threadId = message.first
            ) {
                this.content = element.comment.body
            }
        }
    }


}