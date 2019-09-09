package ch.unibas.dmi.dbis.covis.server.main

import ch.unibas.dmi.dbis.covis.server.rpc.CovisServer

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val server = CovisServer(9734)

        server.start()
        server.blockUntilShutdown()
    }
}