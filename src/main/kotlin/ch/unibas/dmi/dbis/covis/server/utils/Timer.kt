package ch.unibas.dmi.dbis.covis.server.utils

import mu.KotlinLogging

/**
 * @author silvan on 26.10.17.
 */
private val logger = KotlinLogging.logger { }

/**
 * @param name Will be formatted [name] took n ms. Examples include: 'Loading Database', 'Eating Pasta'
 */
fun <R> time(name: String, block: () -> R): R {
    val start = System.currentTimeMillis()
    val res = block.invoke()
    val stop = System.currentTimeMillis()
    logger.debug("$name took ${stop - start} ms")
    return res
}