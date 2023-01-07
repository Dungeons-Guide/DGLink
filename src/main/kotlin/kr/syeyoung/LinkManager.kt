package kr.syeyoung

import dev.kord.common.entity.Snowflake

object LinkManager {
    suspend fun makeLink(issueNum: Long, threadId: Snowflake) {
        redisClient.set("link.github.$issueNum", threadId.toString())
        redisClient.set("link.discord.$threadId", issueNum.toString())
    }
    suspend fun unLink(issueNum: Long, threadId: Snowflake) {
        redisClient.del("link.github.$issueNum", "link.discord.$threadId");
    }

    suspend fun linkMessage(githubIssueNum: Long, githubCommentId: Long, threadId: Snowflake, messageId: Snowflake) {
        redisClient.set("mlink.github.$githubIssueNum-$githubCommentId", "$threadId-$messageId")
        redisClient.set("mlink.discord.$threadId-$messageId", "$githubIssueNum-$githubCommentId")
    }

    suspend fun linkTMessage(githubIssueNum: Long, threadId: Snowflake, messageId: Snowflake) {
        redisClient.set("tlink.github.$githubIssueNum", "$threadId-$messageId")
        redisClient.set("tlink.discord.$threadId-$messageId", "$githubIssueNum")
    }

    suspend fun getThreadIdByGithub(issueNum: Long):Snowflake {
        return Snowflake(redisClient.get("link.github.$issueNum")?.toULong() ?: throw IllegalStateException("Not found"));
    }
    suspend fun getThreadIdByDiscord(id: Snowflake): Long {
        return redisClient.get("link.discord.$id")?.toLong() ?: throw IllegalStateException("Not found");
    }

    suspend fun getMessageIdByGithub(issueNum: Long, commentId: Long): Pair<Snowflake, Snowflake> {
        return redisClient.get("mlink.github.$issueNum-$commentId")?.split("-")?.let { Snowflake(it[0].toULong()) to Snowflake(it[1].toULong()) }?: throw IllegalStateException("Not found");
    }
    suspend fun getGithubIdByDiscord(threadId: Snowflake, messageId: Snowflake): Pair<Long, Long> {
        return redisClient.get("mlink.discord.$threadId-$messageId")?.split("-")?.let { it[0].toLong() to it[1].toLong() }?: throw IllegalStateException("Not found")
    }
    suspend fun getGithubIdByTDiscord(threadId: Snowflake, messageId: Snowflake): Long? {
        return redisClient.get("tlink.discord.$threadId-$messageId")?.toLong();
    }
    suspend fun getDiscordIdByTGithub(issueNum: Long): Pair<Long, Long>? {
        return redisClient.get("tlink.github.$issueNum")?.split("-")?.let { it[0].toLong() to it[1].toLong() }
    }
}