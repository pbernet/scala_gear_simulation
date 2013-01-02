package ch.clx.geargui

import akka.actor._
import collection.mutable.ListBuffer

abstract class Message

//gear API
case object StartSync extends Message
case class ReSync(gearActor : ActorRef) extends Message
case class Crashed(gearActor : ActorRef) extends Message
case class SyncGear(syncSpeed: Int) extends Message
case class SyncGearRestart(syncSpeed: Int, oldSpeed : Int) extends Message
case class Interrupt(toSpeed: Int) extends Message
case class GetSpeed extends Message

//controller API
case class CurrentSpeed(ref : String, speed: Int) extends Message
case class ReceivedSpeed(actorRef : ActorRef) extends Message
case class ReportInterrupt extends Message
case class GetGears extends Message
case class CleanUp extends Message
case class Revive(gearActor : ActorRef) extends Message
case class GearsAmount(default: Int) extends Message

//GUI API
case class Progress(numberOfSyncGears: Int) extends Message
case class SetCalculatedSyncSpeed(syncSpeed: Int) extends Message
case class GearProblem(ref : String) extends Message
case class GiveUp(victimActorRef : String) extends Message
case class AllGears(allGears: List[String]) extends Message
case class ReceivedSpeedGUI(ref: String) extends Message
case class CurrentSpeedGUI(ref : String, speed: Int) extends Message

//saboteur API
case class Sabotage(nGears: List[ActorRef]) extends Message
case class SabotageManual(gearActor : ActorRef, toSpeed: Int) extends Message