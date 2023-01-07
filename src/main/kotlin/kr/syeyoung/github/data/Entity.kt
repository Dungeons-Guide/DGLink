package kr.syeyoung.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    @SerialName("avatar_url") val avatarUrl: String,
    val deleted: Boolean = false,
    val email: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val id: Long,
    val login: String
)

@Serializable
data class Label(
    val color: String,
    val default: Boolean,
    val description: String? = null,
    val id: Long,
    val name: String
)
@Serializable
data class Issue(
    @SerialName("active_lock_reason") val activeLockReason: String? = null,
    val assignees: List<User?> = listOf(),
    val body: String? = null,
    val comments: Long,
    @SerialName("created_at") val createdAt: String,
    val draft: Boolean = false,
    @SerialName("html_url") val htmlUrl: String,
    val id: Long,
    val labels: List<Label> = listOf(),
    val locked: Boolean = false,
    val number: Long,
    @SerialName("performed_via_github_app") val performedViaGithubApp: GithubApp? = null,
    val state: String,
    @SerialName("state_reason") val stateReason: String? = null,
    val title: String,
    val url: String,
    val user: User,
    val milestone: Milestone? = null
)

@Serializable
data class GithubApp(
    val id: Long
)
@Serializable
data class Comment(
    @SerialName("author_association") val authorAssociation: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("html_url") val htmlUrl: String,
    val id: Long,
    @SerialName("performed_via_github_app") val performedViaGithubApp: GithubApp? = null,
    @SerialName("updated_at") val updatedAt: String,
    val user: User
)

@Serializable
data class Milestone(
    val id: Long,
    @SerialName("html_url") val htmlUrl: String,
    val state: String,
    val title: String
)