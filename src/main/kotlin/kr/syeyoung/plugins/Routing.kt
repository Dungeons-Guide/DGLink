package kr.syeyoung.plugins

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kr.syeyoung.github.WebhookHandler
import kr.syeyoung.github.data.IssueCommentEvent
import kr.syeyoung.github.data.IssueEvent
import kr.syeyoung.github.verifyHmac

fun Application.configureRouting() {
    routing {
        post("/webhook") {
            // check signature
            val hmac = call.request.header("X-Hub-Signature-256") ?: throw IllegalArgumentException("Invalid signatyre")
            val body = call.receiveText()
            if (!verifyHmac(hmac.split("=")[1], body)) throw IllegalArgumentException("Invalid Signature")

            val responsible = call.request.header("X-Github-Event");

            when (responsible) {
                "issue_comment" -> {
                    val event = call.receive<IssueCommentEvent>()
                    if (event.sender.htmlUrl.equals("https://github.com/apps/dungeons-guide-issue-tracker")) return@post
                    when (event.action) {
                        "created" -> WebhookHandler.onCommentCreated(event)
                        "edited" -> WebhookHandler.onCommentEdited(event)
                        "deleted" -> WebhookHandler.onCommentDeleted(event)
                        else -> print("Unknown issue comment event:: ${event.action}")
                    }
                }
                "issues" -> {
                    val event = call.receive<IssueEvent>()
                    if (event.sender.htmlUrl.equals("https://github.com/apps/dungeons-guide-issue-tracker")) return@post
                    when (event.action) {
                        "assigned" -> WebhookHandler.onIssueAssigned(event)
                        "closed" -> WebhookHandler.onIssueClosed(event)
                        "deleted" -> WebhookHandler.onIssueDeleted(event)
                        "demilestoned" -> WebhookHandler.onIssueDemilestoned(event)
                        "edited" -> WebhookHandler.onIssueEdited(event)
                        "labeled" -> WebhookHandler.onIssueLabeled(event)
                        "locked" -> WebhookHandler.onIssueLocked(event)
                        "milestoned" -> WebhookHandler.onIssueMilestoned(event)
                        "opened" -> WebhookHandler.onIssueOpened(event)
                        "pinned" -> WebhookHandler.onIssuePinned(event)
                        "reopened" -> WebhookHandler.onIssueReopened(event)
                        "transferred" -> WebhookHandler.onIssueTransferred(event)
                        "unassigned" -> WebhookHandler.onIssueUnassigned(event)
                        "unlabeled" -> WebhookHandler.onIssueUnlabeled(event)
                        "unlocked" -> WebhookHandler.onIssueUnlocked(event)
                        "unpinned" -> WebhookHandler.onIssueUnpinned(event)
                        else -> print("Unknown issue event:: ${event.action}")
                    }
                }
            }
        }
    }
}
