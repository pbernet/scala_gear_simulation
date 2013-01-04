package ch.clx.geargui

import scala.swing._
import collection.mutable.ListBuffer
import event._
import akka.actor.{Actor, ActorRef, Props, ActorSystem}
import concurrent.Await
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory


object GearGUI extends SimpleSwingApplication {

  //Set manually if you want to have more Gears/Sliders
  private val nOfGears = 10

  //Needed to track the state of the simulation
  private var nOfSynchGears = 0

  var isSimulationRunning = false

  //This coll serves as a shadow coll to the internal contents coll. Needed for access to the elements
  private var sliderCollection = new ListBuffer[GearSlider]


  val config = ConfigFactory.parseString("""
  akka.loglevel = DEBUG
  akka.actor.debug {
  receive = on
  lifecycle = on
  }
                                         """)
  val system = ActorSystem("MySystem", config)

  private var gearController: ActorRef = null
  private var saboteur: ActorRef = null
  private var guiActor: ActorRef = null


  /**
   * Setup all GUI components here
   * All are accessible from this application
   */
  object startButton extends Button {text = "Start"}
  object sabotageButton extends Button {text = "Sabotage"}
  object progressBar extends ProgressBar {labelPainted = true; max = nOfGears; value = 0}
  object calculatedSpeedLabel extends Label {text = "Calculated sync speed"}
  object calculatedSpeedTextField extends TextField {text = "0"; columns = 3}

  val startMenuItem = new MenuItem(Action("Start") {
    startSimulation
  })

  val randomSabotageMenuItem = new MenuItem(Action("Random sabotage n Gears") {
    if (isSimulationRunning) {
      doSabotage()
    }
  })

