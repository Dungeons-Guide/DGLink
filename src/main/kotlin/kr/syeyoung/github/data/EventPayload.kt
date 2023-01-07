package kr.syeyoung.github.data

import kotlinx.serialization.Serializable


@Serializable
data class IssueCommentEvent(
    val action: String,
    val comment: Comment,
    val issue: Issue,
    val sender: User
)

@Serializable
data class IssueEvent(
    val action: String,
    val issue: Issue,
    val sender: User,
    val milestone: Milestone? = null,
    val assignee: User? = null,
    val label: Label? = null
)

@Serializable
data class LabelEvent(
    val action: String,
    val label: Label
)