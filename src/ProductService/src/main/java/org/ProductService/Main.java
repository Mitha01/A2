package org.ProductService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.math.BigDecimal;

import java.util.Timer;
import java.util.TimerTask;


public class Main {

    public static Connection connection = null;


    public static void main(String[] args)  {
        // Setup server and database connection
        setupServer();
        setupDatabase();

    }
    private static void shutDown(){
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM Products"); // Adjust table name and query as necessary
                }
            } catch (Exception e) {
                System.err.println("Failed to clear users and purchases information: " + e.getMessage());
            }
        }));
    }

    //SETTING UP SERVER FOR PRODUCT SEVICE
    private static void setupServer() {
        try {

            String content = new String(Files.readAllBytes(Paths.get("config.json")));
            JSONObject config = new JSONObject(content);
            JSONObject userServiceConfig = config.getJSONObject("ProductService");
            String ip = userServiceConfig.getString("ip");
            int port = userServiceConfig.getInt("port");

            HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);

            server.setExecutor(Executors.newFixedThreadPool(20));

            // Set up context for /test POST request
            server.createContext("/product", new Handler());
            server.setExecutor(null); // creates a default executor
            server.start();
            System.out.println("Server started on IP " + ip + " and port " + port);

        } catch (IOException e) {
            System.out.println("Server setup error: " + e.getMessage());
        }
    }

    //SETTING UP DATABASE FOR USERSERVICE
    private static void setupDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:src/ProductService/src/database/Products.db");
            // Create the users table
            createProductTable();

        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
    }

    //Creates User Database
    private static void createProductTable() {
        String sql = "CREATE TABLE IF NOT EXISTS Products (\n"
                + " id INTEGER PRIMARY KEY UNIQUE,\n"
                + " name TEXT UNIQUE,\n"
                + " description TEXT,\n"
                + " price DECIMAL(10,2) ,\n"
                + " qty INTEGER \n"
                + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("Successfully created the product table");
        } catch (SQLException e) {
            System.out.println("Error creating table: " + e.getMessage());
        }
    }

    //Listens to server for incoming API requests
    private static class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();

                if ("POST".equals(method)) {
                    Map<String, Object> jsonMap = parseJsonStringToMap(getRequestBody(exchange));
                    handlePostRequest(exchange, jsonMap);

                } else if ("GET".equals(method)) {
                    handleGetRequest(exchange);

                } else {
                    exchange.sendResponseHeaders(500, -1);
                }
            } finally {
                exchange.close();
            }
        }
    }

    //Gets the Request Body of the API request
    private static String getRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            return requestBody.toString();
        }
    }

    //Gets the HashMap Object from the JsonString
    public static Map<String, Object> parseJsonStringToMap(String jsonString) {
        JSONObject jsonObject = new JSONObject(jsonString);
        return jsonObject.toMap();
    }

    //Checks to see if user is valid to Update/Delete
    private static boolean checkProductExists(Integer id) {
        String sqlString = "SELECT id FROM Products WHERE id = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sqlString);
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    //Handles Post Requests to API - delete, create and update
    private static void handlePostRequest(HttpExchange exchange, Map<String, Object> jsonMap) throws IOException  {
        try {
            String command = (String) jsonMap.getOrDefault("command", null);
            Integer id = (Integer) jsonMap.getOrDefault("id", null);
            String name = (String) jsonMap.getOrDefault("name", null);
            String description = (String) jsonMap.getOrDefault("description", null);
            BigDecimal price = (BigDecimal) jsonMap.getOrDefault("price", null);
            Integer qty = (Integer) jsonMap.getOrDefault("qty", null);

            switch (command) {
                case "create":
                    if (id != null && name != null && description != null && price != null && qty != null) {
                        if (checkProductExists(id)) {
                            System.out.println("400: Bad Request");
                            exchange.sendResponseHeaders(400, -1);
                            break;
                        }
                        else{
                            String sql = "INSERT INTO Products (id, name, description, price, qty) " +
                                    "VALUES (?, ?, ?, ?, ?)";

                            try(PreparedStatement pstmt = connection.prepareStatement(sql)){
                                pstmt.setInt(1, id);
                                pstmt.setString(2, name);
                                pstmt.setString(3, description);
                                pstmt.setBigDecimal(4, price);
                                pstmt.setInt(5, qty);

                                pstmt.executeUpdate();
                                sendValidResponse(exchange, id);


                            } catch (SQLException e) {
                                System.out.println("500: Internal Server Error");
                                exchange.sendResponseHeaders(500, -1);
                            }
                        }

                    }
                    else{
                        System.out.println("400: Bad Request");
                        exchange.sendResponseHeaders(400, -1);
                    }
                    break;
                case "update":
                    if(!checkProductExists(id)){
                        System.out.println("404: Not Found");
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    if (id != null) {
                        int currentQty = getCurrentProductQty(id);
                        Integer orderQty = 0;

                        if (currentQty < 0) {
                            System.out.println("400: Bad Request");
                            exchange.sendResponseHeaders(400, -1); // Product not found
                            return;
                        }

                        if (jsonMap.containsKey("qty")) {
                            orderQty = (Integer) jsonMap.get("qty");
                        }

                        if (orderQty != null && currentQty >= orderQty) {
                            orderQty = currentQty - orderQty;
                        }

                        StringBuilder sqlBuilder = new StringBuilder("UPDATE Products SET ");
                        List<Object> parameters = new ArrayList<>();

                        if (jsonMap.containsKey("name")) {
                            sqlBuilder.append("name = ?, ");
                            parameters.add(jsonMap.get("name"));
                        }
                        if (jsonMap.containsKey("description")) {
                            sqlBuilder.append("description = ?, ");
                            parameters.add(jsonMap.get("description"));
                        }
                        if (jsonMap.containsKey("price")) {
                            sqlBuilder.append("price = ?, ");
                            parameters.add(jsonMap.get("price"));
                        }
                        if (jsonMap.containsKey("qty")) {
                            sqlBuilder.append("qty = ?, ");
                            parameters.add(orderQty);
                        }

                        if (!parameters.isEmpty()) {
                            sqlBuilder.delete(sqlBuilder.length() - 2, sqlBuilder.length());
                        } else {
                            System.out.println("Bad Request");
                            exchange.sendResponseHeaders(400, -1);
                            return;
                        }

                        sqlBuilder.append(" WHERE id = ?");
                        parameters.add(id);

                        try(PreparedStatement pstmt = connection.prepareStatement(sqlBuilder.toString())){
                            for (int i = 0; i < parameters.size(); i++) {
                                pstmt.setObject(i + 1, parameters.get(i));
                            }

                            int affectedRows = pstmt.executeUpdate();
                            if (affectedRows > 0) {
                                sendValidResponse(exchange, id);
                            } else {
                                System.out.println("409: Conflict");
                                exchange.sendResponseHeaders(409, -1);
                            }
                        } catch (SQLException e) {
                            System.out.println("500: Internal Server Error");
                            exchange.sendResponseHeaders(500, -1);
                        }
                    }
                    else{
                        System.out.println("404: Not Found");
                        exchange.sendResponseHeaders(404, -1);
                    }
                    break;
                case "delete":
                    // Handles delete command
                    if (id != null && name != null && description != null && price != null && qty != null) {
                        if (checkProductExists(id)) {
                            String sql = "DELETE FROM Products WHERE id = ?";

                            try(PreparedStatement pstmt = connection.prepareStatement(sql)) {
                                pstmt.setInt(1, id); // Set the value of the first parameter (the user's ID)
                                pstmt.executeUpdate();
                                System.out.println("200: OK");
                                exchange.sendResponseHeaders(200, -1);

                            } catch (SQLException e) {

                                System.out.println("500: Internal Server Error");
                                exchange.sendResponseHeaders(500, -1);
                            }
                        }
                        else{
                            System.out.println("404: Not Found");
                            exchange.sendResponseHeaders(404, -1);
                        }
                    } else {
                        System.out.println("400: Bad Request");
                        exchange.sendResponseHeaders(400, -1);
                    }
                    break;
                default:
                    System.out.println("400: Bad Request");
                    exchange.sendResponseHeaders(400, -1);
                    break;
            }
        } catch (Exception e) {
            System.out.println("500: Internal Server Error");
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }
    }

    //Handles Get Requests to API - gets info
    private static void handleGetRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String [] pathSegments = path.split("/");

        if(pathSegments.length > 3){
            try {
                int id = Integer.parseInt(pathSegments[2]);
                int orderQty = Integer.parseInt(pathSegments[3]);
                int currentQty = 0;

                sendProductInfo(exchange, id, orderQty, currentQty);

            } catch (Exception e) {
                System.out.println("500: Internal Server Error");
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        } else if(pathSegments.length == 3){
            if(pathSegments[2].equals("shutdown")){
                System.out.println("Shutdown command received. Initiating shutdown...");

                // Send a response before initiating the shutdown
                String responseText = "ProductService has gracefully shutdown";
                exchange.sendResponseHeaders(200, responseText.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseText.getBytes());
                os.close();

                // Schedule the shutdown to allow time for the response to be sent
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        System.out.println("Successfully shutdown ProductService");
                        System.exit(0);
                    }
                }, 5000); // Delay for 5 seconds
            }

            if (pathSegments[2].equals("restart")){
                try (Statement statement = connection.createStatement()) {
                    // Send a response before initiating the shutdown
                    String responseText = "Restart Initiated";
                    exchange.sendResponseHeaders(200, responseText.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseText.getBytes());
                    os.close();

                    statement.execute("DELETE FROM Products"); // Adjust table name and query as necessary
                }
                catch (SQLException e) {
                    System.err.println("Failed to clear users and purchases information: " + e.getMessage());
                }
            }
        }
    }//end of handleGetRequest

    private static void sendProductInfo(HttpExchange exchange, int id, int orderQty, int currentQty) throws IOException{
        Map<String, Object> productData = new HashMap<>();

        if (checkProductExists(id)) {
            String query = "SELECT * FROM Products WHERE id = ?";
            try(PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setInt(1, id);
                ResultSet rs = pstmt.executeQuery();

                //check if user exists
                if (rs.next()) {
                    // Retrieve user data from the result set and populate the userData map
                    productData.put("id", rs.getInt("id"));
                    productData.put("name", rs.getString("name"));
                    productData.put("description", rs.getString("description"));
                    productData.put("price", rs.getString("price"));
                    productData.put("qty", rs.getString("qty"));
                    currentQty = Integer.parseInt(rs.getString("qty"));
                }
                else{
                    JSONObject jsonObject = new JSONObject(productData);
                    sendJsonResponse(exchange, jsonObject, 400);
                }

            } catch (SQLException e) {
                System.out.println("500: Internal Server Error");
                exchange.sendResponseHeaders(500, -1);
            }
            if(orderQty < currentQty) {
                JSONObject jsonObject = new JSONObject(productData);
                sendJsonResponse(exchange, jsonObject, 200);
            }
            else{
                System.out.println("400: Bad Request");
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    private static void sendValidResponse(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> Data = new HashMap<>();

        String query = "SELECT * FROM Products WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Retrieve user data from the result set and populate the userData map
                Data.put("id", rs.getInt("id"));
                Data.put("name", rs.getString("name"));
                Data.put("description", rs.getString("description"));
                Data.put("price", rs.getString("price"));
                Data.put("qty", rs.getString("qty"));
            }

        } catch (SQLException e) {
            // Handle any SQL exceptions
            System.out.println("500: Internal Server Error");
            exchange.sendResponseHeaders(500, -1);
        }
        JSONObject jsonObject = new JSONObject(Data);
        sendJsonResponse(exchange, jsonObject, 200);
    }

    private static void sendJsonResponse(HttpExchange exchange, JSONObject jsonResponse, int statusCode) throws IOException {
        // Convert JSONObject to String
        String responseString = jsonResponse.toString();

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        System.out.println(statusCode + responseString);
        exchange.sendResponseHeaders(statusCode, responseString.getBytes(StandardCharsets.UTF_8).length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseString.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static int getCurrentProductQty(int productId) {
        String query = "SELECT qty FROM Products WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("qty");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching product quantity: " + e.getMessage());
        }
        return -1; // Return -1 if product not found or error occurs
    }

}//End of Class








