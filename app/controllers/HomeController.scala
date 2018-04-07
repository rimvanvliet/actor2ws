package controllers

import actors.{CustomActorFlow, MyWebSocketActor}
import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.{Inject, _}
import play.api.mvc._

import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject()(cc: ControllerComponents)
                              (implicit system: ActorSystem,
                               executionContext: ExecutionContext,
                               webJarsUtil: org.webjars.play.WebJarsUtil,
                               mat: Materializer) extends AbstractController(cc) {

  val (flow, underlyingActor) = CustomActorFlow.actorRef { out => MyWebSocketActor.props(out) }

  def chat = WebSocket.accept[String, String] { request =>
    flow
  }
  def index: Action[AnyContent] = Action { implicit request: RequestHeader =>
    val webSocketUrl = routes.HomeController.chat().webSocketURL()
    Ok(views.html.index(webSocketUrl))
  }

  def board(msg: String) = Action { implicit request: Request[AnyContent] =>
    val body: String = request.body.toString
    underlyingActor ! body
    Ok(body)
  }
}
