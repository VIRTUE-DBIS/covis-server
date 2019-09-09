package ch.unibas.dmi.dbis.covis.server.data

import ch.unibas.dmi.dbis.covis.grpc.CovisGrpc.*
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.lang3.RandomUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SyncableContainerStoreTest {

    private val defaultSubscriberUUID = UUID.randomUUID().toString()

    @BeforeEach
    fun resetStore() {
        SyncableContainerStore.store.clear()
        SyncableContainerStore.index.clear()
    }

    @Test
    fun insertOneAndRetrieve() {
        val container = generateSyncableContainer()
        insert(container)
        assertEquals(container, getStored())
    }

    fun insert(syncable: Syncable, subscriberUUID: String = defaultSubscriberUUID): Boolean {
        return SyncableContainerStore.process(generateUpdateMessage(syncable), subscriberUUID)
    }

    fun insert(container: SyncableContainer, subscriberUUID: String = defaultSubscriberUUID): Boolean {
        return SyncableContainerStore.process(generateUpdateMessage(container), subscriberUUID)
    }

    private fun getStored(): SyncableContainer {
        return SyncableContainerStore.getAll().first().second
    }

    @Test
    fun insertSyncableWithoutContainer() {
        val syncable = generateSyncable()
        assertFalse(insert(syncable))
    }

    @Test
    fun updateOneContainerAndRetrieve() {
        val container = generateSyncableContainer()
        insert(container)
        val new = updateSyncableContainer(container)
        insert(new)
        testContainerMerge(container, new, getStored())
    }

    @Test
    fun addSyncableToContainerAndRetrieve() {
        val container = generateSyncableContainer()
        insert(container)
        val newSyncable = generateSyncable()
        val newBuilder = SyncableContainer.newBuilder().mergeFrom(container)
        newBuilder.putSyncables(newSyncable.uuid, newSyncable)
        val new = newBuilder.build()
        insert(new)
        testContainerMerge(container, new, getStored())
    }

    @Test
    fun updateOneSyncableAndRetrieve() {
        val container = generateSyncableContainer()
        insert(container)
        val old = container.syncablesMap.values.first()
        val new = updateSyncable(old)
        insert(new)
        testSyncableMerge(old, new, getStored().syncablesMap[new.uuid]!!)
    }

    @Test
    fun multipleSyncableUpdates() {
        repeat((0 until 100).count()) {
            updateOneSyncableAndRetrieve()
            resetStore()
        }
    }

    @Test
    fun multipleContainerUpdates() {
        repeat((0 until 100).count()) {
            updateOneContainerAndRetrieve()
            resetStore()
        }
    }

    private fun testContainerMerge(old: SyncableContainer, new: SyncableContainer, stored: SyncableContainer) {
        assertEquals(new.uuid, stored.uuid)
        assertEquals(old.uuid, stored.uuid)
        assertEquals(old.type, stored.type)
        assertEquals(old.model, stored.model)
        if (new.name == null || new.name.isEmpty()) assertEquals(old.name, stored.name) else assertEquals(new.name, stored.name)
        old.syncablesMap.forEach { uuid, syncable ->
            if (new.syncablesMap.containsKey(uuid)) testSyncableMerge(syncable, new.syncablesMap[uuid]!!, stored.syncablesMap[uuid]!!) else assertEquals(syncable, stored.syncablesMap[uuid])
        }
    }

    private fun testSyncableMerge(old: Syncable, new: Syncable, stored: Syncable) {
        assertEquals(old.uuid, new.uuid)
        assertEquals(old.uuid, stored.uuid)
        if (new.hasPosition()) assertEquals(new.position, stored.position, "new position does not equal the stored one") else assertEquals(old.position, stored.position, "old position does not equal the stored one")
        if (new.hasRotation()) assertEquals(new.rotation, stored.rotation, "new rotation does not equal the stored one") else assertEquals(old.rotation, stored.rotation, "old rotation does not equal the stored one")
    }

    fun generateUpdateMessage(syncable: Syncable): UpdateMessage {
        return UpdateMessage.newBuilder().setSyncable(syncable).setTimestamp(System.currentTimeMillis()).build()
    }

    fun generateUpdateMessage(container: SyncableContainer): UpdateMessage {
        return UpdateMessage.newBuilder().setContainer(container).setTimestamp(System.currentTimeMillis()).build()
    }

    fun generateSyncableContainer(): SyncableContainer {
        return SyncableContainer.newBuilder()
                .setModel(RandomStringUtils.random(5))
                .setName(RandomStringUtils.random(10))
                .setUuid(UUID.randomUUID().toString())
                .setType(SyncableContainerType.forNumber(RandomUtils.nextInt(1, 4)))
                .putAllSyncables((0 until RandomUtils.nextInt(1, 5)).map { generateSyncable() }.associateBy { it.uuid })
                .build()
    }

    fun updateSyncableContainer(container: SyncableContainer): SyncableContainer {
        val builder = SyncableContainer.newBuilder()
        if (RandomUtils.nextDouble(0.0, 1.0) > 0.5) builder.setName(RandomStringUtils.random(10))
        builder.setUuid(container.uuid)
        container.syncablesMap.forEach {
            if (RandomUtils.nextDouble(0.0, 1.0) > 0.5) {
                val _syncable = updateSyncable(it.value)
                builder.putSyncables(_syncable.uuid, _syncable)
            }
        }
        return builder.build()
    }

    fun updateSyncable(syncable: Syncable): Syncable {
        val builder = Syncable.newBuilder()
        builder.setUuid(syncable.uuid)
        if (RandomUtils.nextDouble(0.0, 1.0) > 0.5) builder.setPosition(generateRandomVec3())
        if (RandomUtils.nextDouble(0.0, 1.0) > 0.5) builder.setRotation(generateRandomVec4())
        return builder.build()
    }

    fun generateSyncable(): Syncable {
        return Syncable.newBuilder().setUuid(UUID.randomUUID().toString()).setPosition(generateRandomVec3()).setRotation(generateRandomVec4()).build()
    }

    fun generateRandomVec3(): Vec3 {
        return Vec3.newBuilder().setX(RandomUtils.nextDouble()).setY(RandomUtils.nextDouble()).setZ(RandomUtils.nextDouble()).build()
    }

    fun generateRandomVec4(): Vec4 {
        return Vec4.newBuilder().setX(RandomUtils.nextDouble()).setY(RandomUtils.nextDouble()).setZ(RandomUtils.nextDouble()).setW(RandomUtils.nextDouble()).build()
    }
}
