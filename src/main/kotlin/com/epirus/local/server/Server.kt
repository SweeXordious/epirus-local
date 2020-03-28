/*
 * Copyright 2020 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.epirus.local.server

import com.epirus.local.ledger.LocalLedger
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.request.receive
import io.ktor.response.respondText
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI

@KtorExperimentalAPI
fun Application.nettyServer() {

    val arguments = parseArguments(environment.config
            .propertyOrNull("sun.java.command")?.getString())

    val directory = arguments["directory"] ?: "."
    val port = arguments["port"] ?: "8080"
    val host = arguments["host"] ?: "0.0.0.0"

    val accounts = generateAccounts()
    val genesis = createGenesis(directory, accounts)
    val localLedger = LocalLedger(accounts, genesis)
    val requestHandler = RequestHandler(localLedger)

    println("""-> Starting client with generated genesis file: $genesis
            -> chainID = 1
            -> Port = $port
            -> Host = $host""".trimIndent())
    accounts.stream().forEach { t -> println("[*] ${t.address} : 100 eth\n\tPrivate key: ${t.privateKey}") }

    install(DefaultHeaders)
    install(CallLogging)
    routing {
        post("/") {
            try {
                val jsonRequest: String = call.receive()
                val jsonResponse: String = requestHandler.processRequest(jsonRequest)
                call.respondText(jsonResponse, ContentType.Text.Plain)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText(
                        "Incorrect request or problem when executing it! Check https://github.com/ethereum/wiki/wiki/JSON-RPC\n",
                        ContentType.Text.Plain
                )
            }
        }
    }
}

fun parseArguments(command: String?): HashMap<String, String?> {

    val splitCommand = command?.split(" ")
    val arguments = HashMap<String, String?>()

    splitCommand?.stream()?.forEach { s ->
        run {
            if (s.startsWith("-p") || s.startsWith("--port")) arguments["port"] = s.split("=").getOrNull(1)
            else if (s.startsWith("-d") || s.startsWith("--directory")) arguments["directory"] = s.split("=").getOrNull(1)
            else if (s.startsWith("-h") || s.startsWith("--host")) arguments["host"] = s.split("=").getOrNull(1)
        }
    }

    return arguments
}
