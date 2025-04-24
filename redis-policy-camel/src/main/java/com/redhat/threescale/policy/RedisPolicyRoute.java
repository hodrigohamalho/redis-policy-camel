package com.redhat.threescale.policy;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

public class RedisPolicyRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // REST endpoints administrativos
        restConfiguration()
            .component("netty-http")
            .port(8080)
            .bindingMode(RestBindingMode.auto);

        rest("/admin")
            .get("/keys")
                .to("direct:list-keys")
            .get("/keys/{key}")
                .to("direct:get-key");

        // Proxy principal
        from("netty-http:http://0.0.0.0:8080/proxy")
            .routeId("CamelProxyRedisPolicy")
            .log("Interceptando requisição para ${header.CamelHttpUri}")
            .setHeader("Command", constant("SET"))
            .setHeader("Key", simple("user-ip:${header.someParam}"))
            .setHeader("Value", header("X-User-IP"))
            .to("redis://{{redis.host}}:{{redis.port}}?password={{redis.password}}")
            .log("IP salvo no Redis: ${header.X-User-IP}")
            .toD("http4://backend-service:8080${header.CamelHttpUri}?bridgeEndpoint=true&throwExceptionOnFailure=false");

        // Admin: Listar todas as chaves
        from("direct:list-keys")
            .routeId("ListRedisKeys")
            .setHeader("Command", constant("KEYS"))
            .setHeader("Key", constant("user-ip:*"))
            .to("redis://{{redis.host}}:{{redis.port}}?password={{redis.password}}")
            .log("Chaves encontradas: ${body}");

        // Admin: Obter valor de uma chave
        from("direct:get-key")
            .routeId("GetRedisKey")
            .setHeader("Command", constant("GET"))
            .setHeader("Key", simple("user-ip:${header.key}"))
            .to("redis://{{redis.host}}:{{redis.port}}?password={{redis.password}}")
            .choice()
                .when(body().isNull())
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                    .setBody(constant("Chave não encontrada."))
                .otherwise()
                    .setBody(simple("Valor: ${body}"));
    }
}
