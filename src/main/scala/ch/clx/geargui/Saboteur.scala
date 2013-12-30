package ch.clx.geargui

import akka.actor._
import akka.util.Timeout
import concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import scala.language.postfixOps

class Saboteur() extends Actor {

  def gearCollection: List[ActorRef] = {

    //http://doc.akka.io/docs/akka/2.0.3/scala/futures.html
    implicit val timeout = Timeout(5 seconds)
    val future = context.parent ? GetGears // enabled by the “ask” import
    Await.result(future, timeout.duration).asInstanceOf[List[ActorRef]] match {
      case gears: List[ActorRef] => gears
      case _ => List[ActorRef]()
    }
  }

  def receive = {
    case SabotageRandom() => {

      val gears = gearCollection
      val nOfGears = gears.length

      val sabotageList = (0 until nOfGears).map(i => gears(scala.util.Random.nextInt(nOfGears))).filterNot( _.isTerminated)
      sabotageList.foreach { _ ! Interrupt(scala.util.Random.nextInt(1000))
      }
    }
    case SabotageManual(ref, toSpeed) => {

      gearCollection.find(_.actorRef.path.toString == ref) match {
        case Some(gear) if !gear.isTerminated => gear ! Interrupt(toSpeed)
        case _ => ()
      }
    }
  }
}