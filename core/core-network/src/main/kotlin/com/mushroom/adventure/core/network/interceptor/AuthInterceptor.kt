package com.mushroom.adventure.core.network.interceptor

import com.mushroom.adventure.core.network.token.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // /auth/* 路径不需要 token
        if (path.startsWith("/auth/")) {
            return chain.proceed(request)
        }

        val token = tokenStore.getAccessToken()
        return if (token != null) {
            val newRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(request)
        }
    }
}
