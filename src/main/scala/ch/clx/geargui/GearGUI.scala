package ch.clx.geargui

/**
 * Created by IntelliJ IDEA.
 * User: pmei
 * Date: 11.02.2010
 * Time: 15:25:25
 * Package: ch.clx.geargui
 * Class: GearGUI
 */

import scala.swing._
import scala.swing.Swing._
import collection.mutable.ListBuffer
import event._
import se.scalablesolutions.akka.actor.{Actor, ActorRef}
import se.scalablesolutions.akka.actor.Actor._
import actors.Scheduler

object GearGUI extends SimpleSwingApplication {

  //Set manually if you want to have more Gears/Sliders
  private val nOfGears = 40

  //Needed to track the State of the simulation
  private var nOfSynchGears = 0

  //This coll serves as a // coll to the internal contents coll. Needed for access to the elements
  private val sliderCollection = new ListBuffer[GearSlider]

  //Actors used for Communication
  private var gearController: ActorRef = null
  private var saboteur: ActorRef = null

  //ForkJoinScheduler is default
  var isForkJoinScheduler: Boolean = true
  var schedulerName: String = "forkJoinScheduler"

  /**
   * Setup all GUI components here
   * All are accessible from this application
   */
  object startButton extends Button {text = "Start"}
  object sabotageButton extends Button {text = "Sabotage"}
  object progressBar extends ProgressBar {labelPainted = true; max = nOfGears; value = 0}
  object calculatedSpeedLabel extends Label {text = "Calculated speed"}
  object calculatedSpeedTextField extends TextField {text = "0"; columns = 3}


  def top = new MainFrame {


    /**
     * Set properties for mainframe
     */
    title = "A simulation of gears using actors and scala-swing in Scala 2.8"
    preferredSize = new java.awt.Dimension(1200, 500)

    menuBar = new MenuBar {
      contents += new Menu("File") {
        contents += new MenuItem(Action("Quit") {
          cleanup
          dispose
        })
      }
      contents += new Menu("Control") {
        contents += new MenuItem(Action("Start") {
          startSimulation
        })
        contents += new MenuItem(Action("Random sabotage n Gears") {
          if (isSimulationRunning) {
            doSabotage()
          }
        })
      }
    }

    /**
     * Start with setting up the gui
     */
    contents = new SplitPane {

      /**
       * Initial properties for SplitPane
       */
      dividerLocation = 250
      dividerSize = 8
      oneTouchExpandable = true
      orientation = Orientation.Vertical;

      /**
       *  Contains controls for the simulation
       */
      val buttonPanel = new FlowPanel {
        preferredSize = new java.awt.Dimension(200, 0)
        contents += startButton
        contents += sabotageButton
        contents += calculatedSpeedLabel
        contents += calculatedSpeedTextField
        contents += progressBar
      }

      /**
       * Contains n singleton instances of GearSlider
       * Each slider represents a Gear
       */
      val gearPanel = new FlowPanel {
        preferredSize = new java.awt.Dimension(600, 0)
        for (i <- 0 to nOfGears - 1) {
          object slider extends GearSlider {
            min = 0
            value = 0
            max = 1000
            majorTickSpacing = 100
            //must be set to true, otherwise the background color does not show
            opaque = true
            sliderId = i.toString
          }
          contents += slider
          sliderCollection += slider
        }
      }


      /**
       * Register both components with the parent SplitPane
       */
      leftComponent = buttonPanel
      rightComponent = gearPanel

    }

    /**
     * Define listener and patterns for GUI-eventmatching
     */
    listenTo(startButton)
    listenTo(sabotageButton)
    sliderCollection.foreach(s => listenTo(s))
    sliderCollection.foreach(s => listenTo(s.mouse.clicks))
    reactions += {
      case ButtonClicked(`startButton`) =>
        println("[GearGUI] Startbutton")
        startSimulation
      case ButtonClicked(`sabotageButton`) =>
        println("[GearGUI] Sabotage")
        doSabotage();
      case MouseReleased(slider: GearSlider, _, _, 2, _) =>
        if (slider.background == java.awt.Color.BLACK) {
          revive(slider.sliderId)
        }
      case MouseReleased(slider: GearSlider, _, _, _, _) =>
        doSabotage(slider.sliderId, slider.value)

      case _ =>
      //println("AnyEvent: ")
    }
  }

