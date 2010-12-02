A gear simulation in Scala
========

## Introduction
A simulation of gears using akka (1.0-M1) actors and scala-swing with Scala 2.8.final.
To get familiar with the akka actors I forked pmeiclx/scala_gear_simulation and changed the simulation from
scala actors to akka actors.

## What this branch contains:
- Usage of akka Actors (http://doc.akkasource.org/actors-scala)
- Fault Tolerance Through Supervisor Hierarchies a(k)ka "Let it crash" (http://doc.akkasource.org/fault-tolerance-scala)

## How it is achieved:
### Usage of akka Actors
- Change from scala.actors.Actor to se.scalablesolutions.akka.actor
- Change type of List / Map and dependencies to ActorRef
- Change loop / react to receive

### Fault Tolerance
- Programmatical linking and supervision of Actors
- OneForOne strategy (5 Exception in less than 20 seconds)

### Monitoring
- YourKit for thread activity

## What is the purpose of...
### ...the slider colors:
- green: Gear is synced
- yellow: Gear in progress
- red: Gear has been sabotaged
- magenta: Gear had an an exception (new in this branch)
- black: Gear had 5 exceptions within 20 seconds (double click to revive) (new in this branch)