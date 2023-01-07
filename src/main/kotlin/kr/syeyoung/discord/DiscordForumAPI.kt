package kr.syeyoung.discord

import dev.kord.common.entity.*
import dev.kord.common.entity.optional.Optional
import dev.kord.common.entity.optional.OptionalBoolean
import dev.kord.common.entity.optional.OptionalInt
import dev.kord.common.entity.optional.OptionalSnowflake
import dev.kord.common.serialization.DurationInSeconds
import dev.kord.rest.request.auditLogReason
import dev.kord.rest.route.Route
import dev.kord.rest.service.ChannelService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kr.syeyoung.github.GithubAPI
import kr.syeyoung.kord

@Serializable
public data class DiscordChannelForum(
    val id: Snowflake,
    val type: ChannelType,
    @SerialName("guild_id")
    val guildId: OptionalSnowflake = OptionalSnowflake.Missing,
    val position: OptionalInt = OptionalInt.Missing,
    @SerialName("permission_overwrites")
    val permissionOverwrites: Optional<List<Overwrite>> = Optional.Missing(),
    val name: Optional<String?> = Optional.Missing(),
    val topic: Optional<String?> = Optional.Missing(),
    val nsfw: OptionalBoolean = OptionalBoolean.Missing,
    @SerialName("last_message_id")
    val lastMessageId: OptionalSnowflake? = OptionalSnowflake.Missing,
    val bitrate: OptionalInt = OptionalInt.Missing,
    @SerialName("user_limit")
    val userLimit: OptionalInt = OptionalInt.Missing,
    @SerialName("rate_limit_per_user")
    val rateLimitPerUser: Optional<DurationInSeconds> = Optional.Missing(),
    val recipients: Optional<List<DiscordUser>> = Optional.Missing(),
    val icon: Optional<String?> = Optional.Missing(),
    @SerialName("owner_id")
    val ownerId: OptionalSnowflake = OptionalSnowflake.Missing,
    @SerialName("application_id")
    val applicationId: OptionalSnowflake = OptionalSnowflake.Missing,
    @SerialName("parent_id")
    val parentId: OptionalSnowflake? = OptionalSnowflake.Missing,
    @SerialName("last_pin_timestamp")
    val lastPinTimestamp: Optional<Instant?> = Optional.Missing(),
    @SerialName("rtc_region")
    val rtcRegion: Optional<String?> = Optional.Missing(),
    @SerialName("video_quality_mode")
    val videoQualityMode: Optional<VideoQualityMode> = Optional.Missing(),
    val permissions: Optional<Permissions> = Optional.Missing(),
    @SerialName("message_count")
    val messageCount: OptionalInt = OptionalInt.Missing,
    @SerialName("member_count")
    val memberCount: OptionalInt = OptionalInt.Missing,
    @SerialName("thread_metadata")
    val threadMetadata: Optional<DiscordThreadMetadata> = Optional.Missing(),
    @SerialName("default_auto_archive_duration")
    val defaultAutoArchiveDuration: Optional<ArchiveDuration> = Optional.Missing(),
    val member: Optional<DiscordThreadMember> = Optional.Missing(),
    @SerialName("available_tags")
    val availableTags: Optional<List<Tag>> = Optional.Missing(),
    @SerialName("applied_tags")
    val appliedTags: Optional<List<Snowflake>> = Optional.Missing()
)
@Serializable
data class Tag(
    val id: Optional<Snowflake> = Optional.Missing(),
    val name: String,
    val moderated: OptionalBoolean = OptionalBoolean.Missing,
)
@Serializable
public data class ForumChannelModifyPatchRequest(
    val name: Optional<String> = Optional.Missing(),
    val position: OptionalInt? = OptionalInt.Missing,
    val topic: Optional<String?> = Optional.Missing(),
    val nsfw: OptionalBoolean? = OptionalBoolean.Missing,
    @SerialName("rate_limit_per_user")
    val rateLimitPerUser: Optional<DurationInSeconds?> = Optional.Missing(),
    val bitrate: OptionalInt? = OptionalInt.Missing,
    @SerialName("user_limit")
    val userLimit: OptionalInt? = OptionalInt.Missing,
    @SerialName("permission_overwrites")
    val permissionOverwrites: Optional<Set<Overwrite>?> = Optional.Missing(),
    @SerialName("parent_id")
    val parentId: OptionalSnowflake? = OptionalSnowflake.Missing,
    val archived: OptionalBoolean = OptionalBoolean.Missing,
    @SerialName("auto_archive_duration")
    val autoArchiveDuration: Optional<ArchiveDuration> = Optional.Missing(),
    val locked: OptionalBoolean = OptionalBoolean.Missing,
    @SerialName("rtc_region")
    val rtcRegion: Optional<String?> = Optional.Missing(),
    val invitable: OptionalBoolean = OptionalBoolean.Missing,
    @SerialName("video_quality_mode")
    val videoQualityMode: Optional<VideoQualityMode?> = Optional.Missing(),
    @SerialName("default_auto_archive_duration")
    val defaultAutoArchiveDuration: Optional<ArchiveDuration?> = Optional.Missing(),
    @SerialName("available_tags")
    val availableTags: Optional<List<Tag>> = Optional.Missing(),
    @SerialName("applied_tags")
    val appliedTags: Optional<List<Snowflake>> = Optional.Missing()
)