  def startSimulation = {
    println("[GearGUI] starting new simulation")

    saboteur = Actor.actorOf[Saboteur]
    saboteur.start()

    val guiActor = receiver
    guiActor.start //stopped in GearController

    gearController = Actor.actorOf(new GearController(guiActor, schedulerName))
    gearController.start()

    gearController ! StartSync

    startButton.enabled = false

  }

  def cleanup() = {
    //needed if the simulation is started n times
    if (gearController != null) {
      gearController ! CleanUp
      gearController = null
    }

    if (saboteur != null) {
      saboteur.stop
      saboteur = null
    }

    progressBar.value = 0
    nOfSynchGears = 0
  }

  def isSimulationRunning = nOfSynchGears > 0 && nOfSynchGears < nOfGears

  def handleStartButton() = {
    if (isSimulationRunning) {
      startButton.enabled = false
    } else {
      cleanup()
      startButton.enabled = true
    }
  }

  def gearCollection: ListBuffer[ActorRef] = gearController.!!(GetGears) match {
    case Some(gears: ListBuffer[ActorRef]) => gears
    case None => ListBuffer()
  }

  /**
   * Revive if possible
   */
  def revive(gearId: String) = {
    gearCollection.find(_.id.equals(gearId)) match {
      case Some(gear) => gearController ! Revive(gear)
      case None => ()
    }
  }

  /**
   * Do a total random sabotage (random gear-selection, and random sabotage-value)
   */
  def doSabotage() = {
    if (isSimulationRunning) {

      val sabotageList = (0 until nOfGears).map(i => gearCollection(scala.util.Random.nextInt(gearCollection.length)))
      saboteur ! Sabotage(sabotageList.toList)
    }
  }

  /**
   * Do sabotage one Gear (choosen via the Slider)
   */
  def doSabotage(gearId: String, toSpeed: Int) = {
    if (isSimulationRunning) {
      gearCollection.find(_.id.equals(gearId)) match {
        case Some(gear) => saboteur ! SabotageManual(gear, toSpeed)
        case None => ()
      }

    }
  }

  /**
   * Define the actor/API for this GUI
   */

  def receiver = Actor.actorOf(new Actor {
    def receive = {
      case CurrentSpeed(gearId: String, speed: Int) =>
        //println("[GearGUI] (" + gearId + ")] SetSpeed to newSpeed: " + speed)
        findSlider(gearId).value = speed
        if (findSlider(gearId).background != java.awt.Color.RED) {
          findSlider(gearId).background = java.awt.Color.YELLOW
        }
      case GearProblem(gearId: String) =>
        println("[GearGUI] Recieved gear problem - due to Sabotage!")
        findSlider(gearId).background = java.awt.Color.RED
      case Progress(numberOfSyncGears: Int) =>
        println("[GearGUI] Progress: " + numberOfSyncGears)
        progressBar.value = numberOfSyncGears
        nOfSynchGears = numberOfSyncGears
        handleStartButton()

      case ReceivedSpeed(gearId: String) =>
        println("[GearGUI] ReceivedSpeed gearId: " + gearId)
        findSlider(gearId).background = java.awt.Color.GREEN
      case SetCalculatedSyncSpeed(syncSpeed: Int) =>
        println("[GearGUI] SetCalculatedSyncSpeed syncSpeed: " + syncSpeed)
        calculatedSpeedTextField.text = syncSpeed.toString
      case Crashed(gearActor) => {
        println("[GearGUI] Recieved gear problem - due to Exception!")
        findSlider(gearActor.id).background = java.awt.Color.MAGENTA
      }
      case GiveUp(victimActorRef) => {
        println("[GearGUI] Recieved gear problem - give up!")
        findSlider(victimActorRef.id).background = java.awt.Color.BLACK
      }
      case _ => println("[GearGUI] Message could not be evaluated!")
    }
  })

  def findSlider(gearId: String) = {
    sliderCollection.find(_.sliderId == gearId).get
  }
}


class GearSlider extends Slider {
  var sliderId: String = "";
}