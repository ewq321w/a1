package com.example.m.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Singleton
    @Provides
    @Named("NewPipe")
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        val cacheSize = 10L * 1024 * 1024 // 10 MB
        val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)

        return OkHttpClient.Builder()
            .cache(cache)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            // Force DNS resolution on every request to respect network changes
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return try {
                        InetAddress.getAllByName(hostname).toList()
                    } catch (e: UnknownHostException) {
                        emptyList()
                    }
                }
            })
            .addInterceptor { chain ->
                val request = chain.request()
                var response = chain.proceed(request)
                var tryCount = 0
                while (!response.isSuccessful && tryCount < 3) {
                    tryCount++
                    response.close()
                    response = chain.proceed(request)
                }
                response
            }
            .addInterceptor(Interceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()
                chain.proceed(requestWithUserAgent)
            })
            .build()
    }

    @Singleton
    @Provides
    @Named("NewPipe")
    fun provideDownloader(@Named("NewPipe") okHttpClient: OkHttpClient): Downloader {
        return object : Downloader() {
            @Throws(IOException::class)
            override fun execute(request: Request): Response {
                val okHttpRequestBuilder = okhttp3.Request.Builder()
                    .url(request.url())
                    .method(request.httpMethod(), request.dataToSend()?.toRequestBody())

                for ((headerName, headerValueList) in request.headers()) {
                    for (headerValue in headerValueList) {
                        okHttpRequestBuilder.addHeader(headerName, headerValue)
                    }
                }

                val okHttpResponse = okHttpClient.newCall(okHttpRequestBuilder.build()).execute()
                val responseBody = okHttpResponse.body?.string()
                val finalUrl = okHttpResponse.request.url.toString()

                return Response(
                    okHttpResponse.code,
                    okHttpResponse.message,
                    okHttpResponse.headers.toMultimap(),
                    responseBody,
                    finalUrl
                )
            }
        }
    }
}