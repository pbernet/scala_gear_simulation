package ch.clx.geargui


import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


class Gear(id: Int, mySpeed: Int, controller: ActorRef) extends Actor {

  var speed = mySpeed

  val failureLevel = 0.04 //raise to get more exceptions
  var sleepTime: Long = 150 //raise to slow down simulation

  println("[Gear (" + id + ")] created with speed: " + mySpeed)

  def receive = {
    case SyncGear(syncSpeed: Int) => {

      //println("[Gear ("+id+")] activated, try to follow controller command (form mySpeed ("+mySpeed+") to syncspeed ("+syncSpeed+")")

      if (math.random < failureLevel) {
        sys.error("I just died due to a RuntimeException - I am marked magenta in the GUI")
      }
      Thread.sleep(sleepTime)
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
    case SetSleepTime(newTime: Long) => {
      sleepTime = newTime
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
      callController()
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