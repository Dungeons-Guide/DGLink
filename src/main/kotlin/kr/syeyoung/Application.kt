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
lateinit var forumChannel: Channel
lateinit var webhook: Webhook;
val webhook_token: String = System.getenv("TARGET_WEBHOOK_SECRET")
suspend fun setupDiscord() {
    kord = Kord(System.getenv("BOT_TOKEN"))

//    kord.on<MessageCreateEvent> {
//        if (message.content != "!ping") return@on
//
//        val response = message.channel.createMessage("Pong!")
//        response.addReaction(pingPong)
//
//        delay(5000)
//        message.delete()
//        response.delete()
//    }

    kord.registerHandlers()


    kord.on<ReadyEvent> {
        forumChannel = kord.getChannel(
            Snowflake(System.getenv("TARGET_CHANNEL").toLong()),
            EntitySupplyStrategy.rest
        )!!;

        webhook = kord.getWebhookWithToken(
            Snowflake(System.getenv("TARGET_WEBHOOK_ID").toLong()),
            System.getenv("TARGET_WEBHOOK_SECRET")
        );
    }
    kord.login {
        @OptIn(PrivilegedIntent::class)
        intents += Intent.MessageContent
    }
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
