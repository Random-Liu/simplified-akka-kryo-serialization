package com.romix.akka.serialization

import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import com.typesafe.config.ConfigFactory

/**
 * Created by taotao on 15-3-27.
 */
/**
 * Created by taotao on 15-3-26.
 */

private[serialization] object AkkaSimpleTest {
  private class B(a : A) {
    var p: A = a;
    var b: Int = 0
  }

  private class C(a : A) {
    var p: A = a;
    var c: Int = 0
  }

  private class A {
    var b: B = new B(this)
    var c: C = new C(this)
  }

  private class Actor1(actor2 : ActorRef) extends Actor {
    val a = new A()
    a.b.b = 1
    a.c.c = 2
    actor2 ! a
    def receive = {
      case _ =>
    }
  }

  private class Actor2 extends Actor {
    def receive = {
      case msg : A =>
        assert(msg.b.b == 1)
        assert(msg.c.c == 2)
        assert(msg.b.p.b.b == 1)
        assert(msg.b.p.c.c == 2)
        assert(msg.c.p.b.b == 1)
        assert(msg.c.p.c.c == 2)
        println("Test All Passed!");
    }
  }

  val ACTOR_SYSTEM_CONFIG =
    """
      |akka.actor.serialize-messages = on
      |akka.actor.provider="akka.remote.RemoteActorRefProvider"
      |akka.remote.netty.tcp.port=0
      |akka.loglevel="DEBUG"
      |akka.stdout-loglevel="DEBUG"
      |akka.remote.netty.tcp.maximum-frame-size=500000000
      |akka.remote.transport-failure-detector.acceptable-heartbeat-pause=240s
      |akka.extensions=["com.romix.akka.serialization.kryo.KryoSerializationExtension$"]
      |akka.actor.serializers.kryo="com.romix.akka.serialization.kryo.KryoSerializer"
      |akka.actor.kryo {
      | implicit-registration-logging=true
      | kryo-trace=true
      |}
      |akka.actor.serialization-bindings {
      | "com.romix.akka.serialization.AkkaSimpleTest$A"=kryo
      |}
    """.stripMargin

  def main(args: Array[String]) {
    val system1 = ActorSystem("system1", ConfigFactory.load(ConfigFactory.parseString(ACTOR_SYSTEM_CONFIG)))
    val system2 = ActorSystem("system2", ConfigFactory.load(ConfigFactory.parseString(ACTOR_SYSTEM_CONFIG)))

    val actor2 = system2.actorOf(Props[Actor2])
    val actor1 = system1.actorOf(Props(classOf[Actor1], actor2))
  }
}
