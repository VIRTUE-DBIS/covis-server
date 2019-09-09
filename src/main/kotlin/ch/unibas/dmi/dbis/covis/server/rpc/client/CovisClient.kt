package ch.unibas.dmi.dbis.covis.server.rpc.client

import ch.unibas.dmi.dbis.covis.grpc.CovisServiceGrpc
import com.google.common.util.concurrent.SettableFuture
import com.google.protobuf.Empty
import io.grpc.netty.NegotiationType
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.StreamObserver
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.ExecutionException

class CovisClient(host: String, port: Int) : Closeable {

    private val channel = NettyChannelBuilder.forAddress(host, port).negotiationType(NegotiationType.PLAINTEXT).build()!!
    private val stub = CovisServiceGrpc.newStub(channel)

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private fun <T> getFuture(future: SettableFuture<T>): T {
        try {
            return future.get()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }
    }

    @Synchronized
    fun getAll() {
        val reply = this.stub.sync(LoggingStreamObserver())
        Thread.sleep(1_000)
        reply.onCompleted()
    }

    internal class LoggingStreamObserver<T> : StreamObserver<T> {
        override fun onError(t: Throwable?) {
            logger.error("error in stream", t)
        }

        override fun onCompleted() {
            logger.info { "Stream completed" }
        }

        override fun onNext(value: T) {
            logger.info { value }
        }
    }

    @Synchronized
    fun ping() {
        val future = SettableFuture.create<Empty>()
        this.stub.ping(Empty.newBuilder().build(), LastObserver(future))
        getFuture(future)
    }

    /**
     * Close connection to worker
     */
    override fun close() {
        this.channel.shutdown()
    }

    /**
     * This is needed for RPC-Calls. See [requestURL] for example usage.
     */
    internal inner class LastObserver<T>(private val future: SettableFuture<T>) : StreamObserver<T> {
        private var last: T? = null


        override fun onCompleted() {
            future.set(this.last)
        }


        override fun onError(e: Throwable) {
            future.setException(e)
        }


        override fun onNext(t: T) {
            this.last = t
        }
    }
}
