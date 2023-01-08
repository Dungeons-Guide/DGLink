package kr.syeyoung.github

import io.jsonwebtoken.Jwts
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.datetime.*
import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.json.Json
import kr.syeyoung.github.data.Comment
import kr.syeyoung.github.data.Issue
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.crypto.SecretKey
import kotlin.time.Duration

@Serializable
data class InstallationToken(val token: String, @SerialName("expires_at") val expiresAt: Instant);

private val key = Base64.getDecoder().decode(System.getenv("PRIVATE_KEY").replace("\n", ""));
private val specs: PKCS8EncodedKeySpec = PKCS8EncodedKeySpec(key);
private val privKey = KeyFactory.getInstance("RSA").generatePrivate(specs);

@Serializable
data class IDHOLDER(val id: Long);
@Serializable
data class IDNUMHOLDER(val id: Long, val number: Long);
@Serializable
data class NUMBERHOLDER(val number: Long);

object GithubAPI {
    private var token: InstallationToken? = null;
    public var client: HttpClient = HttpClient() {
        defaultRequest {
            url("https://api.github.com/")
            accept(ContentType.parse("application/vnd.github+json"))
            header("X-GitHub-Api-Version","2022-11-28")
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys=true
            })
        }
    };
    private fun generateJWT(): String {
        return Jwts.builder()
            .addClaims(mapOf(
                "iat" to System.currentTimeMillis() / 1000 - 60,
                "exp" to System.currentTimeMillis() / 1000 + 600,
                "iss" to System.getenv("APP_ID")
            ))
            .signWith(privKey)
            .compact();
    }
    suspend fun generateInstallationToken(): InstallationToken {
        if (token != null && token!!.expiresAt > Clock.System.now().plus(1, DateTimeUnit.MINUTE)) return token as InstallationToken;
        // request!...
        var response = client.get("app/installations") {
            bearerAuth(generateJWT())
        }
        val id = response.body<List<IDHOLDER>>().get(0).id;
        response= client.post("app/installations/$id/access_tokens") {
            bearerAuth(generateJWT())
        }
        token = response.body<InstallationToken>();
        return token as InstallationToken;
    }

    @Serializable
    data class Body(val body: String);
    @Serializable
    data class TitleBody(val title: String, val body: String, val labels: List<String>);
    @Serializable
    data class TitleLabel(val title: String, val labels: List<String>);

    suspend fun createIssue(title: String, content: String, label: List<String>): IDNUMHOLDER {
        var response = client.post("/repos/Dungeons-Guide/Skyblock-Dungeons-Guide/issues") {
            bearerAuth(generateInstallationToken().token)
            contentType(ContentType.parse("application/json"))
            this.setBody(TitleBody(title, content, label));
        }
        print(response);
        return response.body<IDNUMHOLDER>();
    }
    suspend fun createComment(issueNum: Long, content: String): Long {
        var response = client.post("/repos/Dungeons-Guide/Skyblock-Dungeons-Guide/issues/$issueNum/comments") {
            bearerAuth(generateInstallationToken().token)
            contentType(ContentType.parse("application/json"))
            this.setBody(Body(content));
        }
        print(response);
        return response.body<IDHOLDER>().id;
    }

    suspend fun editComment(issueNum: Long, commentId: Long, content: String): Long {
        var response = client.patch("/repos/Dungeons-Guide/Skyblock-Dungeons-Guide/issues/comments/${commentId}") {
            bearerAuth(generateInstallationToken().token)
            contentType(ContentType.parse("application/json"))
            this.setBody(Body(content));
        }
        print(response);
        return response.body<IDHOLDER>().id;
    }

    suspend fun deleteComment(issueNum: Long, commentId: Long): Long {
        var response = client.delete("/repos/Dungeons-Guide/Skyblock-Dungeons-Guide/issues/comments/${commentId}") {
            bearerAuth(generateInstallationToken().token)
        }
        print(response);
        return response.body<IDHOLDER>().id;
    }

    suspend fun retitle(threadIdByDiscord: Long, title: String,labels: List<String>) {
        var response = client.patch("/repos/Dungeons-Guide/Skyblock-Dungeons-Guide/issues/${threadIdByDiscord}") {
            bearerAuth(generateInstallationToken().token)
            contentType(ContentType.parse("application/json"))
            this.setBody(TitleLabel(title, labels));
        }
        print(response);
    }
    suspend fun recontent(threadIdByDiscord: Long, body: String) {
        var response = client.patch("/repos/Dungeons-Guide/Skyblock-Dungeons-Guide/issues/${threadIdByDiscord}") {
            bearerAuth(generateInstallationToken().token)
            contentType(ContentType.parse("application/json"))
            this.setBody(Body(body));
        }
        print(response);
    }

    suspend fun getIssue(issueNum: Long): Issue {
        var response = client.get("/repos/Dungeons-Guide/Skyblock-Dungeons-Guide/issues/${issueNum}") {
            bearerAuth(generateInstallationToken().token)
        }
        print(response);
        return response.body();
    }

    private suspend fun getComments(issueNum: Long, page: Int): List<Comment> {
        var response = client.get("/repos/Dungeons-Guide/Skyblock-Dungeons-Guide/issues/${issueNum}/comments") {
            parameter("page", page)
            parameter("per_page", 100)
            bearerAuth(generateInstallationToken().token)
        }
        print(response);
        return response.body();
    }
    suspend fun getComments(issueNum: Long): List<Comment> {
        var i = 1;
        val results: MutableList<Comment> = LinkedList()
        while(true) {
            val result = getComments(issueNum, i++)
            results.addAll(result)
            if (result.size < 100) {
                break
            }
        }
        return results;
    }
}