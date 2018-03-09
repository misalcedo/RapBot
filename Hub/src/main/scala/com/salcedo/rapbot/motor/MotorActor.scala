package com.salcedo.rapbot.motor

import java.util.UUID

import akka.actor.Status.Success
import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{JsonFraming, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.google.gson.Gson
import com.salcedo.rapbot.motor.MotorActor._
import com.salcedo.rapbot.remote.ActorBreaker
import com.salcedo.rapbot.snapshot.SnapshotActor.TakeSubSystemSnapshot

import scala.concurrent.Future

object MotorActor {

  case class Vehicle(backLeft: Motor, backRight: Motor)

  case class Command(value: Int)

  val Forward = Command(1)
  val Backward = Command(2)
  val Brake = Command(3)
  val Release = Command(4)

  case class Motor(speed: Int, command: Command)

  case class Pushed(version: UUID)

  def props(uri: Uri): Props = Props(new MotorActor(uri))
}

class MotorActor(val uri: Uri) extends Actor with ActorBreaker with ActorLogging {
  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val json = new Gson
  val http = Http(context.system)
  var vehicle = Vehicle(Motor(0, Release), Motor(0, Release))
  var version: UUID = nextVersion
  var remoteCall: Future[UUID] = Future.successful(version)

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[Vehicle])
    send()
  }

  override def receive: PartialFunction[Any, Unit] = {
    case vehicle: Vehicle => this.update(vehicle)
    case Pushed(v) => this.push(v)
    case _: TakeSubSystemSnapshot => this.snapshot()
  }

  def update(vehicle: Vehicle): Unit = {
    this.version = nextVersion
    this.vehicle = vehicle

    send()
  }

  private def nextVersion = {
    UUID.randomUUID()
  }

  def push(pushedVersion: UUID): Unit = {
    if (version.equals(pushedVersion)) {
      log.debug("Received a pushed message for '{}' version", version)
      return
    }

    send()
  }

  def send(): Unit = {
    if (!remoteCall.isCompleted) return

    val request = HttpRequest(
      uri = uri.withPath(Uri.Path("/motors")),
      method = HttpMethods.PUT,
      entity = HttpEntity(ContentTypes.`application/json`, json.toJson(vehicle))
    )

    val future = http.singleRequest(request).flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        parseVehicle(entity)
      case HttpResponse(statusCode, _, entity, _) =>
        entity.discardBytes()
        sys.error(s"Received a response with a status code other than 200 OK. Status: $statusCode")
    }(context.dispatcher)

    future.onComplete(_ => self ! Pushed(version))(context.dispatcher)
  }

  def snapshot(): Unit = {
    sender() ! Success(vehicle)
  }

  private def parseVehicle(entity: ResponseEntity): Future[Vehicle] = {
    entity.transformDataBytes(JsonFraming.objectScanner(256))
      .dataBytes
      .map(_.utf8String)
      .map(json.fromJson(_, classOf[Vehicle]))
      .runWith(Sink.seq)
      .map(_.head)(context.dispatcher)
  }
}
