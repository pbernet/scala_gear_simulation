package ch.clx.geargui

import akka.actor._

trait Message

//Gear API
case class StartSync(receiver : ActorRef) extends Message
case class ReSync(gearActor : ActorRef) extends Message
case class Crashed(gearActor : ActorRef) extends Message
case class SyncGear(syncSpeed: Int) extends Message
case class SyncGearRestart(syncSpeed: Int, oldSpeed : Int) extends Message
case class Interrupt(toSpeed: Int) extends Message
case object GetSpeed extends Message
case class SetSleepTime(time: Long)
case class SetErrorLevel(level: Double)

//GearController API
case class CurrentSpeed(ref : String, speed: Int) extends Message
case object ReceivedSpeed extends Message
case object ReportInterrupt extends Message
case object GetGears extends Message
case object CleanUp extends Message
case class Revive(ref : String) extends Message
case object RequestNumberOfGears extends Message

//GUI API
case class Progress(numberOfSyncGears: Int) extends Message
case class SetCalculatedSyncSpeed(syncSpeed: Int) extends Message
case class GearProblem(ref : String) extends Message
case class GiveUp(victimActorRef : String) extends Message
case class AllGears(allGears: List[String]) extends Message
case class ReceivedSpeedGUI(ref: String) extends Message
case class CurrentSpeedGUI(ref : String, speed: Int) extends Message

//Saboteur API
case class SabotageRandom() extends Message
case class SabotageRandomFrom(list: List[ActorRef]) extends Message
case class SabotageManual(ref : String, toSpeed: Int) extends Message
case class SabotageManualFrom(list: List[ActorRef], ref : String, toSpeed: Int) extends Message