package ch.clx.geargui

/**
 * Created by IntelliJ IDEA.
 * User: pmei
 * Date: 11.02.2010
 * Time: 15:23:29
 * Package: ch.clx.geargui
 * Class: GearController
 */

import collection.mutable.ListBuffer
import scala.math.{max, min}
import se.scalablesolutions.akka.config.Supervision.OneForOneStrategy
import se.scalablesolutions.akka.actor.{MaximumNumberOfRestartsWithinTimeRangeReached, Actor, ActorRef}

class GearController(guiActor: ActorRef, aName: String) extends Actor {

  /**
   *  Let it crash model
   *  - Just restart the crashed actor (OneForOne)
   *  - Give up reviving if 5 exceptions occurs from one actor within 2 seconds
   */
  self.faultHandler =
          OneForOneStrategy(
            List(classOf[Throwable]),
            Some(5),
            Some(20000)
            )

  private var syncGears = new ListBuffer[String]
  private var syncSpeed = 0

  val schedulerName = aName
  val rt = Runtime.getRuntime
  val startTime = System.nanoTime
  var maxThreads = totalThreads
  var minFree = rt.freeMemory
  var maxTotal = rt.totalMemory
  var gearColl: ListBuffer[ActorRef] = ListBuffer()

  var stateMap: Map[String, Int] = Map()

  def gearCollection = {
    gearColl
  }

  def resetGearCollection = {
    gearColl = ListBuffer()
  }

  def init = {
    val gearAmount = guiActor.!!("gearsAmount",2000) match {
        case Some(i: Int) => i
        case None => 0
    }
    for (i <- 0 until gearAmount) {
      val randSpeed = scala.util.Random.nextInt(1000)
      val gearActor = Actor.actorOf(new Gear(i.toString, randSpeed, self))
      gearActor.id = i.toString
      someSelf match {
        case Some(ref: ActorRef) => {
          println("Registered gear: " + gearActor.id)
          ref.startLink(gearActor)
        }
      }
      //Add to stateMap
      stateMap += (gearActor.id -> randSpeed)
      gearColl += gearActor
    }
  }

  def receive = {
    case StartSync => {
      println("[Controller] Send commands for syncing to gears!")

      gearColl.isEmpty match {
        case true => init
        case false => ()
      }

      var speeds = new ListBuffer[Int]
      gearCollection.foreach(e => {
        val gearSpeed = e.!!(GetSpeed, 2000) match {
          case Some(i: Int) => i
          case None => 0
        }
        speeds += gearSpeed
      })

      //Calc avg
      syncSpeed = (0 /: speeds)(_ + _) / speeds.length //Average over all gear speeds
      guiActor ! SetCalculatedSyncSpeed(syncSpeed)

      println("[Controller] calculated syncSpeed: " + syncSpeed)
      gearCollection.foreach(_ ! SyncGear(syncSpeed))

      println("[Controller] started all gears")
    }
    case ReSync(gearActor) => {
      val oldSpeed = stateMap.get(gearActor.id).getOrElse(0)
      gearActor ! SyncGearRestart(syncSpeed, oldSpeed)
    }
    case crashed@Crashed(gearActor) => {
      //forward does the same as !, but will pass the original sender
      guiActor forward crashed
    }
    case ReceivedSpeed(gearId: String) => {
      println("[Controller] Syncspeed received by a gear (" + gearId + ")")
      syncGears += gearId

      /**
       *  Notify the gui
       */
      guiActor ! ReceivedSpeed(gearId)
      guiActor ! Progress(syncGears.length)

      endResult
    }

    case CurrentSpeed(gearId: String, speed: Int) => {
      //println("[GearController] gear(" + gearId + ") currentSpeed: " + speed)
      // Update map
      stateMap += (gearId -> speed) //overwrite key
      //Tell gui actor
      guiActor ! CurrentSpeed(gearId, speed)
    }

    case ReportInterrupt => {
      self.sender match {
        case Some(actor) => {
          if (syncGears.contains(actor.id)) {
            syncGears -= actor.id;
            actor ! SyncGear(syncSpeed)
          }

          /**
           * Notify the gui
           */
          guiActor ! GearProblem(actor.getId)
          guiActor ! Progress(syncGears.length)
        }
        case None => ()
      }


    }
    case GetGears => {
      self.reply(gearCollection)
    }
    case Revive(gear) => {
      // Can't restart an actor that has been shut down with 'stop' or 'exit'
      // create new one 
      val gearActor = Actor.actorOf(new Gear(gear.id, stateMap.get(gear.id).getOrElse(0), self))
      gearActor.id = gear.id
      someSelf.get.startLink(gearActor)
      //replace old actor for new in list
      gearColl -= gear
      gearColl += gearActor
      //now do a resync
      self ! ReSync(gearActor)
    }
    case CleanUp => {
      gearColl.foreach(gear => {
        gear.stop
        someSelf.get.unlink(gear)
      })
      someSelf.get.stop
      guiActor.stop
      gearColl = null
    }
    case MaximumNumberOfRestartsWithinTimeRangeReached(
    victimActorRef, maxNrOfRetries, withinTimeRange, lastExceptionCausingRestart) => {
      println("Giving up gear with id: " + victimActorRef.id)
      guiActor ! GiveUp(victimActorRef)
    }
    case _ => println("[Controller] No match :(")

  }

  def endResult = {
    if (syncGears.length == gearCollection.length) {
      println("[Controller] all gears are back in town!")


      //a simple micro benchmark inspired by:
      //https://bitbucket.org/eengbrec/managedforkjoinpool/src/tip/src/main/scala/actorbench/TestHarness.scala
      maxThreads = max(maxThreads, totalThreads)
      minFree = min(minFree, rt.freeMemory)
      maxTotal = max(maxTotal, rt.totalMemory)
      printResults(startTime, System.nanoTime, maxThreads, minFree, maxTotal)
    }
  }

  protected def printResults(startTime: Long, endTime: Long, maxThreads: Int, minFree: Long, maxTotal: Long) {
    import scala.util.Properties

    def pf(s: String) = println(s)
    val props = System.getProperties
    def pp(name: String) = pf(name + ": " + props.getProperty(name))

    pf("****Microbenchmark****")
    pf("Scheduler: " + schedulerName)
    pf("# of Cores: " + rt.availableProcessors.toString)
    pp("java.vm.name") // e.g. Java HotSpot(TM) 64-Bit Server VM
    pp("java.vm.version") // e.g. 16.3-b01-279
    pp("java.version") // e.g. 1.6.0_20
    pf("Scala Version: " + Properties.scalaPropOrElse("version.number", "unknown")) // e.g. 2.8.0.final
    pp("os.name") // e.g. Mac OS X
    pp("os.version") // e.g. 10.5.8
    pp("os.arch") // e.g. x86_64
    val wct = (endTime - startTime).asInstanceOf[Double] / (1000.0 * 1000.0 * 1000.0)
    pf("Time: " + wct)
    def bytes2megs(bytes: Long) = bytes.asInstanceOf[Double] / (1024.0 * 1024.0)
    pf("Max Threads: " + maxThreads)
    pf("Min free Mem: " + bytes2megs(minFree))
    pf("Max total Mem: " + bytes2megs(maxTotal))
    pf("****")
  }

  def totalThreads = {
    def rec(tg: ThreadGroup): Int = if (tg.getParent eq null) tg.activeCount else rec(tg.getParent)
    rec(currentThread.getThreadGroup)
  }


}