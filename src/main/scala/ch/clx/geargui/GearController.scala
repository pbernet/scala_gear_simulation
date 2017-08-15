package ch.clx.geargui

import collection.mutable.ListBuffer
import scala.math.{max, min}
import akka.actor._
import akka.actor.SupervisorStrategy.{Stop, Restart, Resume}
import concurrent.Await
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.language.postfixOps


class GearController extends Actor {

  /**
   * Let it crash model:
   * - Restart the crashed Gear actor (OneForOne)
   * - Give up if 2 exceptions occur from one gear actor within 2 seconds (double click in GUI to bring back to live)
   */

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 2.seconds) {
    case _: ArithmeticException => Resume
    case _: NullPointerException => Restart
    case _: IllegalArgumentException => Stop
    case _: RuntimeException => Restart
  }

  private var gearColl = ListBuffer[ActorRef]()
  private var syncGears = ListBuffer[ActorRef]()
  private var syncSpeed = 0

  val rt = Runtime.getRuntime
  val startTime = System.nanoTime
  var maxThreads = totalThreads
  var minFree = rt.freeMemory
  var maxTotal = rt.totalMemory

  var stateMap: Map[String, Int] = Map()

  val saboteur = context.actorOf(Props(classOf[Saboteur]), name = "Saboteur")

  var guiActor: ActorRef = null

  def gearList = gearColl.toList

  def resetGearCollection() {
    gearColl = ListBuffer[ActorRef]()
  }

  def init() {
    //http://doc.akka.io/docs/akka/snapshot/scala/futures.html
    implicit val timeout = Timeout(5 seconds)
    val future = guiActor ? RequestNumberOfGears // enabled by the “ask” import
    val nOfGears = Await.result(future, timeout.duration).asInstanceOf[Int]


    for (i <- 0 until nOfGears) {
      val child = createGear(i)
      gearColl += child
      //registration for "Lifecycle Monitoring aka DeathWatch"
      //http://doc.akka.io/docs/akka/snapshot/scala/actors.html
      context.watch(child)
    }
  }


  private def createGear(id: Int): ActorRef = {
    val randSpeed = scala.util.Random.nextInt(1000)
    val gearActor = context.actorOf(Props(new Gear(id, randSpeed)), name = "Gear" + id.toString)
    stateMap += (gearActor.path.toString -> randSpeed)
    gearActor
  }

  def receive = {
    case StartSync(theGUIActor) => {
      println("[Controller] Send commands for syncing to gears!")
      guiActor =  theGUIActor
      if (gearColl.isEmpty) init()

      var speeds = new ListBuffer[Int]
      gearList.foreach(e => {

        implicit val timeout = Timeout(5 seconds)
        val future = e ? GetSpeed // enabled by the “ask” import
        val gearSpeed = Await.result(future, timeout.duration).asInstanceOf[Int]

        speeds += gearSpeed
      })

      syncSpeed = (0 /: speeds)(_ + _) / speeds.length //Average over all gear speeds
      guiActor ! SetCalculatedSyncSpeed(syncSpeed)

      println("[Controller] calculated syncSpeed: " + syncSpeed)
      guiActor ! AllGears(gearList.map(_.path.toString))
      gearList.foreach(_ ! SyncGear(syncSpeed))
      println("[Controller] started all gears")
    }
    case ReSync(gearActor) => {
      val oldSpeed = stateMap.getOrElse(gearActor.path.toString, 0)
      gearActor ! SyncGearRestart(syncSpeed, oldSpeed)
    }
    case crashed@Crashed(gearActor) => {
      //forward does the same as !, but will pass the original sender
      guiActor forward crashed
    }
    case ReceivedSpeed => {
      val gearRef = context.sender
      println("[Controller] Syncspeed received by a gear (" + gearRef + ")")
      syncGears += gearRef

      guiActor ! ReceivedSpeedGUI(gearRef.path.toString)
      guiActor ! Progress(syncGears.length)

      if (isSimulationFinished) benchmark()
    }

    case CurrentSpeed(ref: String, speed: Int) => {
      //println("[GearController] gear(" + gearId + ") currentSpeed: " + speed)

      stateMap += (ref -> speed) //overwrite key
      guiActor ! CurrentSpeedGUI(ref, speed)
    }

    case SetSleepTime(time) => {
      gearList.foreach(_ ! SetSleepTime(time))
    }

    case SetErrorLevel(level) => {
      println("[GearController] error Level: "  + level)
      gearList.foreach(_ ! SetErrorLevel(level))
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
      sender ! gearList
    }

    case CleanUp => {
      context.children.foreach(context.stop(_))
      resetGearCollection()
    }

    case Revive(ref) => {

      def revive(gear: ActorRef) = {
        // Can't restart an actor that has been terminated - create a new one instead
        // http://doc.akka.io/docs/akka/snapshot/general/supervision.html#supervision-restart
        println("Try to revive gear with path: " + gear.path)
        val originalID = gear.path.toString.split("/").last.replace("Gear", "").toInt
        val child = createGear(originalID)

        context.unwatch(gear)
        context.watch(child)

        gearColl -= gear
        gearColl += child

        self ! ReSync(child)
      }

      //start here
      gearList.find(_.actorRef.path.toString == ref) match {
        case Some(gear) => revive(gear)
        case None => ()
      }
    }

    case SabotageRandom() => {
      saboteur ! SabotageRandomFrom(gearList)
    }

    case SabotageManual(ref, toSpeed) => {
      saboteur ! SabotageManualFrom(gearList, ref, toSpeed)
    }

    case t@Terminated(child) => {

      println("Terminated received for child: " + child.path.toString + " Dead: " + t.getExistenceConfirmed())
      guiActor ! GiveUp(child.path.toString)
    }

    case _ => println("[Controller] No match :(")

  }

  private def isSimulationFinished = {
    syncGears.length == gearList.length
  }

  private def benchmark() {
    //a simple micro benchmark inspired by:
    //https://bitbucket.org/eengbrec/managedforkjoinpool/src/tip/src/main/scala/actorbench/TestHarness.scala
    maxThreads = max(maxThreads, totalThreads)
    minFree = min(minFree, rt.freeMemory)
    maxTotal = max(maxTotal, rt.totalMemory)
    printResults(startTime, System.nanoTime, maxThreads, minFree, maxTotal)
  }

  private def printResults(startTime: Long, endTime: Long, maxThreads: Int, minFree: Long, maxTotal: Long) {
    import scala.util.Properties

    def pf(s: String) {
      println(s)
    }
    val props = System.getProperties
    def pp(name: String) {
      pf(name + ": " + props.getProperty(name))
    }

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

  private def totalThreads = {
    def rec(tg: ThreadGroup): Int = if (tg.getParent eq null) tg.activeCount else rec(tg.getParent)
    rec(Thread.currentThread.getThreadGroup)
  }
}