  def top = new MainFrame {

    title = "A simulation of synchronizing gears using akka-actors and scala-swing in Scala 2.10"
    preferredSize = new java.awt.Dimension(1200, 500)

    menuBar = new MenuBar {
      contents += new Menu("File") {
        contents += new MenuItem(Action("Quit") {
          cleanup()
          close()
          System.exit(0)
        })
      }
      contents += new Menu("Control") {
        contents += startMenuItem
        contents += randomSabotageMenuItem
      }
    }

    contents = new SplitPane {

      dividerLocation = 250
      dividerSize = 8
      oneTouchExpandable = true
      orientation = Orientation.Vertical

      /**
       *  Contains controls for the simulation
       */
      val buttonPanel = new FlowPanel {
        preferredSize = new java.awt.Dimension(200, 0)
        contents += startButton
        sabotageButton.enabled = false
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
     * Define listener and patterns for GUI-Eventmatching
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
        doSabotage()
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

  def startSimulation() {
    println("[GearGUI] starting new simulation")

    isSimulationRunning = true

    saboteur = system.actorOf(Props[Saboteur], name = "Saboteur")

    guiActor = createReceiverActor

    gearController = system.actorOf(Props(new GearController(guiActor)), name = "GearController")
    gearController ! StartSync

    startButton.enabled = false
    startMenuItem.enabled = false

  }

  def cleanup() {
    //needed if the simulation is started n times
    if (gearController != null) {
      gearController ! CleanUp
      system.stop(gearController)
      gearController = null
      system.stop(guiActor)
    }

    if (saboteur != null) {
      system.stop(saboteur)
      saboteur = null
    }

    progressBar.value = 0
    nOfSynchGears = 0
  }


  def handleControls() {
    if (isSimulationRunning) {
      startButton.enabled = false
      startMenuItem.enabled = false
      sabotageButton.enabled = true
      randomSabotageMenuItem.enabled = true

    } else {
      cleanup()
      startButton.enabled = true
      startMenuItem.enabled = true
      sabotageButton.enabled = false
      randomSabotageMenuItem.enabled = false
    }
  }

  def gearCollection: ListBuffer[ActorRef] = {

    //http://doc.akka.io/docs/akka/2.0.3/scala/futures.html
    implicit val timeout = Timeout(5 seconds)
    val future = gearController ? GetGears // enabled by the “ask” import
    Await.result(future, timeout.duration).asInstanceOf[ListBuffer[ActorRef]] match {
      case gears: ListBuffer[ActorRef] => gears
      case _ => ListBuffer[ActorRef]()
    }
  }

  /**
   * Revive triggered manually from gui
   */
  def revive(ref: String) {
    println("Revive entered via gui with ref: " + ref)
    gearCollection.find(_.actorRef.path.toString == ref) match {
      case Some(gear) => gearController ! Revive(gear)
      case None => ()
    }
  }

  /**
   * Do a total random sabotage (random gear-selection, and random sabotage-value)
   */
  def doSabotage() {
    if (isSimulationRunning) {
      println("Random sabotage entered")
      val sabotageList = (0 until nOfGears).map(i => gearCollection(scala.util.Random.nextInt(gearCollection.length))).filterNot( _.isTerminated)
      saboteur ! Sabotage(sabotageList.toList)
    }
  }

  /**
   * Do sabotage one Gear (chosen via the Slider)
   */
  def doSabotage(ref: String, toSpeed: Int) {
    if (isSimulationRunning) {
      println("Manual sabotage enterend for ref: " + ref + " with new Speed: " + toSpeed)
      gearCollection.find(_.actorRef.path.toString == ref) match {
        case Some(gear) if(!gear.isTerminated) => saboteur ! SabotageManual(gear, toSpeed)
        case _ => ()
      }

    }
  }

  /**
   * http://doc.akka.io/docs/akka/2.0.3/scala/actors.html#actors-scala
   * TODO See if warning "Creating Actors using anonymous classes" applies here
   */
  //
  def createReceiverActor = system.actorOf(Props(new Actor {
    println("Initialize GUIActor")
    def receive = {
      case CurrentSpeedGUI(ref: String, speed: Int) =>
        //println("[GearGUI] (" + gearId + ")] SetSpeed to newSpeed: " + speed)
        findSlider(ref).value = speed
        if (findSlider(ref).background != java.awt.Color.RED) {
          findSlider(ref).background = java.awt.Color.YELLOW
        }
      case GearProblem(ref: String) =>
        println("[GearGUI] Recieved gear problem - due to Sabotage!")
        findSlider(ref).background = java.awt.Color.RED
      case Progress(numberOfSyncGears: Int) =>
        println("[GearGUI] Progress: " + numberOfSyncGears)
        progressBar.value = numberOfSyncGears
        nOfSynchGears = numberOfSyncGears
        if (nOfSynchGears == nOfGears) isSimulationRunning = false else isSimulationRunning = true
        handleControls()

      case ReceivedSpeedGUI(ref: String) =>
        println("[GearGUI] ReceivedSpeedGUI ref: " + ref)
        findSlider(ref).background = java.awt.Color.GREEN
      case SetCalculatedSyncSpeed(syncSpeed: Int) =>
        println("[GearGUI] SetCalculatedSyncSpeed syncSpeed: " + syncSpeed)
        calculatedSpeedTextField.text = syncSpeed.toString
      case Crashed(gearActor) => {
        println("[GearGUI] Recieved gear problem - due to Exception!")
        findSlider(gearActor.path.toString).background = java.awt.Color.MAGENTA
      }
      case GiveUp(ref: String) => {
        println("[GearGUI] Recieved gear problem - give up!")
        findSlider(ref).background = java.awt.Color.BLACK
      }
      case GearsAmount(amount: Int) => {
        println("[GearGUI] Recieved GearsAmount")
        sender ! nOfGears
      }
     case AllGears(allPaths: List[String])=> {
        val zippedCol =  sliderCollection zip allPaths
        sliderCollection = zippedCol.collect{
          case(gearSlider ,path) => gearSlider.sliderId = path ; gearSlider
        }
      }
      case _ => println("[GearGUI] Message could not be evaluated!")
    }
  }))

  def findSlider(path: String) = {
    sliderCollection.find(_.sliderId == path).get
  }
}


class GearSlider extends Slider {
  var sliderId: String = _
}