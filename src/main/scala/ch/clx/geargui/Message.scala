package ch.clx.geargui


/**
 * Created by IntelliJ IDEA.
 * User: pmei
 * Date: 11.02.2010
 * Time: 15:20:44
 * Package: ch.clx.geargui
 * Class: Message
 */
// test commit

import actors.Actor
import collection.mutable.ListBuffer
import se.scalablesolutions.akka.actor.ActorRef

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
case class CurrentSpeed(gearId: String, speed: Int) extends Message  //Used in GUI too
case class ReceivedSpeed(gearId: String) extends Message //Used in GUI too
case class ReportInterrupt extends Message
case class GetGears extends Message
case class CleanUp extends Message
case class Revive(gear : ActorRef) extends Message

//GUI API
case class Progress(numberOfSyncGears: Int) extends Message
case class SetCalculatedSyncSpeed(syncSpeed: Int) extends Message
case class GearProblem(gearId: String) extends Message
case class GiveUp(victimActorRef : ActorRef) extends Message

//saboteur API
case class Sabotage(nGears: List[ActorRef]) extends Message
case class SabotageManual(gear: ActorRef, toSpeed: Int) extends Message