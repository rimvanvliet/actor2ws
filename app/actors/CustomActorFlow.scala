package actors

import akka.actor._
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.reactivestreams.Publisher

object CustomActorFlow {

  def actorRef[In, Out](props: ActorRef => Props, bufferSize: Int = 16, overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew)(implicit factory: ActorRefFactory, mat: Materializer): (Flow[In, Out, _], ActorRef) = {

    val (outActor: ActorRef, publisher: Publisher[Out]) = Source.actorRef[Out](bufferSize, overflowStrategy)
      .toMat(Sink.asPublisher(true))(Keep.both).run()

    val sinkActor: ActorRef = factory.actorOf(Props(new Actor {
      val flowActor: ActorRef = context.watch(context.actorOf(props(outActor), "flowActor"))

      def receive: PartialFunction[Any, Unit] = {
        case Status.Success(_) | Status.Failure(_) => flowActor ! PoisonPill
        case Terminated(_) => context.stop(self)
        case other => flowActor ! other // <<-- whatever you send to sinkActor is forwarded to your actor, except above
      }

      override def supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
        case _ => SupervisorStrategy.Stop
      }
    }))

    (Flow.fromSinkAndSource(
      Sink.actorRef(sinkActor, Status.Success(())),
      Source.fromPublisher(publisher)
    ), sinkActor) // <<-- return as a tuple
  }
}