package com.example.kuromoji_api_docker;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.util.List;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.atilika.kuromoji.unidic.neologd.Token;
import com.atilika.kuromoji.unidic.neologd.Tokenizer;
import com.fasterxml.jackson.core.JsonProcessingException;

public class App 
{
    private static class PostRequestBody {
      public String body;
      public String mode;
    }

    private static ObjectMapper mapper = new ObjectMapper();

    protected static String convert(Tokenizer tokenizer, String body) throws JsonProcessingException
    {
        List<Token> tokens = tokenizer.tokenize(body);

        ArrayNode tokenArrayNode = mapper.createArrayNode();
        for (Token token : tokens) {
            ArrayNode featuresNode = mapper.createArrayNode();
            for (String featureString : token.getAllFeaturesArray()) {
                featuresNode.add(featureString);
            }

            ObjectNode tokenNode = mapper.createObjectNode();
            tokenNode.put("surface", token.getSurface());
            tokenNode.put("position", token.getPosition());
            tokenNode.put("isKnown", token.isKnown());
            tokenNode.set("features", featuresNode);

            tokenArrayNode.add(tokenNode);
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("tokens", tokenArrayNode);

        return mapper.writeValueAsString(result);
    }

    public static void main( String[] args )
    {
        String portString = System.getenv("PORT");
        int port;
        try {
            port = Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            port = 9696;
        }

        System.out.println("Starting server at 0.0.0.0:" + port);
        Undertow server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        exchange.getRequestReceiver().receiveFullBytes(
                            (fullByteExchange, data) -> {
                                try {
                                    PostRequestBody requestBody = mapper.readValue(new String(data), PostRequestBody.class);
                                    String outputJson = convert(new Tokenizer(), requestBody.body);
                                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                                    exchange.getResponseSender().send(outputJson);
                                } catch (IOException e) {
                                    exchange.setStatusCode(400);
                                    exchange.getResponseSender().send("Invalid Request");
                                    exchange.endExchange();
                                }
                            }
                        );
                    }
                }).build();
        server.start();
    }
}
