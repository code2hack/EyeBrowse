package com.code2hack.eyebrowse.chromiumfixture

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Existing fixed pages plus the original nonce-receipt protocol, for the reserved #57 window. */
fun main(args: Array<String>) {
    require(args.size == 4) { "bind port pages-directory evidence-directory" }
    val address = InetAddress.getByName(args[0])
    require(address.hostAddress == args[0] && NetworkInterface.getByInetAddress(address) != null)
    val octets = address.address.map { it.toInt() and 255 }
    require(address.isLoopbackAddress || address.isSiteLocalAddress ||
        (octets.size == 4 && octets[0] == 100 && octets[1] in 64..127))
    val port = args[1].toInt().also { require(it in 1024..65535) }
    val directory = Path.of(args[2]).toRealPath()
    val evidence = Path.of(args[3]).toRealPath()
    val routes = listOf("keyboard.html", "local-keyboard.html", "history.html", "author-light.html", "media.svg")
        .associate { name ->
            val file = directory.resolve(name).toRealPath()
            require(file.parent == directory)
            "/$name" to Files.readAllBytes(file).also { require(it.size <= 128 * 1024) }
        }.let { it + ("/loading.html" to it.getValue("/author-light.html")) }
    val loads = AtomicInteger()
    val submissions = AtomicInteger()
    val executor = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(16))
    val server = HttpServer.create(InetSocketAddress(address, port), 4)
    val journalLock = Any()
    server.createContext("/") { exchange ->
        exchange.use {
            val route = exchange.requestURI.path
            val receipt = exchange.requestMethod == "GET" && Regex("/route-probe/[a-zA-Z0-9-]{8,100}").matches(route)
            if (exchange.requestMethod == "GET" && route == "/loading.html") Thread.sleep(10_000)
            if (receipt) Thread.sleep(2_000) // Existing routing test observes the live DIRECT connection.
            val bytes = when {
                receipt -> ("{\"nonce\":\"${route.substringAfterLast('/')}\",\"method\":\"GET\",\"path\":\"$route\"," +
                    "\"peer\":\"${exchange.remoteAddress.address.hostAddress}\",\"receivedAt\":\"${Instant.now()}\"}").toByteArray()
                exchange.requestMethod == "GET" && route in routes -> { loads.incrementAndGet(); routes.getValue(route) }
                exchange.requestMethod == "GET" && route == "/api/observations" ->
                    "{\"loads\":${loads.get()},\"submissions\":${submissions.get()}}".toByteArray()
                exchange.requestMethod == "GET" && route == "/submit" -> {
                    submissions.incrementAndGet(); "<!doctype html><title>Submitted</title>Harmless fixture submission".toByteArray()
                }
                else -> "Unknown fixture route".toByteArray()
            }
            val known = exchange.requestMethod == "GET" && (receipt || route in routes || route in listOf("/api/observations", "/submit"))
            exchange.responseHeaders.add("Content-Type", when {
                receipt || route == "/api/observations" -> "application/json"
                route == "/media.svg" -> "image/svg+xml"
                else -> "text/html; charset=utf-8"
            })
            exchange.responseHeaders.add("Cache-Control", "no-store")
            exchange.sendResponseHeaders(if (known) 200 else 404, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            if (receipt) synchronized(journalLock) {
                Files.writeString(evidence.resolve("fixture-receipts.jsonl"), bytes.toString(Charsets.UTF_8) + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
            }
            println("FIXTURE peer=${exchange.remoteAddress.address.hostAddress} route=${if (known) route else "other"} loads=${loads.get()} submits=${submissions.get()}")
        }
    }
    server.executor = executor
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(0); executor.shutdownNow() })
    server.start()
    println("READY bind=${address.hostAddress} port=$port pid=${ProcessHandle.current().pid()}")
}
