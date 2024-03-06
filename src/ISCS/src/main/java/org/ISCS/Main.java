package org.ISCS;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

import org.apache.http.conn.HttpHostConnectException;

import java.util.Timer;
import java.util.TimerTask;

public class Main {
    static String userIp = "";
    static String productIp = "";
    static String iscsIp = "";
    static int userPort = 0;
    static int iscsPort = 0;
    static int productPort = 0;
    static HttpClient httpClient = null;
    static HttpClient httpPostClient = null;
    static String productUrl = "";
    static String userUrl = "";

    public static void main(String[] args) throws IOException {

        //read the config.json file
        String content = new String(Files.readAllBytes(Paths.get("config.json")));
        JSONObject config = new JSONObject(content);
        JSONObject userServiceConfig = config.getJSONObject("UserService");
        JSONObject productServiceConfig = config.getJSONObject("ProductService");
        JSONObject iscsServiceConfig = config.getJSONObject("InterServiceCommunication");

        iscsIp = iscsServiceConfig.getString("ip");
        iscsPort = iscsServiceConfig.getInt("port");
        userIp = userServiceConfig.getString("ip");
        userPort = userServiceConfig.getInt("port");
        productIp = productServiceConfig.getString("ip");
        productPort = productServiceConfig.getInt("port");

        userUrl = "http://" + userIp + ":" + userPort + "/";
        productUrl = "http://" + productIp + ":" + productPort + "/";


        //SETTING UP SERVER
        HttpServer server = HttpServer.create(new InetSocketAddress(iscsIp, iscsPort), 0);
        // thread pool
        server.setExecutor(Executors.newFixedThreadPool(100));
        // set context for /order
        server.createContext("/user", new UserHandler());
        server.createContext("/product", new ProductHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Server started on IP " + iscsIp + " and port " + iscsPort);
    }


    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // POST request for /user
            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject jsonObject = getObject(exchange);

                String userPostUrl = userUrl + "user";

                HttpResponse postResponse = handlePostRequest(userPostUrl, jsonObject);
                // Send a response back
                sendValidResponse(exchange, postResponse);

            } else if ("GET".equals(exchange.getRequestMethod())) {
                System.out.println("U - HANDLEGET");
                String path = exchange.getRequestURI().getPath();
                System.out.println(path);
                String[] pathSegments = path.split("/");

                System.out.println(pathSegments[0]);
                System.out.println(pathSegments[1]);
                System.out.println(pathSegments[2]);

                // Assuming the path is in the format "/user/get/<id>"
                if (pathSegments.length > 3) {
                    String command = pathSegments[2];
                    String userId = pathSegments[3];

                    String userGetUrl = userUrl + "user/" + command + '/' + userId;

                    HttpResponse userServiceResponse = handleGetRequest(userGetUrl);
                    // Send a response back
                    sendValidResponse(exchange, userServiceResponse);

                    System.out.println(userServiceResponse);

                } else if(pathSegments.length == 3) { ///user/shutdown
                    String command = pathSegments[2]; // this would be "shutdown"

                    String userShutdownUrl = userUrl + "user/" + command;
                    System.out.println(userShutdownUrl);
                    HttpResponse userShutdownResponse = handleGetRequest(userShutdownUrl);
                    sendValidResponse(exchange, userShutdownResponse);

                } else {
                    // Send a 405 Method Not Allowed response for non-POST requests
                    exchange.sendResponseHeaders(405, 0);
                    exchange.close();
                }
            }
        }
    }

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // POST request for /product
            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject jsonObject = getObject(exchange);

                String productPostUrl = productUrl + "product";

                HttpResponse postResponse = handlePostRequest(productPostUrl, jsonObject);
                // Send a response back
                sendValidResponse(exchange, postResponse);

            }else if ("GET".equals(exchange.getRequestMethod())) {
                System.out.println("P - HANDLEGET");
                String path = exchange.getRequestURI().getPath();
                String[] pathSegments = path.split("/");

                if (pathSegments.length > 3) {
                    String productId = pathSegments[2];  // Get the product ID
                    String qty = pathSegments[3]; //Get the qty number

                    System.out.println(productId);
                    System.out.println(qty);

                    String productGetUrl = productUrl + "product/" + productId + "/" + qty;

                    HttpResponse productServiceResponse = handleGetRequest(productGetUrl);
                    // Send a response back
                    sendValidResponse(exchange, productServiceResponse);
                }
                else if(pathSegments.length == 3) { ///user/shutdown
                    String command = pathSegments[2]; // this would be "shutdown"

                    String productShutdownUrl = productUrl + "product/" + command;
                    System.out.println(productShutdownUrl);
                    HttpResponse productShutdownResponse = handleGetRequest(productShutdownUrl);
                    sendValidResponse(exchange, productShutdownResponse);

                    iscsShutdown();
                }
            }
            else {
                // Send a 405 Method Not Allowed response for non-POST requests
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
            }
        }
    }

    public static void iscsShutdown() throws IOException {

           System.out.println("IN SHUTDOWN");
           System.out.println("Shutdown command received. Initiating shutdown...");

           // Schedule the shutdown to allow time for the response to be sent
           new Timer().schedule(new TimerTask() {
               @Override
               public void run() {
                   System.out.println("Successfully shutdown ISCS");
                   System.exit(0);
               }
           }, 5000); // Delay for 5 seconds
    }

    static JSONObject getObject(HttpExchange exchange) throws IOException {
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

    private static HttpResponse handleGetRequest(String url) throws IOException {
        System.out.println("HANDLEGETREQUEST");
        httpClient = HttpClients.createDefault();
        HttpGet getRequest = new HttpGet(url);
        try {
            return httpClient.execute(getRequest);
        } catch (HttpHostConnectException e) {
            System.err.println("UserService has gracefully shutdown" + url);
            return null; // or construct a specific HttpResponse indicating the error
        } catch (IOException e) {
            e.printStackTrace();
            // Handle exception
            return null;
        }
    }
    private static HttpResponse handlePostRequest(String url, JSONObject json) throws IOException {
        httpClient = HttpClients.createDefault();
        HttpPost postRequest = new HttpPost(url);
        StringEntity entity = new StringEntity(json.toString());
        postRequest.setEntity(entity);
        postRequest.setHeader("Content-type", "application/json");
        try {
            HttpResponse response = httpClient.execute(postRequest);
            return response;
        } catch (IOException e) {
            e.printStackTrace();
            // Handle exception
            return null;
        }
    }

    private static void sendValidResponse(HttpExchange exchange, HttpResponse response) throws IOException {
        String responseString;
        try {
            responseString = EntityUtils.toString(response.getEntity());
        } catch (HttpHostConnectException e) {
            responseString = "Shutdown Intiated";
        } catch (IOException e) {
            responseString = "Error processing response: " + e.getMessage();
            // Handle exception or log as needed
        }
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("status code: " + statusCode + " body from user/product: " + responseString);
        exchange.sendResponseHeaders(statusCode, responseString.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseString.getBytes(StandardCharsets.UTF_8));
        }
    }

}
