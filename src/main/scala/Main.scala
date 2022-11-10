
import LogHelpers.futureLoggerOps
import akka.actor.ActorSystem
import com.google.common.reflect.TypeToken
import com.twitter.clientlib.{JSON, TwitterCredentialsBearer, TwitterCredentialsOAuth2}
import com.twitter.clientlib.api.TwitterApi
import com.twitter.clientlib.model.{AddOrDeleteRulesRequest, AddRulesRequest, DeleteRulesRequest, DeleteRulesRequestDelete, FilteredStreamingTweetResponse, RuleNoId}
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

object Main extends App {
  val config = ConfigFactory.load()
  implicit val executionContext = ExecutionContext.global
  implicit val logger: Logger = LoggerFactory.getLogger("Main")
  implicit val system: ActorSystem = ActorSystem()

  val userNames: Seq[String] = config.getStringList("twitter.followUsers").asScala.toSeq
  val bearerToken: String = config.getString("twitter.bearer")

  val mastodonBaseUrl: String = config.getString("mastodon.baseUrl")
  val mastodonAppName: String = config.getString("mastodon.applicationName")
  val mastodonUsername: String = config.getString("mastodon.username")
  val mastodonPassword: String = config.getString("mastodon.password")

  val apiInstance = new TwitterApi(new TwitterCredentialsBearer(bearerToken))
  val ruleNoId = new RuleNoId().value(userNames.map("from:" + _).mkString(" OR ") + " -is:retweet -is:reply -is:quote")
  val addRule = new AddRulesRequest().addAddItem(ruleNoId)
  val addRulesRequest = new AddOrDeleteRulesRequest(addRule)

  val tweetFields = new util.HashSet[String](util.Arrays.asList("text"))
  val userFields = new util.HashSet[String](util.Arrays.asList("username"))
  val expansions = new util.HashSet[String](util.Arrays.asList("author_id"))

  val program = for {
    oldRules <- Future(apiInstance.tweets().getRules.execute()).logError()
    deleteRulesRequest = new DeleteRulesRequest().delete(new DeleteRulesRequestDelete().ids(oldRules.getData.asScala.map(_.getId).asJava))
    _ <- Future(apiInstance.tweets().addOrDeleteRules(new AddOrDeleteRulesRequest(deleteRulesRequest)).execute()).logError()
    _ <- Future(apiInstance.tweets().addOrDeleteRules(addRulesRequest).execute()).logError()
    stream <- Future(apiInstance.tweets().searchStream()
      .tweetFields(tweetFields).userFields(userFields).expansions(expansions).execute()).logError()
    app <- Mastodon.createApp(mastodonBaseUrl, mastodonAppName)
    token <- app.login(mastodonUsername, mastodonPassword)
  } yield {
    val localVarReturnType = new TypeToken[FilteredStreamingTweetResponse](){}.getType

    val reader = new BufferedReader(new InputStreamReader(stream))
    var line = reader.readLine()
    while(line != null){
      if(line.nonEmpty){
        val jsonObject: FilteredStreamingTweetResponse = JSON.getGson.fromJson(line, localVarReturnType)
        val text = jsonObject.getData.getText
        val username = Option(jsonObject.getIncludes.getUsers.get(0)).map(_.getUsername).getOrElse("Unknown")
        app.toot(s"@$username: $text", Visibility.Public)(token).logError("Failed to toot")
      }
      line = reader.readLine()
    }
  }

  Await.result(program, Duration.Inf)
}

