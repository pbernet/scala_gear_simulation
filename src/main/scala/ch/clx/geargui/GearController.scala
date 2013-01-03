package ch.clx.geargui

import collection.mutable.ListBuffer
import scala.math.{max, min}
import akka.actor._
import akka.actor.SupervisorStrategy.{Stop, Restart, Resume}
import concurrent.Await
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._


class GearController(guiActor: ActorRef) extends Actor {

  /**
   * Let it crash model
   * - Restart the crashed actor (OneForOne)
   * - Give up if 2 exceptions occurs from one actor within 2 seconds
   */

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 2 seconds) {
    case _: ArithmeticException => Resume
    case _: NullPointerException => Restart
    case _: IllegalArgumentException => Stop
    case _: RuntimeException => Restart
  }

  private var gearColl = new ListBuffer[ActorRef]
  private var syncGears = new ListBuffer[ActorRef]
  private var syncSpeed = 0

  val rt = Runtime.getRuntime
  val startTime = System.nanoTime
  var maxThreads = totalThreads
  var minFree = rt.freeMemory
  var maxTotal = rt.totalMemory

  var stateMap: Map[String, Int] = Map()

  def gearCollection = {
    gearColl
  }

  def resetGearCollection = {
    gearColl = new ListBuffer[ActorRef]
  }

  def init = {
    //http://doc.akka.io/docs/akka/snapshot/scala/futures.html
    implicit val timeout = Timeout(5 seconds)
    val future = guiActor ? GearsAmount(2000) // enabled by the “ask” import
    val gearAmount = Await.result(future, timeout.duration).asInstanceOf[Int]


    for (i <- 0 until gearAmount) {
      val child = createGear(i)
      gearColl += child
      //registration for "Lifecycle Monitoring aka DeathWatch"
      //http://doc.akka.io/docs/akka/snapshot/scala/actors.html
      context.watch(child)
    }
  }


  private def createGear(id: Int): ActorRef = {
    val randSpeed = scala.util.Random.nextInt(1000)
    val gearActor = context.actorOf(Props(new Gear(id, randSpeed, self)), name = "Gear" + id.toString())
    stateMap += (gearActor.path.toString -> randSpeed)
    gearActor
  }

  def receive = {
    case StartSync => {
      println("[Controller] Send commands for syncing to gears!")

      if (gearColl.isEmpty) init

      var speeds = new ListBuffer[Int]
      gearCollection.foreach(e => {

        implicit val timeout = Timeout(5 seconds)
        val future = e ? GetSpeed // enabled by the “ask” import
        val gearSpeed = Await.result(future, timeout.duration).asInstanceOf[Int]

        speeds += gearSpeed
      })

      syncSpeed = (0 /: speeds)(_ + _) / speeds.length //Average over all gear speeds
      guiActor ! SetCalculatedSyncSpeed(syncSpeed)

      println("[Controller] calculated syncSpeed: " + syncSpeed)
      guiActor ! AllGears((gearCollection.map(_.path.toString)).toList)
      gearCollection.foreach(_ ! SyncGear(syncSpeed))
      println("[Controller] started all gears")
    }
    case ReSync(gearActor) => {
      val oldSpeed = stateMap.get(gearActor.path.toString).getOrElse(0)
      gearActor ! SyncGearRestart(syncSpeed, oldSpeed)
    }
    case crashed@Crashed(gearActor) => {
      //forward does the same as !, but will pass the original sender
      guiActor forward crashed
    }
    case ReceivedSpeed(ref: ActorRef) => {
      println("[Controller] Syncspeed received by a gear (" + ref + ")")
      syncGears += ref

      guiActor ! ReceivedSpeedGUI(ref.path.toString)
      guiActor ! Progress(syncGears.length)

      endResult
    }

    case CurrentSpeed(ref: String, speed: Int) => {
      //println("[GearController] gear(" + gearId + ") currentSpeed: " + speed)

      stateMap += (ref -> speed) //overwrite key
      guiActor ! CurrentSpeedGUI(ref, speed)
    }

    case ReportInterrupt => {
      sender match {
        case actor: ActorRef => {
          if (syncGears.contains(actor)) {
            syncGears -= actor
            actor ! SyncGear(syncSpeed)
          }

          guiActor ! GearProblem(actor.path.toString)
          guiActor ! Progress(syncGears.length)
        }
        case _ => ()
      }
    }
    case GetGears => {
      sender ! this.gearCollection
    }

    case CleanUp => {
      context.children foreach (context.stop(_))
      resetGearCollection
    }

    case Revive(gear) => {
      // Can't restart an actor that has been terminated - create new one
      // http://doc.akka.io/docs/akka/snapshot/general/supervision.html#supervision-restart
      println("Try to revive gear with path: " + gear.path + " Terminated: " + gear.isTerminated)
      val originalID = gear.path.toString().split("/").last.replace("Gear", "").toInt
      val child = createGear(originalID)

      context.unwatch(gear)
      context.watch(child)

      gearColl -= gear
      gearColl += child

      self ! ReSync(child)
    }

    case t@Terminated(child) => {

      println("Terminated recieved for child: " + child.path.toString() + " Dead: " + t.getExistenceConfirmed())
      guiActor ! GiveUp(child.path.toString)
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
    rec(Thread.currentThread.getThreadGroup)
  }


}