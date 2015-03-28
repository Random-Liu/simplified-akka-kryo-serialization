simplified-akka-kryo-serialization - simplified version of akka-kryo-serialization
==================================================================================

*akka-kryo-serialization* is an excellent library which provides custom Kryo-based serializers for Scala and Akka.
([akka-kryo-serialization](https://github.com/romix/akka-kryo-serialization))

However, when we used it, we found that the features are too many for us. It mades our code redundant and hard to maintain.
Which is most important is that the key feature we need in our project, implicit class registration, is not well
supported in *akka-kryo-serialization*, because the `registerImplicit` function is overrided with strange behavior.

So we decided to simplify the code of *akka-kryo-serialization* to be a very simplified Akka Kryo extension -
*simplified-akka-kryo-serialization*.

Features We Keep
----------------
*   Kryo extension for Akka.
*   Kryo configured in Akka ActorSystem configuration.
*   Kryo serialization for Akka ActorRef.

Features We Remove
------------------
*   Scala types serialization.
*   Post serialization transformations.
*   Different `IdStrategies`. We only need to identify whether implicit class registration is enabled.
*   Configuration `use-manifests`. Because `includeManifests` in Akka Serializer interface is almost useless for Kryo serialization. We just set it to *false* and remove `use-manifests` in configuration to make it less confusing.

Features We Add
---------------
*   Well supported implicit class registration.
*   Configuration `max-buffer-size`. Because we may transfer very big objects in our project, so we add `max-buffer-size` to make it configurable.

Build Guide
-----------
Build command:

    `sbt compile package`

Configuration of simplified-akka-kryo-serialization
---------------------------------------------------
The configuration of *simplified-akka-kryo-serialization* only has a little differences with *akka-kryo-serialization*.

The following options are available for configuring this serializer:

*   You need to add a following line to the list of your Akka extensions:
	`extensions = ["com.romix.akka.serialization.kryo.KryoSerializationExtension$"]`

*   You need to add a new `kryo` section to the akka.actor part of configuration  

		kryo  {
		    # Whether Kryo should support object graph serialization
            # true: Kryo supports serialization of object graphs with shared nodes
            # and cyclic references, but this comes at the expense of a small overhead
            # false: Kryo does not support object grpahs with shared nodes, but is usually faster
            reference-enabled = true

            # Whether Kryo should support implicit class registration
            # true: Kryo supports implicit class registration. Class can be serialized and deserialized
            # without pre-registration but with lower efficiency.
            # false: Kryo does not support implicit class registration. It will throw an exception when
            # an unregistered class needs to be serialized or deserialized.
            implicit-registration-enabled = true

            # Log implicitly registered classes. Useful, if you want to know all classes
            # which are serialized
            implicit-registration-logging = false

            # Define a default size for byte buffers used during serialization
            buffer-size = 4096

            # Define max size for byte buffers used during serialization
            # 1073741824 == 1G
            max-buffer-size = 1073741824

            # Define a default size for serializer pool
            serializer-pool-size = 16

            # If enabled, Kryo logs a lot of information about serialization process.
            # Useful for debugging and lowl-level tweaking
            kryo-trace = false

            # If enabled, Kryo uses internally a map detecting shared nodes.
            # This is a preferred mode for big object graphs with a lot of nodes.
            # For small object graphs (e.g. below 10 nodes) set it to false for
            # better performance.
            kryo-reference-map = true

            # Define mappings from a fully qualified class name to a numeric id.
            # Smaller ids lead to smaller sizes of serialized representations.
            # The smallest possible id should start at 9 (or even higher), because
            # ids below it are used by Kryo internally.
            # This section is optional.
            mappings {
                # fully.qualified.classname1 = id1
                # fully.qualified.classname2 = id2
            }

            # Define a set of fully qualified class names for
            # classes to be used for serialization.
            # The ids for those classes will be assigned automatically,
            # but respecting the order of declaration in this section.
            # This section is optional.
            classes = [
                # fully.qualified.classname1
                # fully.qualified.classname2
            ]
		}

*   You should declare in the Akka `serializers` section a new kind of serializer:  

		serializers {  
			java = "akka.serialization.JavaSerializer"  
			# Define kryo serializer   
			kryo = "com.romix.akka.serialization.kryo.KryoSerializer"  
		}    
     
*    As usual, you should declare in the Akka `serialization-bindings` section which classes should use kryo serialization. One thing to keep in mind is that classes that you register in this section are supposed to be *TOP-LEVEL* classes that you wish to serialize. I.e. this is a class of object that you send over the wire. It should not be a class that is used internally by a top-level class. The reason for it: Akka sees only an object of a top-level class to be sent. It picks a matching serializer for this top-level class, e.g. a default Java serializer, and then it serializes the whole object graph with this object as a root using this Java serializer.

Other Usage Information
-----------------------
*   How do you create mappings or classes sections with proper content?
*   How to create a custom initializer for Kryo?

See [akka-kryo-serialization](https://github.com/romix/akka-kryo-serialization)