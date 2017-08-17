A gear simulation in Scala
========

## Introduction
A simulation of synchronizing gears modeled with akka-actors and scala-swing in Scala 2.11.x

Clone and run with:
```
mvn
```

## Concepts
![Alt text](concepts.jpg?raw=true "Concepts")

- Each _Gear_ is an akka Actor which is represented in the _GearGUI_ by a slider
- The gears are supervised by the _GearController_ Actor
- The _Receiver_ Actor coordinates updates from the _GearController_ to the _GearGUI_


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

### Update to akka 2.5.4 and Scala 2.11.11
- Update pom.xml
- Adjust the params in Gear for faster completion
- Add deprecated note for Typesafe Console

### Update to akka 2.3.8 and Scala 2.11.4
-  Set the ErrorLevel via Slider from GUI
-  Run Application via sbt-atmos plug-in
-  Usage of Typesafe Console (deprecated) via via sbt-atmos plug-in
```
./sbt
> atmos:run-main ch.clx.geargui.GearGUI
[info] Starting Atmos and Typesafe Console ...
[info] Typesafe Console is available at http://localhost:9900
```

### Update to akka 2.2.3
- Change the gear actor behaviour with "akka become", so there is no need for a local var "speed" anymore
- Minor enhancements: Removed ActorRefs in Messages
- Added sbt and support for monitoring with Typesafe Console via sbt-atmos plug-in https://github.com/sbt/sbt-atmos

### Update to akka 2.1.0 and Scala 2.10 (this branch)
- Forked from dhob/scala_gear_simulation
- Changed akka implementation from 1.3 -> 2.0 -> 2.1 using the [akka migration guides](http://doc.akka.io/docs/akka/2.0.3/project/migration-guide-1.3.x-2.0.x.html)
- [Fault Tolerance](http://doc.akka.io/docs/akka/snapshot/java/fault-tolerance.html) through Supervisor Strategies a(k)ka "Let it crash"
- Minor enhancements to the GUI

## Monitoring
- YourKit for thread activity
- Typesafe Console via sbt-atmos plug-in
