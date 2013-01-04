package ch.clx.geargui


import akka.actor._
import scala.concurrent.duration._
// for implicit execution context
import scala.concurrent.ExecutionContext.Implicits.global


class Gear(id: Int, mySpeed: Int, controller: ActorRef) extends Actor {

  var speed = mySpeed
  def gearId = id
  val failureLevel = 0.02 //raise to get more

  println("[Gear (" + id + ")] created with speed: " + mySpeed)

  def receive = {
    case SyncGear(syncSpeed: Int) => {

      //println("[Gear ("+id+")] activated, try to follow controller command (form mySpeed ("+mySpeed+") to syncspeed ("+syncSpeed+")")

      // Throw NPE and/or RuntimeEx - these sliders are marked magenta in the GUI
      if (math.random < failureLevel) {
        throw new NullPointerException
      }
      if (math.random < failureLevel) {
        throw new RuntimeException
      }
      Thread.sleep(100)
      controller ! CurrentSpeed(self.path.toString, speed)
      adjustSpeedTo(syncSpeed)
    }
    case SyncGearRestart(syncSpeed, oldSpeed) => {
      speed = oldSpeed
      controller ! CurrentSpeed(self.path.toString, speed)
      adjustSpeedTo(syncSpeed)
    }
    case Interrupt(toSpeed: Int) => {
      println("[Gear (" + id + ")] got interrupt: from " + speed + " to " + toSpeed)
      speed = toSpeed
      controller ! ReportInterrupt
    }
    case GetSpeed => {
      sender ! speed
    }
    case _ => {
      println("[Gear (" + self.path.toString + ")] match error")
    }
  }

  def adjustSpeedTo(targetSpeed: Int) {

    //println("Gear "+self.path.toString()+" is adjusting speed")
    if (targetSpeed > speed) {
      speed += 1
      self ! SyncGear(targetSpeed)
    } else if (targetSpeed < speed) {
      speed -= 1
      self ! SyncGear(targetSpeed)
    } else if (targetSpeed == speed) {
      callController
    }
  }

  def callController() {
    println("[Gear (" + id + ")] has syncSpeed")
    controller ! ReceivedSpeed(self)
  }

  /**
   * Lifecycle methods
   */
  override def preRestart(reason: Throwable, message: Option[Any]) {
    println("preRestart recieved: " + self.path + " reason: " + reason + " message: " + message.getOrElse(""))
    controller ! Crashed(self)
  }

  override def postRestart(reason: Throwable) {
    println("postRestart recieved: " + self.path)

    context.system.scheduler.scheduleOnce(500 milliseconds) {
      controller ! ReSync(self)
    }
  }
}