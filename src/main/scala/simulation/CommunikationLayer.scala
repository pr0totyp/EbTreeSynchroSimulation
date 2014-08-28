package simulation

import akka.actor.{ActorRef, Actor}
import akka.actor.Actor.Receive
import model._
import model.EbTreeDataObject

import model.InsertNewObject
import akka.event.Logging
import src.main.scala.model.Delta
import scala.util.Random


/**
 * Created by prototyp on 19.06.14.
 */
class CommunikationLayer(var pktLossPsbl:Int) extends Actor{
  var cells:List[ActorRef] = _ // HashMap name> actRef
  var ebTreeMessageQueue:List[List[EBTreeMessage]] = List(List()) //first list cells, second list clock slots, third list messages for each time slot
//  var ebTreeMessageQueue1:List[List[EBTreeMessage]] = List(List())
//  var ebTreeMessageQueue2:List[List[EBTreeMessage]] = List(List())
  var synchroStart:ActorRef = _
  var log = Logging(context.system, this)

  override def receive: Receive = {
    //init
    case SetTreeActors(actors:List[ActorRef]) =>
      //ebTreeMessageQueue = ebTreeMessageQueue(0) :: List(List())
      //ebTreeMessageQueue = ebTreeMessageQueue(1) :: List(List())
      //actors.foreach(x=>ebTreeMessageQueue :: List(List(Nil)))
      cells = actors

    case l:SetLoss =>
      pktLossPsbl = l.loss

    //basic  operations
//----------------------------------------------------------------------------------------------------------------------------------
    case Clock =>
      log.info("[Clock] received!")
      if(!ebTreeMessageQueue.isEmpty){
        ebTreeMessageQueue.head.foreach(x => x.toActorRef ! x) // send all queued messages
        ebTreeMessageQueue = ebTreeMessageQueue.tail//delete head
      }

    case x:InsertNewObject[_] => //insert new item the head of the queue
      if(!isLost()){
        //log.info("[InsertNewObject] ! Placing item in queue")
        ebTreeMessageQueue = List(ebTreeMessageQueue.head ::: List(x) ) ::: ebTreeMessageQueue.tail
      }else{
        log.info("[InsertNewObject] ! Item Lost!")
      }
      //TODO insert failure => packet loss or delay

    case x:UpdateObject[_] =>
      //insert new item the head of the queue
      if(!isLost()){
        log.info("[UpdateObject] received! Placing item in queue")
        ebTreeMessageQueue = List(ebTreeMessageQueue.head ::: List(x) ) ::: ebTreeMessageQueue.tail
      }

    //sync operations
//----------------------------------------------------------------------------------------------------------------------------------
    case x:StartSynchro =>
      log.info("{" + self.path.name + "}" + "[StartSynchro] received! Syncing from: "+x.fromActorRef.get.path.name + " to: "+x.toActorRef.path.name)
      synchroStart = sender
      x.toActorRef ! Synchronize(Delta(0, 64, -1, -1, -2),Some(x.fromActorRef.get),x.toActorRef)

    case SynchroFinished =>
      log.info("{" + self.path.name + "}" + "[StopSynchro] finished! Syncing ")
      synchroStart ! SynchroFinished

    case sync:Synchronize =>
      //log.info("{" + self.path.name + "}" + "[Synchronize] Syncing "+ sync.fromActorRef.get.path.name)
      sync.toActorRef ! sync

    case x:CheckLeaf[_] =>
      x.toActorRef ! x

    case  x:RequestEbTreeDataObject =>
      x.toActorRef ! x
//----------------------------------------------------------------------------------------------------------------------------------

    case ShutDownActorRequest => context.system.shutdown()

    case _ => log.error("CommunicationActor wrong message received")
  }

  def isLost():Boolean = Random.nextInt(101) match {
    case x:Int if(x == 0) => false
    case x:Int if(x <= pktLossPsbl) => true
    case x:Int if(x > pktLossPsbl) => false
  }

}

// for each actor a fifo queeu which gets called via a clock

//Random  seed

// Should system be designed for n treeActors?

// what happens to synchro if tree part changes while synchro