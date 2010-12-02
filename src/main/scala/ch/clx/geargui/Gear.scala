package ch.clx.geargui

import se.scalablesolutions.akka.actor.{Scheduler, ActorRef, Actor}
import java.util.concurrent.TimeUnit
import se.scalablesolutions.akka.config.Supervision

/**
 * Created by IntelliJ IDEA.
 * User: pmei
 * Date: 11.02.2010
 * Time: 15:22:35
 * Package: ch.clx.geargui
 * Class: Gear
 */

class Gear(id: String, mySpeed : Int, controller: ActorRef) extends Actor {
  /**
   * Let it crash
   */
  self.lifeCycle = Supervision.Permanent

  var speed = mySpeed

  def gearId = id

  /* Constructor */
  println("[Gear (" + id + ")] created with speed: " + mySpeed)

  def receive = {
    case SyncGear(syncSpeed: Int) => {

      //println("[Gear ("+id+")] activated, try to follow controller command (form mySpeed ("+mySpeed+") to syncspeed ("+syncSpeed+")")

      if (math.random < 0.01) {
        throw new RuntimeException
      }

      
      controller ! CurrentSpeed(self.id, speed)
      adjustSpeedTo(syncSpeed)
    }
    case SyncGearRestart(syncSpeed,oldSpeed) => {
      speed = oldSpeed
      controller ! CurrentSpeed(self.id, speed)
      adjustSpeedTo(syncSpeed)
    }
    case Interrupt(toSpeed: Int) => {
      println("[Gear (" + id + ")] got interrupt: from " + speed + " to " + toSpeed);
      speed = toSpeed;
      controller ! ReportInterrupt
    }
    case GetSpeed => {
      self.reply(speed)
    }
    case _ => {
      println("[Gear (" + self.id + ")] match error")
    }
  }

  def adjustSpeedTo(targetSpeed: Int) = {
    //println("Gear "+self.id+" is adjusting speed")
    if (targetSpeed > speed) {
      speed += 1
      Scheduler.scheduleOnce(self, SyncGear(targetSpeed), 50, TimeUnit.MILLISECONDS)
    } else if (targetSpeed < speed) {
      speed -= 1
      Scheduler.scheduleOnce(self, SyncGear(targetSpeed), 50, TimeUnit.MILLISECONDS)
    } else if (targetSpeed == speed) {
      callController
    }

  }

  def callController = {
    println("[Gear (" + id + ")] has syncSpeed")
    controller ! ReceivedSpeed(self.id)
  }

  /**
   * Fault tolerance
   */
  override def preRestart(reason: Throwable) {
    controller ! Crashed(self)
  }

  override def postRestart(reason: Throwable) {
    self.id = id
    Scheduler.scheduleOnce(controller, ReSync(self), 500, TimeUnit.MILLISECONDS)
  }
}