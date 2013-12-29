package ch.clx.geargui

import akka.actor._
import akka.transactor._
import scala.concurrent.stm._


class Coordinator(friends: List[ActorRef]) extends Transactor {

  val count = Ref(0)


  override def coordinate = {
    case CoordinateGears => {
      println("Coordinator: List of Gears: " + friends.mkString)
      sendTo(friends(0) -> SyncGear(10), friends(1) -> SyncGear(10), friends(2) -> SyncGear(10))
    }
  }

  def atomically = implicit txn => {
    case CoordinateGears => {
      println("Atomically CoordinateGears: " + count.single.get)
    }
    case CoordinateFinished(gear: ActorRef) => {
      println("Coordinator: CoordinateFinished: " + gear)
      count transform (_ + 1)
      println("Atomically CoordinateFinished: " + count.single.get)
    }
  }

  override def before = {
    case CoordinateGears => {
      println("Coordinator before entered - not part of transaction")
    }
  }

  override def after = {
    case CoordinateGears => {
      println("Coordinator after entered Msg: CoordinateGears - not part of transaction")
    }
    case CoordinateFinished(gear: ActorRef) => {
      println("Coordinator after entered Msg: CoordinateFinished - not part of transaction " + gear)
      println("Counter value is: " + count.single.get )
      count.single.set(0)
      if (count.single.get == 3) {
        println("Work done...stop the Coordinator ")
        //context.stop(self)
      }
    }
  }

  //  override def normally = {
  //Any message matched by normally will not be matched by the other methods, and will not be involved in coordinated transactions.
  //In this method you can implement normal actor behavior, or use the normal STM atomic for local transactions.
  //Using _ would disable the whole coordinate meccano
  //    case _ => {
  //      println("Coordinator normaly bypass entered")
  //    }
  // }

}
