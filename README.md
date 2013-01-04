A gear simulation in Scala
========

## Introduction
A simulation of synchronizing gears using akka-actors and scala-swing in Scala 2.10.

## What this branch contains:
- Usage of [akka Actors](http://akka.io)
- [Fault Tolerance](http://doc.akka.io/docs/akka/snapshot/java/fault-tolerance.html) through Supervisor Strategies a(k)ka "Let it crash"

## What is the purpose of...
### ...the slider colors:
- green: Gear is synced (= has the same speed as the calculated sync speed)
- yellow: Gear in progress
- red: Gear has been sabotaged (= a new speed was forced)
- magenta: Gear had an exception and is restarted by the supervisor
- black: Gear had 2 exceptions within 2 seconds (double click to revive)

### ...the parameters in the code:
- GearGUI nOfGears (increase if you want to have more gears)
- Gear failureLevel (increase if you want to have more exceptions)

## History
### Migration from Scala Actors to akka actors 1.3
- Forked pmeiclx/scala_gear_simulation
- Change from scala.actors.Actor to akka.actor._
- Change type of List / Map and dependencies to ActorRef
- Change loop / react to receive

### Update to akka 2.1.0 and Scala 2.10 (this branch)
- Forked dhob/scala_gear_simulation
- Changed akka implementation from 1.3 -> 2.0 -> 2.1 using the [akka migration guides](http://doc.akka.io/docs/akka/2.0.3/project/migration-guide-1.3.x-2.0.x.html)
- Minor enhancements to the GUI


## Monitoring
- YourKit for thread activity
