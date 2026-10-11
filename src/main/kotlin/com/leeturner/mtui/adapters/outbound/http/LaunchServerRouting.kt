package com.leeturner.mtui.adapters.outbound.http

import com.leeturner.mtui.domain.core.model.LaunchServer
import io.micronaut.context.annotation.Value
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.uri.UriBuilder
import java.net.URI
import java.util.function.Supplier

data class ChosenLaunchServer(
    val server: LaunchServer,
) : PropagatedContextElement

// Calls made inside go to the given server
fun <T : Any> onServer(
    server: LaunchServer,
    call: () -> T,
): T =
    PropagatedContext
        .getOrEmpty()
        .plus(ChosenLaunchServer(server))
        .propagate(Supplier(call))

// The generated client only knows the release server's URL, so requests made onServer(SNAPSHOT) are redirected
@ClientFilter(serviceId = ["micronaut-launch"])
class LaunchServerFilter(
    @param:Value("\${mtui.launch.snapshot-url}") private val snapshotUrl: URI,
) {
    @RequestFilter
    fun pointAtChosenServer(request: MutableHttpRequest<*>) {
        val chosen =
            PropagatedContext
                .getOrEmpty()
                .find(ChosenLaunchServer::class.java)
                .orElse(null)
                ?.server
        if (chosen != LaunchServer.SNAPSHOT) return
        request.uri(
            UriBuilder
                .of(request.uri)
                .scheme(snapshotUrl.scheme)
                .host(snapshotUrl.host)
                .port(snapshotUrl.port)
                .build(),
        )
    }
}
