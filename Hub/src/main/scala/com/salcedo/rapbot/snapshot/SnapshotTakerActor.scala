package com.salcedo.rapbot.snapshot

import java.util.UUID

import akka.actor._
import akka.routing.{BroadcastRoutingLogic, Router}
import com.salcedo.rapbot.snapshot.SnapshotTakerActor.{RegisterSubSystem, TakeSnapshot}

import scala.concurrent.duration.{Duration, MILLISECONDS}


object SnapshotTakerActor {

  case class TakeSnapshot(trigger: String)

  case class RegisterSubSystem(subsystem: ActorRef)

  def props: Props = Props(new SnapshotTakerActor())
}

class SnapshotTakerActor() extends Actor with ActorLogging {
  var subsystems = Set.empty[ActorRef]
  var router = Router(BroadcastRoutingLogic())

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[TakeSnapshot])
    context.system.eventStream.subscribe(self, classOf[RegisterSubSystem])
    scheduleSnapshot()
  }

  private def scheduleSnapshot(): Cancellable = {
    context.system.scheduler.scheduleOnce(Duration(100, MILLISECONDS), self, TakeSnapshot(self.path.name))(context.dispatcher)
  }

  override def receive: PartialFunction[Any, Unit] = {
    case subsystem: RegisterSubSystem => this.register(subsystem)
    case terminated: Terminated => this.terminate(terminated)
    case take: TakeSnapshot => this.snapshot(take)
  }

  private def snapshot(take: TakeSnapshot): Unit = {
    val uuid = UUID.randomUUID

    log.debug("Starting system snapshot '{}'. Subsystems: {}.", uuid, subsystems.map(_.path))

    start(take.trigger, uuid)
  }

  private def start(trigger: String, uuid: UUID): Unit = {
    val actor = context.actorOf(SnapshotActor.props(trigger, uuid, subsystems))

    router = router.addRoutee(actor)
    context.watch(actor)
  }

  private def terminate(message: Terminated): Unit = {
    unregister(message)
    complete(message)
  }

  private def unregister(message: Terminated): Unit = {
    if (subsystems.contains(message.actor)) subsystems -= message.actor
  }

  private def complete(message: Terminated): Unit = {
    router = router.removeRoutee(message.actor)

    if (router.routees.isEmpty) {
      scheduleSnapshot()
    }
  }

  private def register(message: RegisterSubSystem): Unit = {
    subsystems += message.subsystem
    context.watch(message.subsystem)
  }
}