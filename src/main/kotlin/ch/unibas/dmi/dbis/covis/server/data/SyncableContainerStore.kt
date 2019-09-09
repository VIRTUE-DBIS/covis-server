package ch.unibas.dmi.dbis.covis.server.data

import ch.unibas.dmi.dbis.covis.grpc.CovisGrpc
import ch.unibas.dmi.dbis.covis.grpc.CovisGrpc.SyncableContainer
import ch.unibas.dmi.dbis.covis.grpc.CovisGrpc.UpdateMessage
import mu.KotlinLogging

typealias ContainerUUID = String
typealias SyncableUUID = String
typealias SubscriberUUID = String

object SyncableContainerStore {

    private val logger = KotlinLogging.logger { }

    /**
     * Stores for each container uuid which subscriber it belongs to and which syncable container is associated with said uuid
     */
    val store = mutableMapOf<ContainerUUID, Pair<SubscriberUUID, SyncableContainer>>()

    /**
     * Stores for each syncable uuid which container uuid it belongs to
     */
    val index = mutableMapOf<SyncableUUID, ContainerUUID>()

    /**
     * Process one update message at a time. Synchronized to avoid concurrency problems.
     *
     * @param update the UpdateMessage which is to be processed
     * @param id uuid of the subscriber sending the update
     * @return if the processing of the update was successful
     */
    @Synchronized
    fun process(update: UpdateMessage, id: SubscriberUUID): Boolean {
        if (update.hasSyncable()) {
            logger.trace { "updating syncable: {\n$update \n}" }
            val syncable = update.syncable!!
            if (syncable.uuid.isBlank()) {
                logger.error { "Received blank syncable uuid for syncable $syncable and subscriber $id. This is an error by clients. Exiting." }
                return false
            }
            if (index.containsKey(syncable.uuid)) {
                val container = store[index[syncable.uuid]]!!.second
                val builder = SyncableContainer.newBuilder().mergeFrom(container)
                //Merge old syncable (retrieved from store via containeruuid (retrieved from index) with new syncable)
                val newSyncable = CovisGrpc.Syncable.newBuilder().mergeFrom(container.syncablesMap.values.first { it.uuid == syncable.uuid }).mergeFrom(syncable).build()
                builder.putSyncables(container.syncablesMap.filter { it.value.uuid == syncable.uuid }.keys.toList().first(), newSyncable)
                val newContainer = builder.build()

                store[index[syncable.uuid]!!] = Pair(id, newContainer)
            } else {
                //Even if syncables are added at runtime, the container should always be updated first
                logger.error { "Received update message with unknown syncable!!! $update" }
                return false
            }
            return true
        }
        if (update.hasContainer()) {
            val newContainer = update.container!!
            if (newContainer.uuid.isBlank()) {
                logger.error { "Received empty syncableContainer uuid for container $newContainer and subscriber $id. Exiting" }
                return false
            }
            newContainer.syncablesMap.values.forEach {
                if (it.uuid.isBlank()) {
                    logger.error { "Received blank syncable uuid for container $newContainer and subscriber $id. This is an error by clients. Exiting." }
                    return false
                }
            }
            if (store.containsKey(newContainer.uuid)) {
                logger.trace { "container with uuid ${newContainer.uuid} found in store, updating" }
                //We know this container
                val old = store[newContainer.uuid]!!.second
                //If there is a syncable which we have not seen yet, we need to update the index
                newContainer.syncablesMap.forEach { newSyncable ->
                    //check if the syncable already exists in the existing stored syncable
                    if (old.syncablesMap.values.count { it.uuid == newSyncable.value.uuid } == 0) {
                        index[newSyncable.value.uuid] = newContainer.uuid
                    }
                }
                //Merge the old container with the new one
                val builder = SyncableContainer.newBuilder().mergeFrom(old).mergeFrom(newContainer)
                //Additionally, we need to merge the individual syncables
                newContainer.syncablesMap.forEach { (syncableKey, newSyncable) ->
                    //check if we had that syncable in the old map
                    val mergedSyncable = if (old.syncablesMap.count { it.value.uuid == newSyncable.uuid } != 0) {
                        //The syncable already exists, we need to merge it
                        CovisGrpc.Syncable.newBuilder().mergeFrom(old.syncablesMap.values.first { it.uuid == newSyncable.uuid }).mergeFrom(newSyncable).build()
                    } else {
                        newSyncable
                    }
                    //insert the merged syncables
                    builder.putSyncables(syncableKey, mergedSyncable)
                }
                val mergedContainer = builder.build()

                //store the merged container
                store[newContainer.uuid] = Pair(id, mergedContainer)
            } else {
                logger.trace { "Adding new container $newContainer" }
                //store container
                store[newContainer.uuid] = Pair(id, newContainer)
                //update index
                newContainer.syncablesMap.values.forEach { index[it.uuid] = newContainer.uuid }
            }
            return true
        }
        if (update.hasDeleteContainer()) {
            return removeContainer(update.deleteContainer.containerUUID)
        }
        if (update.hasDeleteSyncable()) {
            return removeSyncable(update.deleteSyncable.syncableUUID)
        }
        if (update.hasDisconnect()) {
            return removeSubscriber(id)
        }
        return true
    }

    private fun removeContainer(containerUUID: String): Boolean {
        logger.trace { "Removing container $containerUUID" }
        store[containerUUID]!!.second.syncablesMap.values.forEach {
            //Remove all uuids from the syncable
            if (!index.contains(it.uuid)) {
                logger.error { "Trying to remove container $containerUUID with syncable $it which is not indexed. This should only happen for syncables which are shared between multiple clients." }
                return false
            }
            index.remove(it.uuid)
        }
        store.remove(containerUUID)
        return true
    }

    private fun removeSyncable(syncableUUID: String): Boolean {
        logger.trace { "Removing syncable $syncableUUID" }
        if (!index.containsKey(syncableUUID)) {
            logger.error { "Trying to remove unknown syncable with uuid $syncableUUID. This is not ideal." }
            return false
        }
        val old = store[index[syncableUUID]]!!
        //Create a new container with everything remaining except this specific syncable
        val syncableMatches = old.second.syncablesMap.filter { it.value.uuid == syncableUUID }
        if (syncableMatches.size != 1) {
            logger.error { "Trying to remove syncable with uuid $syncableUUID, but it has ${syncableMatches.size} matches in container ${old.first}. This is not ideal." }
            return false
        }
        val new = SyncableContainer.newBuilder().mergeFrom(old.second).removeSyncables(syncableMatches.keys.first()).build()
        //update store
        store[index[syncableUUID]!!] = Pair(old.first, new)
        //remove from index
        index.remove(syncableUUID)
        return true
    }

    @Synchronized
    fun getAll(): List<Pair<SubscriberUUID, SyncableContainer>> {
        return store.values.toList()
    }

    fun removeSubscriber(id: SubscriberUUID): Boolean {
        logger.info { "Removing subscriber $id" }
        //remove all containers for subscriber
        store.filter { it.value.first == id }.forEach { removeContainer(it.value.second.uuid) }
        return true
    }
}
