
import LogHelpers.futureLoggerOps
import akka.actor.ActorSystem
import com.google.common.reflect.TypeToken
import com.twitter.clientlib.{JSON, TwitterCredentialsBearer, TwitterCredentialsOAuth2}
import com.twitter.clientlib.api.TwitterApi
import com.twitter.clientlib.model.{AddOrDeleteRulesRequest, AddRulesRequest, DeleteRulesRequest, DeleteRulesRequestDelete, FilteredStreamingTweetResponse, RuleNoId, Tweet}
import com.typesafe.config.ConfigFactory
import de.sciss.scaladon.{Mastodon, Visibility}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{BufferedReader, InputStreamReader}
import java.lang.reflect.Type
import java.util
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.{ListHasAsScala, SeqHasAsJava}
import scala.util.Try

case class MastodonCredentials(username: String, password: String)

object Main extends App {
  val config = ConfigFactory.load()
  implicit val executionContext = ExecutionContext.global
  implicit val logger: Logger = LoggerFactory.getLogger("Main")
  implicit val system: ActorSystem = ActorSystem()

  val bearerToken: String = config.getString("twitter.bearer")

  val mastodonBaseUrl: String = config.getString("mastodon.baseUrl")
  val mastodonAppName: String = config.getString("mastodon.applicationName")

  val followsToPosters: Map[String, MastodonCredentials] = if(config.hasPath("multiAccount")){
    val multiAccount = config.getConfigList("multiAccount").asScala.toSeq
    multiAccount.map{config =>
      val follow = config.getString("follow")
      val username = config.getString("mastodonUsername")
      val password = config.getString("mastodonPassword")
      (follow -> MastodonCredentials(username, password))
    }.toMap
  } else {
    val userNames: Seq[String] = config.getStringList("twitter.followUsers").asScala.toSeq
    val mastodonUsername: String = config.getString("mastodon.username")
    val mastodonPassword: String = config.getString("mastodon.password")
    userNames.map(username => username -> MastodonCredentials(mastodonUsername, mastodonPassword)).toMap
  }

  val apiInstance = new TwitterApi(new TwitterCredentialsBearer(bearerToken))
  val ruleNoId = new RuleNoId().value(s"(${followsToPosters.keys.map("from:" + _).mkString(" OR ")}) -is:retweet -is:reply -is:quote")
  val addRule = new AddRulesRequest().addAddItem(ruleNoId)
  val addRulesRequest = new AddOrDeleteRulesRequest(addRule)

  val tweetFields = new util.HashSet[String](util.Arrays.asList("text"))
  val userFields = new util.HashSet[String](util.Arrays.asList("username"))
  val expansions = new util.HashSet[String](util.Arrays.asList("author_id"))

  val program = for {
    oldRules <- Future(apiInstance.tweets().getRules.execute()).logError()
    deleteRulesRequest <- Future(new DeleteRulesRequest().delete(new DeleteRulesRequestDelete().ids(oldRules.getData.asScala.map(_.getId).asJava))).logError()
    _ <- Future(apiInstance.tweets().addOrDeleteRules(new AddOrDeleteRulesRequest(deleteRulesRequest)).execute()).logError()
    _ <- Future(apiInstance.tweets().addOrDeleteRules(addRulesRequest).execute()).logError()
    stream <- Future(apiInstance.tweets().searchStream()
      .tweetFields(tweetFields).userFields(userFields).expansions(expansions).execute()).logError()
    app <- Mastodon.createApp(mastodonBaseUrl, mastodonAppName).logError()
    usernameToTokens <- Future.traverse(followsToPosters.map{ case (follow: String, credentials: MastodonCredentials) =>
      (follow, app.login(credentials.username, credentials.password))
    }.toList){case (k, fv) => fv.map(k -> _)}.map(_.toMap)
  } yield {
    val localVarReturnType = new TypeToken[FilteredStreamingTweetResponse](){}.getType

    val reader = new BufferedReader(new InputStreamReader(stream))
    var line = reader.readLine()
    while(line != null){
      if(line.nonEmpty){
        logger.info(s"Received line: $line")
        for {
          jsonObject <- Option(JSON.getGson.fromJson[FilteredStreamingTweetResponse](line, localVarReturnType))
          data <- Option(jsonObject.getData)
          text = data.getText
          username <- Option(jsonObject.getIncludes.getUsers.get(0)).map(_.getUsername)
        } yield {
          logger.info(s"Retooting: $text")
          usernameToTokens.get(username).map{token =>
            app.toot(s"@$username: $text", Visibility.Unlisted)(token).logError("Failed to toot")
          }
        }
      }
      line = reader.readLine()
    }
  }

  Await.result(program, Duration.Inf)
}

