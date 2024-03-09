package OrderService;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
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
import java.util.UUID;
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
                try {
                    JSONObject jsonObject = getObject(exchange);
                    System.out.println(jsonObject);

                    //String params = String.valueOf(jsonObject.getInt("user_id")) + "/" + String.valueOf(jsonObject.getInt("product_id")) + "/" + String.valueOf(jsonObject.getInt("qty"));
                    String userIscsUrl = iscsUrl + "user/get/" + jsonObject.getInt("user_id");
                    String productIscsUrl = iscsUrl + "product/" + jsonObject.getInt("product_id") + "/" + jsonObject.getInt("qty");

                    HttpResponse userResponse = handleGetRequest(userIscsUrl);
                    HttpResponse prodResponse = handleGetRequest(productIscsUrl);

                    Boolean uR = (userResponse != null && userResponse.getStatusLine().getStatusCode() == 200);
                    Boolean pR = (prodResponse != null && prodResponse.getStatusLine().getStatusCode() == 200);

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
                        assert uResponse != null && uResponse.getStatusLine().getStatusCode() == 200;
                        int statusUCode = uResponse.getStatusLine().getStatusCode();
                        String uResponseContent = EntityUtils.toString(uResponse.getEntity());

                        HttpResponse productResponse = handlePostRequest(iscsUrl + "/product", productUpdateObject);
                        assert productResponse != null && productResponse.getStatusLine().getStatusCode() == 200;
                        int statusPCode = productResponse.getStatusLine().getStatusCode();
                        String productResponseContent = EntityUtils.toString(productResponse.getEntity());

                        System.out.println(statusUCode);
                        System.out.println(statusPCode);

                        if(statusUCode == 200 && statusPCode == 200){
                            jsonObject.put("id", UUID.randomUUID());
                            jsonObject.put("status", "Success");
                            sendJsonResponse(exchange, jsonObject, 200);
                        } else {
                            jsonObject.put("id", UUID.randomUUID());
                            jsonObject.put("status", "Invalid Request");
                            sendJsonResponse(exchange, jsonObject, 404);
                        }
                    } else if (uR && !pR){
                        jsonObject.put("id", UUID.randomUUID());
                        jsonObject.put("status", "Exceeded qty limit");
                        sendJsonResponse(exchange, jsonObject, 400);
                    } else {
                        jsonObject.put("id", UUID.randomUUID());
                        jsonObject.put("status", "Invalid Request");
                        sendJsonResponse(exchange, jsonObject, 404);
                    }
                } catch (NoHttpResponseException e){
                    exchange.sendResponseHeaders(400, -1);
                }
            } else if ("GET".equals(exchange.getRequestMethod())) {
                orderServiceShutdown(exchange);
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
            exchange.close();
        }

        private static void orderServiceShutdown(HttpExchange exchange) throws IOException {
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
                return null;
            }
        }

        private static void sendJsonResponse(HttpExchange exchange, JSONObject jsonResponse, int statusCode) throws IOException {
            String responseString = jsonResponse.toString();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            System.out.println(statusCode + " " + responseString);
            exchange.sendResponseHeaders(statusCode, responseString.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseString.getBytes(StandardCharsets.UTF_8));
            }
        }

    }
}//End of Class