@Serializable
public data class ForumThreadModifyPatchRequest(
    val name: Optional<String> = Optional.Missing(),
    val position: OptionalInt? = OptionalInt.Missing,
    val topic: Optional<String?> = Optional.Missing(),
    val nsfw: OptionalBoolean? = OptionalBoolean.Missing,
    @SerialName("rate_limit_per_user")
    val rateLimitPerUser: Optional<DurationInSeconds?> = Optional.Missing(),
    val bitrate: OptionalInt? = OptionalInt.Missing,
    @SerialName("user_limit")
    val userLimit: OptionalInt? = OptionalInt.Missing,
    @SerialName("permission_overwrites")
    val permissionOverwrites: Optional<Set<Overwrite>?> = Optional.Missing(),
    @SerialName("parent_id")
    val parentId: OptionalSnowflake? = OptionalSnowflake.Missing,
    val archived: OptionalBoolean = OptionalBoolean.Missing,
    @SerialName("auto_archive_duration")
    val autoArchiveDuration: Optional<ArchiveDuration> = Optional.Missing(),
    val locked: OptionalBoolean = OptionalBoolean.Missing,
    @SerialName("rtc_region")
    val rtcRegion: Optional<String?> = Optional.Missing(),
    val invitable: OptionalBoolean = OptionalBoolean.Missing,
    @SerialName("video_quality_mode")
    val videoQualityMode: Optional<VideoQualityMode?> = Optional.Missing(),
    @SerialName("default_auto_archive_duration")
    val defaultAutoArchiveDuration: Optional<ArchiveDuration?> = Optional.Missing(),
    @SerialName("applied_tags")
    val appliedTags: Optional<List<Snowflake>> = Optional.Missing()
)

suspend fun ChannelService.patchForum(
    channelId: Snowflake,
    channel: ForumChannelModifyPatchRequest,
    reason: String? = null,
): DiscordChannel = kord.rest.unsafe(Route.ChannelPatch) {
    keys[Route.ChannelId] = channelId
    body(ForumChannelModifyPatchRequest.serializer(), channel)
    auditLogReason(reason)
}
suspend fun ChannelService.patchForumThread(
    channelId: Snowflake,
    channel: ForumThreadModifyPatchRequest,
    reason: String? = null,
): DiscordChannel = kord.rest.unsafe(Route.ChannelPatch) {
    keys[Route.ChannelId] = channelId
    body(ForumThreadModifyPatchRequest.serializer(), channel)
    auditLogReason(reason)
}

suspend fun ChannelService.getForumChannel(
    channelId: Snowflake
): DiscordChannelForum = GithubAPI.client.get("https://discord.com/api/v10/channels/${channelId}") {
    accept(ContentType.parse("application/json"))
    userAgent("DiscordBot (blahblah, 1.0)")
    header(HttpHeaders.Authorization, "Bot ${kord.resources.token}")
}.body()