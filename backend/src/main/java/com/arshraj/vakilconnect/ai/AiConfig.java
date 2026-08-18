package com.arshraj.vakilconnect.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The one piece of wiring the Ollama adapter cannot do for itself: an HTTP
 * client with finite timeouts.
 *
 * WHY THIS CLASS EXISTS AT ALL, when ResendEmailSender needs no equivalent.
 * ResendEmailSender takes a {@code RestClient.Builder} and calls {@code build()}
 * in its own constructor, which is fine because it never touches the request
 * factory. Timeouts have to be set ON the factory, and an adapter that did
 * {@code builder.requestFactory(...)} in its constructor would OVERWRITE the
 * mock factory that {@code MockRestServiceServer.bindTo(builder)} installed -
 * the bind happens first, the constructor runs second, last write wins. The
 * result would be a "unit" test making real calls to a local Ollama server if
 * one happened to be running, and failing on machines where one was not.
 * Building the client here, and injecting the finished {@code RestClient},
 * removes that ordering hazard entirely.
 *
 * WHY A RestClient BEAN AND NOT A SECOND RestClient.Builder BEAN. Boot's
 * auto-configured {@code restClientBuilder} is {@code @ConditionalOnMissingBean}.
 * Declaring another builder would make the auto-configuration back off, and
 * ResendEmailSender - which injects {@code RestClient.Builder} unqualified -
 * would silently receive the AI one, complete with AI timeouts. Declaring a
 * {@code RestClient} instead is a different type, so nothing existing changes.
 *
 * WHY TIMEOUTS ARE NOT OPTIONAL. RestClient's default request factory has NO
 * read timeout. A model that starts generating and then stalls would hold the
 * calling thread until the process restarts.
 *
 * Gated on the same property as {@link OllamaLlmClient}, so a stub context does
 * not build an HTTP client it will never use - which is also the strongest
 * available proof that the test suite cannot reach any inference server.
 */
@Configuration
@ConditionalOnProperty(name = "vakilconnect.ai.provider",
        havingValue = AiProperties.OLLAMA)
public class AiConfig {

    /**
     * NO baseUrl IS SET on the client, deliberately. {@link OllamaLlmClient}
     * composes the full absolute URL from {@code vakilconnect.ai.base-url},
     * which keeps the endpoint assertable in a test that binds
     * MockRestServiceServer to a bare {@code RestClient.builder()}.
     *
     * The read timeout here is the one that matters and is deliberately
     * generous - see {@link AiProperties#readTimeout()} for why local inference
     * needs far longer than a hosted API would.
     */
    @Bean
    public RestClient ollamaRestClient(RestClient.Builder builder, AiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());

        return builder.requestFactory(factory).build();
    }
}
