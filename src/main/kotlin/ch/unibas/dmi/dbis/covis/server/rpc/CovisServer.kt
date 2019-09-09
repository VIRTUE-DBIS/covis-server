package ch.unibas.dmi.dbis.covis.server.rpc

import io.grpc.ServerBuilder
import mu.KotlinLogging

class CovisServer(private val port: Int) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val server = ServerBuilder.forPort(port).addService(CovisServiceImpl()).build()

    /**
     * Start the service.
     */
    fun start() {
        server.start()
        logger.info("Server started, listening on $port")
        Runtime.getRuntime().addShutdownHook(Thread {
            // Use stderr here since the logger may has been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down")
            this@CovisServer.stop()
            System.err.println("*** server shut down")
        })
    }


    /**
     * Stop serving requests and shutdown resources.
     */
    fun stop() {
        server?.shutdown()
    }


    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    fun blockUntilShutdown() {
        server?.awaitTermination()
    }

}