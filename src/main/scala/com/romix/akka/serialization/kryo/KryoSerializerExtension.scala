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

import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import com.typesafe.config.Config

import akka.actor.{ActorSystem, Extension, ExtendedActorSystem}
import akka.event.Logging
import scala.util.{Failure, Success, Try}
import com.esotericsoftware.kryo.Kryo

object KryoSerialization {

  class Settings(val system: ExtendedActorSystem) {

    val config: Config = system.settings.config

    val log = Logging(system, getClass.getName)

    import scala.collection.JavaConverters._
    import config._

    // Whether kryo supports graph serialization: true / false
    val ReferenceEnabled: Boolean = config.getBoolean("akka.actor.kryo.reference-enabled")

    val ImplicitRegistrationEnabled: Boolean = config.getBoolean("akka.actor.kryo.implicit-registration-enabled")

    val ImplicitRegistrationLogging: Boolean = config.getBoolean("akka.actor.kryo.implicit-registration-logging")

    val BufferSize: Int = config.getInt("akka.actor.kryo.buffer-size")

    val MaxBufferSize: Int = config.getInt("akka.actor.kryo.max-buffer-size")

    val SerializerPoolSize: Int = config.getInt("akka.actor.kryo.serializer-pool-size")

    val KryoReferenceMap: Boolean = config.getBoolean("akka.actor.kryo.kryo-reference-map")

    // Each entry should be: FQCN -> integer id
    val ClassNameMappings: Map[String, String] = configToMap(getConfig("akka.actor.kryo.mappings"))

    val ClassNames: java.util.List[String] = config.getStringList("akka.actor.kryo.classes")

    val KryoCustomSerializerInit: String = Try(config.getString("akka.actor.kryo.kryo-custom-serializer-init")).getOrElse(null)

    val KryoTrace: Boolean = config.getBoolean("akka.actor.kryo.kryo-trace")

    // Whether Akka should add Class Information in Message
    // The top class is serialized and deserialized by KryoSerialilzer, when setting UseManifests KryoSerializer which
    // implements Akka.Serializer will add class information in top class. However, sub-classes are serialized by kryo,
    // not KryoSerializer. Kryo won't be affected by UseManifests. So we just always set UseManifests to be false.
    // val UseManifests: Boolean = config.getBoolean("akka.actor.kryo.use-manifests")

    private def configToMap(cfg: Config): Map[String, String] =
      cfg.root.unwrapped.asScala.toMap.map {
        case (k, v) => (k, v.toString)
      }
  }

}

class KryoSerialization(val system: ExtendedActorSystem) extends Extension {

  import KryoSerialization._

  val settings = new Settings(system)
  val log = Logging(system, getClass.getName)
}

object KryoSerializationExtension extends ExtensionId[KryoSerialization] with ExtensionIdProvider {
  override def get(system: ActorSystem): KryoSerialization = super.get(system)

  override def lookup = KryoSerializationExtension

  override def createExtension(system: ExtendedActorSystem): KryoSerialization = new KryoSerialization(system)
}