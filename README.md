A gear simulation in Scala
========

## Introduction
A simulation of synchronizing gears using akka-actors and scala-swing in Scala 2.11.x

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

![Gear GUI](./GearGUI.png?raw=true)

### ...the parameters in the code:
- GearGUI nOfGears (increase if you want to have more gears)
- Gear errorLevel (raise initial value here to have more exceptions or use Slider in GUI)
- Gear sleepTime (raise initial value here to slow down simulation or use Slider in GUI)

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

### Update to akka 2.2.3
- Change the gear actor behaviour with "akka become", so there is no need for a local var "speed" anymore
- Minor enhancements: Removed ActorRefs in Messages
- Added sbt and support for monitoring with Typesafe Console via sbt-atmos plug-in https://github.com/sbt/sbt-atmos

### Update to akka 2.3.8 and Scala 2.11.4
-  Set the ErrorLevel via Slider from GUI
-  Usage of Typesafe Console via via sbt-atmos plug-in
```
./sbt
> atmos:run-main ch.clx.geargui.GearGUI
[info] Starting Atmos and Typesafe Console ...
[info] Typesafe Console is available at http://localhost:9900
```

## Monitoring
- YourKit for thread activity
- Typesafe Console via sbt-atmos plug-in
