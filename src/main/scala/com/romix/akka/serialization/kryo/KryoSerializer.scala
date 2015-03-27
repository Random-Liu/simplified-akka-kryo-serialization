/** *****************************************************************************
  * Copyright 2012 Roman Levenstein
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  * *****************************************************************************/

package com.romix.akka.serialization.kryo

import akka.serialization._
import akka.actor.ExtendedActorSystem
import akka.actor.ActorRef
import akka.event.Logging
import scala.collection.JavaConversions._
import com.esotericsoftware.kryo.{Registration, Kryo}
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.objenesis.strategy.StdInstantiatorStrategy

//import com.esotericsoftware.shaded.org.objenesis.strategy.StdInstantiatorStrategy

import com.esotericsoftware.kryo.util._
import scala.util.{Try, Success, Failure}
import com.esotericsoftware.minlog.{Log => MiniLog}

class KryoSerializer(val system: ExtendedActorSystem) extends Serializer {

  import KryoSerialization._

  val log = Logging(system, getClass.getName)

  val settings = new Settings(system)

  locally {
    log.debug("Got mappings: {}", settings.ClassNameMappings)
    log.debug("Got classes: {}", settings.ClassNames)
    log.debug("Got kryo-custom-serializer-init: {}", settings.KryoCustomSerializerInit)
    log.debug("Got buffer-size: {}", settings.BufferSize)
    log.debug("Got max-buffer-size: {}", settings.MaxBufferSize)
    log.debug("Got serializer-pool-size: {}", settings.SerializerPoolSize)
    log.debug("Got kryo-reference-map: {}", settings.KryoReferenceMap)
    log.debug("Got reference-enabled: {}", settings.ReferenceEnabled)
    log.debug("Got implicit-registration-enabled: {}", settings.ImplicitRegistrationEnabled)
    log.debug("Got implicit-registration-logging: {}", settings.ImplicitRegistrationLogging)
    log.debug("Got kryo-trace: {}", settings.KryoTrace)
  }

  val customSerializerInitClass =
    if (settings.KryoCustomSerializerInit == null) null
    else
      system.dynamicAccess.getClassFor[AnyRef](settings.KryoCustomSerializerInit) match {
        case Success(clazz) => Some(clazz)
        case Failure(e) => {
          log.error("Class could not be loaded and/or registered: {} ", settings.KryoCustomSerializerInit)
          throw e
        }
      }

  val customizerInstance = Try(customSerializerInitClass.map(_.newInstance))

  val customizerMethod = Try(customSerializerInitClass.map(_.getMethod("customize", classOf[Kryo])))

  locally {
    log.debug("Got serializer init class: {}", customSerializerInitClass)
    log.debug("Got customizer instance: {}", customizerInstance)
    log.debug("Got customizer method: {}", customizerMethod)
  }

  // includeManifest is useless for kryo
  def includeManifest: Boolean = false

  // A unique identifier for this Serializer
  def identifier = 848200 // My student number, :)

  // Delegate to a real serializer
  def toBinary(obj: AnyRef): Array[Byte] = {
    val ser = getSerializer
    val bin = ser.toBinary(obj)
    releaseSerializer(ser)
    bin
  }

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    val ser = getSerializer
    val obj = ser.fromBinary(bytes, clazz)
    releaseSerializer(ser)
    obj
  }

  val serializerPool = new ObjectPool[KryoBasedSerializer](settings.SerializerPoolSize, () => {
    new KryoBasedSerializer(getKryo(),
      settings.BufferSize,
      settings.MaxBufferSize)
  })

  locally {
    log.debug("Got serializer pool: {}", serializerPool)
  }

  private def getSerializer = serializerPool.fetch

  private def releaseSerializer(ser: KryoBasedSerializer) = serializerPool.release(ser)

  private def getKryo(): Kryo = {
    if (settings.KryoTrace)
      MiniLog.TRACE()

    val referenceResolver = if (settings.KryoReferenceMap) new MapReferenceResolver() else new ListReferenceResolver()

    val kryo =
      if (settings.ImplicitRegistrationLogging)
        new Kryo(new DefaultClassResolver() {
          // This is just a log wrapper current now
          override def registerImplicit(typ: Class[_]): Registration = {
            val registration = super.registerImplicit(typ);
            if (registration.getId == DefaultClassResolver.NAME)
              MiniLog.info("Implicitly registered class: " + typ.getName)
            registration
          }
        }, referenceResolver /*, new DefaultStreamFactory()*/)
      else
        new Kryo(new DefaultClassResolver(), referenceResolver /*, new DefaultStreamFactory()*/)

    // Support deserialization of classes without no-arg constructors
    kryo.setInstantiatorStrategy(new StdInstantiatorStrategy())

    // Add ActorRef serializer
    kryo.addDefaultSerializer(classOf[ActorRef], new ActorRefSerializer(system))


    kryo.setRegistrationRequired(!settings.ImplicitRegistrationEnabled)

    for ((fqcn: String, idNum: String) <- settings.ClassNameMappings) {
      val id = idNum.toInt
      // Load class
      system.dynamicAccess.getClassFor[AnyRef](fqcn) match {
        case Success(clazz) => kryo.register(clazz, id)
        case Failure(e) => {
          log.error("Class could not be loaded and/or registered: {} ", fqcn)
          throw e
        }
      }
    }

    for (classname <- settings.ClassNames) {
      // Load class
      system.dynamicAccess.getClassFor[AnyRef](classname) match {
        case Success(clazz) => kryo.register(clazz)
        case Failure(e) => {
          log.warning("Class could not be loaded and/or registered: {} ", classname)
          /* throw e */
        }
      }
    }

    kryo.setReferences(settings.ReferenceEnabled)

    Try(customizerMethod.get.get.invoke(customizerInstance.get.get, kryo))

    kryo
  }
}

/** *
   Kryo-based serializer backend 
  */
class KryoBasedSerializer(val kryo: Kryo, val bufferSize: Int, val maxBufferSize: Int) {

  // "toBinary" serializes the given object to an Array of Bytes
  def toBinary(obj: AnyRef): Array[Byte] = {
    val buffer = getBuffer
    try {
      kryo.writeClassAndObject(buffer, obj)
      buffer.toBytes()
    } finally
      releaseBuffer(buffer)
  }

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    kryo.readClassAndObject(new Input(bytes))
  }

  val buf = new Output(bufferSize, maxBufferSize)

  private def getBuffer = buf

  private def releaseBuffer(buffer: Output) = {
    buffer.clear()
  }
}


import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

// Support pooling of objects. Useful if you want to reduce 
// the GC overhead and memory pressure.
class ObjectPool[T](number: Int, newInstance: () => T) {

  private val size = new AtomicInteger(0)
  private val pool = new ArrayBlockingQueue[T](number)

  def fetch(): T = {
    pool.poll() match {
      case o if o != null => o
      case null => createOrBlock
    }
  }

  def release(o: T): Unit = {
    pool.offer(o)
  }

  def add(o: T): Unit = {
    pool.add(o)
  }

  private def createOrBlock: T = {
    size.get match {
      case e: Int if e == number => block
      case _ => create
    }
  }

  private def create: T = {
    size.incrementAndGet match {
      case e: Int if e > number => size.decrementAndGet; fetch()
      case e: Int => newInstance()
    }
  }

  private def block: T = {
    val timeout = 5000
    pool.poll(timeout, TimeUnit.MILLISECONDS) match {
      case o if o != null => o
      case _ => throw new Exception("Couldn't acquire object in %d milliseconds.".format(timeout))
    }
  }
}
