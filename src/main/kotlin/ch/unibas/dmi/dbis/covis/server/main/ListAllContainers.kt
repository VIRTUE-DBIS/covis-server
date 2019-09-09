package ch.unibas.dmi.dbis.covis.server.main

import ch.unibas.dmi.dbis.covis.server.rpc.CovisServer
import ch.unibas.dmi.dbis.covis.server.rpc.client.CovisClient

object ListAllContainers {
    @JvmStatic
    fun main(args: Array<String>) {
        val client = CovisClient("localhost", 9734)
        client.getAll()
    }
}