package kr.syeyoung

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Webhook
import dev.kord.core.entity.channel.Channel
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.on
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import io.github.crackthecodeabhi.kreds.connection.newClient
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kr.syeyoung.discord.registerCommands
import kr.syeyoung.discord.registerHandlers
import kr.syeyoung.github.GithubAPI
import kr.syeyoung.plugins.*

suspend fun main() = coroutineScope {
    async { setupRedis() }
    async { setupDiscord() }
    async { embeddedServer(Netty, port = 7070, host = "0.0.0.0", module = Application::module)
        .start() }
    async { GithubAPI.generateInstallationToken() }
    return@coroutineScope;
}

lateinit var kord: Kord;


lateinit var defaultchannel: LinkedChannel;
val linkedChannels: MutableList<LinkedChannel>  = ArrayList();
val tagMap: MutableMap<String, LinkedChannel> = HashMap();
val channelIdMap: MutableMap<Snowflake, LinkedChannel> = HashMap();
data class LinkedChannel(val channelId: Snowflake,
                         val channel: Channel,
                         val webhookId: Snowflake,
                         val webhookSecret: String,
                         val webhook: Webhook,
                         val configurationName: String,
                         val tags: Set<String>)


suspend fun setupDiscord() {
    println("Trying to load")
    kord = Kord(System.getenv("BOT_TOKEN"))

    println("Kord instantiated")

    kord.registerHandlers()
    kord.registerCommands()

    println("Registered")


    kord.on<ReadyEvent> {
        println("READY!")
        val configs: List<String> = System.getenv("TARGET_CHANNEL_CONF").split(",");
        for (config in configs) {
            println("setting up... ${config}")
            val channel = LinkedChannel(
                channelId = Snowflake(System.getenv("DSCD_${config}_CHANNEL_ID").toULong()),
                channel = kord.getChannel(Snowflake(System.getenv("DSCD_${config}_CHANNEL_ID").toULong()), EntitySupplyStrategy.rest) ?: throw IllegalArgumentException("Channel does not exist for ${config}"),
                webhookId = Snowflake(System.getenv("DSCD_${config}_WEBHOOK_ID").toULong()),
                webhookSecret = System.getenv("DSCD_${config}_WEBHOOK_SECRET"),
                webhook = kord.getWebhookWithToken(Snowflake(System.getenv("DSCD_${config}_WEBHOOK_ID").toULong()), System.getenv("DSCD_${config}_WEBHOOK_SECRET")),
                configurationName = config,
                tags = System.getenv("DSCD_${config}_TAGS").split(",").toSet())
            linkedChannels.add(channel)
            channel.tags.forEach {
                tagMap.put(it, channel)
            }
            channelIdMap.put(channel.channelId, channel)

            if (config == System.getenv("TARGET_CHANNEL_DEFAULT"))
                defaultchannel = channel;
        }
    }
    println("Logging in...")
    kord.login {
        @OptIn(PrivilegedIntent::class)
        intents += Intent.MessageContent
    }
    println("Logged in!")
}

lateinit var redisClient: KredsClient;

suspend fun setupRedis() {
    redisClient = newClient(Endpoint.from(System.getenv("REDIS_SERVER")))
}

fun Application.module() {
    configureSerialization()
    configureRouting()
    configureDoubleReceive()
}
