package org.OrderService;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import java.io.IOException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLOutput;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

public class Main {

    static String iscsIp = "";
    static String orderIp = "";
    static int iscsPort = 0;
    static int orderPort = 0;

    static HttpClient httpClient;
    static String iscsUrl = "";

    public static void main(String[] args) throws ClassNotFoundException, IOException {
        // Read the config.json file
        String content = new String(Files.readAllBytes(Paths.get("config.json")));
        JSONObject config = new JSONObject(content);
        JSONObject orderServiceConfig = config.getJSONObject("OrderService");
        JSONObject iscsServiceConfig = config.getJSONObject("InterServiceCommunication");
        orderIp = orderServiceConfig.getString("ip");
        orderPort = orderServiceConfig.getInt("port");
        iscsIp = iscsServiceConfig.getString("ip");
        iscsPort = iscsServiceConfig.getInt("port");

        iscsUrl = "http://" + iscsIp + ":" + iscsPort + "/";

        //SETTING UP SERVER
        HttpServer server = HttpServer.create(new InetSocketAddress(orderIp, orderPort), 0);
        // thread pool
        server.setExecutor(Executors.newFixedThreadPool(50));
        // set context for /order
        server.createContext("/order", new OrderHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Server started on IP " + orderIp + " and port " + orderPort);

    }

    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // handling placing orders
            if ("POST".equals(exchange.getRequestMethod())) {

                JSONObject jsonObject = getObject(exchange);
                System.out.println(jsonObject);

                //String params = String.valueOf(jsonObject.getInt("user_id")) + "/" + String.valueOf(jsonObject.getInt("product_id")) + "/" + String.valueOf(jsonObject.getInt("qty"));
                String userIscsUrl = iscsUrl + "user/get/" + jsonObject.getInt("user_id");
                String productIscsUrl = iscsUrl + "product/" + jsonObject.getInt("product_id") + "/" + jsonObject.getInt("qty");

                HttpResponse userResponse = handleGetRequest(userIscsUrl);
                HttpResponse prodResponse = handleGetRequest(productIscsUrl);

                // System.out.println(prodResponse.getStatusLine().getStatusCode());

                Boolean uR = (userResponse != null && userResponse.getStatusLine().getStatusCode() == 200);
                Boolean pR = (prodResponse != null && prodResponse.getStatusLine().getStatusCode() == 200);

                System.out.println(jsonObject.getInt("user_id"));

                System.out.println(uR);
                System.out.println(pR);

                if (uR && pR) {
                    JSONObject productUpdateObject = new JSONObject();
                    JSONObject userPurchaseObject = new JSONObject();

                    userPurchaseObject.put("command", "place");
                    userPurchaseObject.put("id", jsonObject.getInt("user_id"));
                    userPurchaseObject.put("product_id", jsonObject.getInt("product_id"));
                    userPurchaseObject.put("qty", jsonObject.getInt("qty"));

                    productUpdateObject.put("id", jsonObject.getInt("product_id"));
                    productUpdateObject.put("qty", jsonObject.getInt("qty"));
                    productUpdateObject.put("command", "update");

                    HttpResponse uResponse = handlePostRequest(iscsUrl + "/user", userPurchaseObject);
                    System.out.println("ORDER CREATION:" + uResponse);
                    assert uResponse != null;
                    String uResponseContent = EntityUtils.toString(uResponse.getEntity());

                    HttpResponse productResponse = handlePostRequest(iscsUrl + "/product", productUpdateObject);
                    int statusPCode = productResponse.getStatusLine().getStatusCode();
                    System.out.println("ORDER CREATION:" + productResponse);

                    assert productResponse != null;
                    String productResponseContent = EntityUtils.toString(productResponse.getEntity());

                    // Send the productResponse content back
                    exchange.sendResponseHeaders(200, uResponseContent.getBytes(StandardCharsets.UTF_8).length);
                    exchange.sendResponseHeaders(200, productResponseContent.getBytes(StandardCharsets.UTF_8).length);

                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(uResponseContent.getBytes(StandardCharsets.UTF_8));
                        os.write(productResponseContent.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    //check to prodResponse and userResponse for accurate response back
                    String response = "Error in finding data";
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            else if ("GET".equals(exchange.getRequestMethod())) {
                orderServiceShutdown(exchange);
            } else {
                // Send a 405 Method Not Allowed response for non-POST requests
                exchange.sendResponseHeaders(405, -1); // -1 indicates no response body
                exchange.close();
            }
        }

        private static void orderServiceShutdown(HttpExchange exchange) throws IOException {
            System.out.println("IN SHUTDOWN");
            System.out.println("Shutdown command received. Initiating shutdown...");

            // Send a response before initiating the shutdown
            String responseText = "OrderService has gracefully shutdown";
            exchange.sendResponseHeaders(200, responseText.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseText.getBytes());
            os.close();

            // Schedule the shutdown to allow time for the response to be sent
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Successfully shutdown OrderService");
                    System.exit(0);
                }
            }, 5000); // Delay for 5 seconds
        }

        private static JSONArray getArray(HttpExchange exchange) throws IOException {
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            isr.close();
            br.close();

            // Parse the request body as a JSON array
            return new JSONArray(requestBody.toString());
        }

        private static JSONObject getObject(HttpExchange exchange) throws IOException {
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            isr.close();
            br.close();

            // Parse the request body as a JSON object
            return new JSONObject(requestBody.toString());
        }
        private HttpResponse handleGetRequest(String url) throws IOException {
            //SETTING UP HTTP CLIENT
            httpClient = HttpClients.createDefault();
            HttpGet getRequest = new HttpGet(url);
            try {
                return httpClient.execute(getRequest);
            } catch (IOException e) {
                e.printStackTrace();
                // Handle exception
                return null;
            }
        }
        private HttpResponse handlePostRequest(String url, JSONObject json) throws IOException {
            HttpPost postRequest = new HttpPost(url);
            StringEntity entity = new StringEntity(json.toString());
            postRequest.setEntity(entity);
            postRequest.setHeader("Content-type", "application/json");
            try {
                return httpClient.execute(postRequest);
            } catch (IOException e) {
                e.printStackTrace();
                // Handle exception
                return null;
            }
        }


    }
}
