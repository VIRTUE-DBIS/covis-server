package ch.unibas.dmi.dbis.covis.server.rpc

import ch.unibas.dmi.dbis.covis.grpc.CovisGrpc
import ch.unibas.dmi.dbis.covis.grpc.CovisGrpc.UpdateMessage
import ch.unibas.dmi.dbis.covis.grpc.CovisServiceGrpc
import ch.unibas.dmi.dbis.covis.server.data.SyncableContainerStore
import ch.unibas.dmi.dbis.covis.server.utils.time
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CovisServiceImpl : CovisServiceGrpc.CovisServiceImplBase() {

    private val subscribers = ConcurrentHashMap<String, Pair<StreamObserver<UpdateMessage>, MessageObserver>>()

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun ping(request: Empty?, responseObserver: StreamObserver<Empty>?) {
        time("Responding to Ping") {
            responseObserver!!.onNext(Empty.newBuilder().build())
            responseObserver.onCompleted()
        }
    }

    override fun sync(serverToClientMessages: StreamObserver<UpdateMessage>?): StreamObserver<UpdateMessage> {
        val id: String = UUID.randomUUID().toString()
        logger.info { "Syncing with a new client with id $id" }
        val obs = MessageObserver(id, subscribers)
        //TODO Sync time in v3.0
        // Ordering is important because the syncable containers will contain outdated references.
        SyncableContainerStore.getAll().map { it.second }.forEach {
            serverToClientMessages!!.onNext(UpdateMessage.newBuilder().setContainer(it).setTimestamp(System.currentTimeMillis()).build())
        }
        subscribers[id] = Pair(serverToClientMessages!!, obs)
        return obs
    }

    internal class MessageObserver(private val id: String, private val subscribers: ConcurrentHashMap<String, Pair<StreamObserver<UpdateMessage>, MessageObserver>>) : StreamObserver<UpdateMessage> {

        override fun onNext(value: UpdateMessage?) {
            SyncableContainerStore.process(value!!, id)
            subscribers.filterNot { it.key == this.id }.forEach { sub ->
                sub.value.first.onNext(value)
                logger.trace { "Sent $value to ${sub.value.second.id}" }
            }
        }

        override fun onError(t: Throwable?) {
            logger.info { "Received Error from $id: ${t!!.message}. Disconnecting" }
            close()
        }

        override fun onCompleted() {
            logger.info { "Received Completion from $id. Disconnecting" }
            close()
        }

        private fun close() {
            if (subscribers.containsKey(id)) {
                subscribers[id]!!.first.onCompleted()
                subscribers.remove(id)
            }
            val disconnect = CovisGrpc.DisconnectMessage.newBuilder().addAllContainers(SyncableContainerStore.getAll().filter { it.first == id }.map { it.second }).build()
            val update = UpdateMessage.newBuilder().setDisconnect(disconnect).setTimestamp(System.currentTimeMillis()).build()
            subscribers.filterNot { it.key == id }.forEach { it.value.first.onNext(update) }
            SyncableContainerStore.removeSubscriber(id)
        }

    }
}