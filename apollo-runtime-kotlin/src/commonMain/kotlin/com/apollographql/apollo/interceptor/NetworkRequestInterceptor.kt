package com.apollographql.apollo.interceptor

import com.apollographql.apollo.ApolloParseException
import com.apollographql.apollo.ApolloSerializationException
import com.apollographql.apollo.api.ApolloExperimental
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.network.GraphQLRequest
import com.apollographql.apollo.network.GraphQLResponse
import com.apollographql.apollo.network.NetworkTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@ApolloExperimental
@ExperimentalCoroutinesApi
class NetworkRequestInterceptor(
    private val networkTransport: NetworkTransport
) : ApolloRequestInterceptor {

  override fun <T> intercept(request: ApolloRequest<T>, interceptorChain: ApolloInterceptorChain): Flow<Response<T>> {
    return flow { emit(request.toNetworkRequest()) }
        .flatMapLatest { networkRequest -> networkTransport.execute(request = networkRequest, executionContext = request.executionContext) }
        .map { networkResponse -> networkResponse.parse(request) }
  }

  private fun <T> GraphQLResponse.parse(request: ApolloRequest<T>): Response<T> {
    val response = try {
      request.operation.parse(
          source = body,
          scalarTypeAdapters = request.scalarTypeAdapters
      )
    } catch (e: Exception) {
      throw ApolloParseException(
          message = "Failed to parse GraphQL network response",
          cause = e
      )
    } finally {
      body.close()
    }
    return response.copy(executionContext = request.executionContext + executionContext)
  }

  private fun ApolloRequest<*>.toNetworkRequest(): GraphQLRequest {
    return try {
      GraphQLRequest(
          operationName = operation.name().name(),
          operationId = operation.operationId(),
          document = operation.queryDocument(),
          variables = operation.variables().marshal(scalarTypeAdapters)
      )
    } catch (e: Exception) {
      throw ApolloSerializationException(
          message = "Failed to compose GraphQL network request",
          cause = e
      )
    }
  }
}
