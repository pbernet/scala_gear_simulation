package ch.clx.geargui


import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps


/**
 * A Gear tries to reach the syncSpeed calculated by the GearController
 * Throws RuntimeExceptions at random and is supervised by the GearController
 */

class Gear(id: Int, initSpeed: Int) extends Actor {

  val failureLevel = 0.04
  //raise to get more exceptions
  var sleepTime: Long = 150 //raise to slow down simulation

  val controller = context.parent

  println("[Gear (" + id + ")] created with speed: " + initSpeed)

  //change the actor behaviour with "akka become", so there is no need for a local var "speed" anymore
  def receive = adjust(initSpeed)

  def adjust(speed: Int): Receive = {

    case SyncGear(syncSpeed: Int) => {

      //println("[Gear ("+id+")] activated, try to follow controller command (form mySpeed ("+mySpeed+") to syncspeed ("+syncSpeed+")")

      if (math.random < failureLevel) {
        sys.error("I just died due to a RuntimeException - I am marked magenta in the GUI")
      }
      Thread.sleep(sleepTime)
      controller ! CurrentSpeed(self.path.toString, speed)
      adjustSpeedTo(speed, syncSpeed)
    }
    case SyncGearRestart(syncSpeed, oldSpeed) => {
      context.become(adjust(oldSpeed))
      controller ! CurrentSpeed(self.path.toString, speed)
      adjustSpeedTo(speed, syncSpeed)
    }
    case Interrupt(toSpeed: Int) => {
      println("[Gear (" + id + ")] got interrupt: from " + speed + " to " + toSpeed)
      context.become(adjust(toSpeed))
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

  def adjustSpeedTo(currentSpeed: Int, targetSpeed: Int) {

    //println("Gear "+self.path.toString()+" is adjusting speed from: " + currentSpeed + " to: " + targetSpeed )
    if (targetSpeed > currentSpeed) {
      context.become(adjust(currentSpeed + 1))
      self ! SyncGear(targetSpeed)
    } else if (targetSpeed < currentSpeed) {
      context.become(adjust(currentSpeed - 1))
      self ! SyncGear(targetSpeed)
    } else if (targetSpeed == currentSpeed) {
      println("[Gear (" + id + ")] has syncSpeed")
      controller ! ReceivedSpeed
    }
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