/**
 * Akka cluster chat
 * Author: Panfilov V.I.
 * 10.2020
 */
import Main.{system, sessionManager, name, myPath}
import ChatWindow._
import javafx.application.{Application, Platform}
import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import scala.collection.mutable.HashMap

class ClusterListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart() {
    cluster.subscribe(self, InitialStateAsEvents, classOf[MemberEvent],
      classOf[UnreachableMember])
  }

  override def postStop() {
    cluster.unsubscribe(self)
  }

  def receive = {
    case MemberUp(member) =>
      log.info(s"[Listener] node is up: $member")
      if (member.address + "/user/sessionManager" != myPath) {
        var actorPath = member.address + "/user/sessionManager"
        sessionManager ! RequestNameSession(actorPath) //запрос имени и адреса пользователя
      }

    case UnreachableMember(member) =>
      log.info(s"[Listener] node is unreachable: $member")
      var actorPath = member.address + "/user/sessionManager"
      sessionManager ! RemoteLogout(actorPath) //удаление вышедшего пользователя из списка

    case MemberRemoved(member, prevStatus) =>
      log.info(s"[Listener] node is removed: $member")

    case ev: MemberEvent =>
      log.info(s"[Listener] event: $ev")
  }
}

class Session(userID: String) extends Actor {

  def receive = {
    case SelfPrivateChatMsg(from, to, message) =>
      Platform.runLater(new Runnable() {
        override def run(): Unit = {
          ChatWindow.textArea.appendText("[private to " + to + "] Ме: " + message + "\n")
        }
      })
    case SelfCommonChatMsg(from, to, message) =>
      Platform.runLater(new Runnable() {
        override def run(): Unit = {
          ChatWindow.textArea.appendText("[Common room] Ме: " + message + "\n")
        }
      })
    case PrivateChatMsg(from, to, message) =>
      Platform.runLater(new Runnable() {
        override def run(): Unit = {
          ChatWindow.textArea.appendText("[private] " + from + ": " + message + "\n")
        }
      })
    case CommonChatMsg(from, message) =>
      Platform.runLater(new Runnable() {
        override def run(): Unit = {
          ChatWindow.textArea.appendText(from + ": " + message + "\n")
        }
      })
  }
}
import akka.actor.{ActorRef, ActorSystem, Props, Actor}

case class SelfPrivateChatMsg(from: String, to: String, message: String)
case class SelfCommonChatMsg(from: String, to: String, message: String)
case class PrivateChatMsg(from: String, to: String, message: String)
case class CommonChatMsg(from: String, message: String)
case class RemoteLogin(from: String, name: String, session: ActorRef)
case class RemoteLogout(from: String)
case class RequestNameSession(from: String)
case class SendRequestNameSession(from: String)
case class ResponseNameSession(from: String, name: String, session: ActorRef)

class SessionManager extends Actor {
  val addressNick = new HashMap[String, String]
  val sessions = new HashMap[String, ActorRef]
  val session = system.actorOf(Props(new Session(name)))
  addressNick += (myPath -> name)
  sessions += (name -> session)

  def receive = {
    case RequestNameSession(from) =>
      system.actorSelection(from) ! SendRequestNameSession(from)
    case SendRequestNameSession(from) =>
      sender() ! RemoteLogin(from, name, session)
    case RemoteLogin(from, username, session) =>
      println(s"$username login")
      var boolUserEntered: Boolean = false
      for (key <- sessions.keySet if key == username) boolUserEntered = true
      if (boolUserEntered == false) {
        addressNick += (from -> username)
        sessions += (username -> session)
        Platform.runLater(new Runnable() {
          override def run(): Unit = {
            users.add(username)
          }
        })
      }
      boolUserEntered = false
    case RemoteLogout(from) =>
      var nick = addressNick(from)
      Platform.runLater(new Runnable() {
        override def run(): Unit = {
          users.remove(nick)
        }
      })
      sessions -= (addressNick(from))
      addressNick -= (from)
    case chatMsg@SelfPrivateChatMsg(from, to, message) =>
      sessions(from) ! chatMsg
    case chatMsg@SelfCommonChatMsg(from, to, message) =>
      sessions(to) ! chatMsg
    case chatMsg@PrivateChatMsg(from, to, message) =>
      sessions(to) ! chatMsg
    case chatMsg@CommonChatMsg(from, message) =>
      for (key <- sessions.keySet) {
        if (key == name) {
          sessionManager ! SelfCommonChatMsg(name, name, msgTextField.getText)
        } else {
          sessions(key) ! chatMsg
        }
      }
  }
}

object Main extends App {
  val system = ActorSystem("system")

  val clusterListener = system.actorOf(Props[ClusterListener], "clusterListener")

  val sessionManager = system.actorOf(Props[SessionManager], "sessionManager")

  /**
   * Имя пользователя и путь.
   */
  var name = "John"

  val myPath = "akka://system@127.0.0.1:2552/user/sessionManager"

  Application.launch(classOf[ChatWindow], args: _*)
}