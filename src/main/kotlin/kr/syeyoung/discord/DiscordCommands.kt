package kr.syeyoung.discord

import dev.kord.common.DiscordBitSet
import dev.kord.common.entity.ALL
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.PublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.integer
import kr.syeyoung.LinkManager
import kr.syeyoung.github.recreateIssue
import java.lang.Exception

suspend fun Kord.registerCommands() {
    this.createGuildChatInputCommand(
        Snowflake(System.getenv("GUILD_ID").toULong()),
        "recreate-issue", "Creates issue from github or recreates one") {
        this.integer("issue-num", "Number for issue") {
            required=true
            minValue = 0
        }
        defaultMemberPermissions = Permissions.Builder(DiscordBitSet("0")).build();
    }
    on<GuildChatInputCommandInteractionCreateEvent> {
        if (interaction.command.rootName == "recreate-issue") {
            var resp = interaction.deferPublicResponse()
            val issue = interaction.command.integers["issue-num"]!!;
            var resp2: PublicMessageInteractionResponseBehavior;
            try {
                val github = LinkManager.getThreadIdByGithub(issue);
                resp2 = resp.respond {
                    this.content = "Linked issue found at <#${github.second}> in <#${github.first}>\nDeleting it"
                }

                kord.rest.channel.deleteChannel(github.second)
                resp2 = resp2.edit {
                    this.content = "Creating new issue..."
                }
            } catch (e: Exception) {
                resp2 = resp.respond {
                    this.content = "Linked issue not found: Creating new one from scratch"
                }
            }
            val target = recreateIssue(issue)
            resp2.edit {
                this.content = "Linked issue recreated! at <#${target.second}> in <#${target.first}>"
            }
        }
    }
}