package org.UserService;

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

import java.util.Timer;
import java.util.TimerTask;


public class Main {

    public static Connection connection = null;

    //SETTING UP SERVER FOR PRODUCT SERVICE
    public static void main(String[] args)  {
        // Setup server and database connection
        setupServer();
        setupDatabase();

    }

    //SETTING UP SERVER FOR USER SERVICE
    private static void setupServer() {
        try {

            String content = new String(Files.readAllBytes(Paths.get("config.json")));
            JSONObject config = new JSONObject(content);
            JSONObject userServiceConfig = config.getJSONObject("UserService");
            String ip = userServiceConfig.getString("ip");
            int port = userServiceConfig.getInt("port");

            HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);

            server.setExecutor(Executors.newFixedThreadPool(100));

            // Set up context for /test POST request
            server.createContext("/user", new Handler());
            server.setExecutor(null); // creates a default executor
            server.start();
            System.out.println("Server started on IP " + ip + " and port " + port);

        } catch (IOException e) {
            System.out.println("Server setup error: " + e.getMessage());
        }
    }

    //SETTING UP DATBASE FOR USERSERVICE
    private static void setupDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:src/UserService/src/database/Users.db");
            // Create the users table
            createUserTable();
            createPurchasesTable();

        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Database connection error: " + e.getMessage());
        }
    }

    //Creates User Database
    private static void createUserTable() {
        String sql = "CREATE TABLE IF NOT EXISTS Users (\n"
                + " id STRING PRIMARY KEY,\n"
                + " username TEXT UNIQUE,\n"
                + " email TEXT UNIQUE,\n"
                + " password TEXT \n"
                + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("Successfully created the user table");
        } catch (SQLException e) {
            System.out.println("Error creating table: " + e.getMessage());
        }
    }

    private static void createPurchasesTable() {
        String sql = "CREATE TABLE IF NOT EXISTS Purchases (\n"
                + " id INT,\n"
                + " product_id INT,\n"
                + " qty INT\n"
                + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("Successfully created the purchase table");
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

                }
                if ("GET".equals(method)) {
                    handleGetRequest(exchange);

                } else {
                    exchange.sendResponseHeaders(405, -1);
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

    //Checks if User is Valid
    private static boolean checkUserExists(Integer id) {
        String sqlString = "SELECT id FROM Users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlString)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    //Handles Post Requests to API - delete, create and update
    private static void handlePostRequest(HttpExchange exchange,Map<String, Object> jsonMap) throws IOException {
        try {
            String command = (String) jsonMap.getOrDefault("command", null);
            Integer id = (Integer) jsonMap.getOrDefault("id", null);
            String username = (String) jsonMap.getOrDefault("username", null);
            String email = (String) jsonMap.getOrDefault("email", null);
            String password = (String) jsonMap.getOrDefault("password", null);
            Integer product_id = (Integer) jsonMap.getOrDefault("product_id", null);
            Integer qty = (Integer) jsonMap.getOrDefault("qty", null);

            switch (command) {
                case "place":
                    if(id != null && product_id != null && qty != null){
                      if(!checkUserExists(id)) {
                          System.out.println("400: Bad Request");
                          exchange.sendResponseHeaders(400, -1);
                          break;
                      } else{
                          String sql = "INSERT INTO Purchases (id, product_id, qty) VALUES (?, ?, ?)";
                          try (PreparedStatement pstmt = connection.prepareStatement(sql)){
                              pstmt.setInt(1, id);
                              pstmt.setInt(2, product_id);
                              pstmt.setInt(3, qty);
                              pstmt.executeUpdate();
                              sendValidResponse(exchange,  id);

                          } catch (SQLException e) {
                              System.out.println(e.getMessage());
                              System.out.println("500: Internal Server Error");
                              exchange.sendResponseHeaders(500, -1);
                          }
                      }

                      }
                    break;
                case "create":
                    if (id != null && username != null && email != null && password != null) {
                        if (checkUserExists(id)) {
                            System.out.println("400: Bad Request");
                            exchange.sendResponseHeaders(400, -1);
                            break;
                        } else {
                            String sql = "INSERT INTO Users (id, username, email, password) VALUES (?, ?, ?, ?)";
                            try (PreparedStatement pstmt = connection.prepareStatement(sql)){
                                pstmt.setInt(1, id);
                                pstmt.setString(2, username);
                                pstmt.setString(3, email);
                                pstmt.setString(4, password);
                                pstmt.executeUpdate();
                                sendValidResponse(exchange,  id);

                            } catch (SQLException e) {
                                System.out.println("500: Internal Server Error");
                                exchange.sendResponseHeaders(500, -1);
                            }
                        }

                    } else {
                        System.out.println("400: Bad Request");
                        exchange.sendResponseHeaders(400, -1);
                    }
                    break;
                case "update":
                    if (!checkUserExists(id)) {
                        System.out.println("404: Not Found");
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    if (id != null) {
                        StringBuilder sqlBuilder = new StringBuilder("UPDATE users SET ");
                        List<Object> parameters = new ArrayList<>();

                        if (jsonMap.containsKey("username")) {
                            sqlBuilder.append("username = ?, ");
                            parameters.add(jsonMap.get("username"));
                        }
                        if (jsonMap.containsKey("email")) {
                            sqlBuilder.append("email = ?, ");
                            parameters.add(jsonMap.get("email"));
                        }
                        if (jsonMap.containsKey("password")) {
                            sqlBuilder.append("password = ?, ");
                            parameters.add(jsonMap.get("password"));
                        }

                        if (parameters.size() > 0) {
                            sqlBuilder.delete(sqlBuilder.length() - 2, sqlBuilder.length());
                        } else {
                            System.out.println("Bad Request");
                            exchange.sendResponseHeaders(400, -1);
                            return;
                        }

                        sqlBuilder.append(" WHERE id = ?");
                        parameters.add(id);

                        try (PreparedStatement pstmt = connection.prepareStatement(sqlBuilder.toString())){
                            for (int i = 0; i < parameters.size(); i++) {
                                pstmt.setObject(i + 1, parameters.get(i));
                            }

                            int affectedRows = pstmt.executeUpdate();
                            if (affectedRows > 0) {
                                sendValidResponse(exchange,  id);
                            } else {
                                System.out.println("409: Conflict");
                                exchange.sendResponseHeaders(409, -1);
                            }

                        } catch (SQLException e) {
                            System.out.println("500: Internal Server Error");
                            exchange.sendResponseHeaders(500, -1);
                        }
                    } else {
                        System.out.println("404: Not Found");
                        exchange.sendResponseHeaders(404, -1);
                    }
                    break;
                case "delete":
                    // Handles delete command
                    if (id != null && username != null && email != null && password != null) {
                        if (checkUserExists(id)) {
                            String sql = "DELETE FROM Users WHERE id = ?";

                            try (PreparedStatement pstmt = connection.prepareStatement(sql)){
                                pstmt.setInt(1, id); // Set the value of the first parameter (the user's ID)
                                pstmt.executeUpdate();
                                System.out.println("200: OK");
                                exchange.sendResponseHeaders(200, -1);

                            } catch (SQLException e) {
                                System.out.println("500: Internal Server Error");
                                exchange.sendResponseHeaders(500, -1);
                            }
                        } else {
                            System.out.println("404: Not Found");
                            exchange.sendResponseHeaders(404, -1);
                        }
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
        try {
            String path = exchange.getRequestURI().getPath();
            String [] pathSegments = path.split("/");
            String command = "";
            int id = 1;

            System.out.println(pathSegments);

            if(pathSegments.length > 3) {
                command = pathSegments[2];
                id = Integer.parseInt(pathSegments[3]);
                System.out.println("LENGTH IS 3");
            }
            else if(pathSegments.length == 3) { ///user/shutdown
                command = pathSegments[2]; // this would be "shutdown"
                System.out.println("LENGTH IS 2");
            }

            if(command.equals("get")){
                System.out.println("IN GETTT");
                System.out.println(command);
                System.out.println(id);
                sendUserInfo(exchange, id);
            } else if(command.equals("purchased")){
                sendPurchaseInfo(exchange, id);
            } else if(command.equals("shutdown")){
                System.out.println("IN SHUTDOWN");
                System.out.println("Shutdown command received. Initiating shutdown...");

                // Send a response before initiating the shutdown
                String responseText = "UserService has gracefully shutdown";
                exchange.sendResponseHeaders(200, responseText.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseText.getBytes());
                os.close();

                // Schedule the shutdown to allow time for the response to be sent
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        System.out.println("Successfully shutdown UserService");
                        System.exit(0);
                    }
                }, 5000); // Delay for 5 seconds
            } else if(command.equals("restart")) {
                try (Statement statement = connection.createStatement()) {

                    // Send a response before initiating the shutdown
                    String responseText = "Restart Initiated";
                    exchange.sendResponseHeaders(200, responseText.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseText.getBytes());
                    os.close();

                    statement.execute("DELETE FROM Users"); // Adjust table name and query as necessary
                    statement.execute("DELETE FROM Purchases"); // Adjust table name and query as necessary


                }
                 catch (SQLException e) {
                    System.err.println("Failed to clear users and purchases information: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("500: Internal Server Error");
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }

    }//end of handleGetRequest

    private static void sendPurchaseInfo(HttpExchange exchange, int id) throws IOException {
        List<String> purchasesList = new ArrayList<>();

        if (checkUserExists(id)) {
            String query = "SELECT product_id, qty FROM Purchases WHERE id = ?";
            StringBuilder jsonBuilder = new StringBuilder("{");
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setInt(1, id);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    if (jsonBuilder.length() > 1) { // More than just the opening brace
                        jsonBuilder.append(", ");
                    }
                    int productId = rs.getInt("product_id");
                    int qty = rs.getInt("qty");
                    jsonBuilder.append(productId).append(":").append(qty);
                }
            } catch (SQLException e) {
                System.out.println("Database error occurred");
                // Handle the error appropriately
            }
            jsonBuilder.append("}");
            String jsonString = jsonBuilder.toString();
            System.out.println(jsonString); // Optional: Print the JSON-like string

            // Directly sending jsonString as the response
            byte[] responseBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } else {
            System.out.println("404: Not Found");
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private static void sendUserInfo(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> userData = new HashMap<>();

        if (checkUserExists(id)) {
            String query = "SELECT * FROM Users WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {

                pstmt.setInt(1, id);

                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    // Retrieve user data from the result set and populate the userData map
                    userData.put("id", rs.getInt("id"));
                    userData.put("username", rs.getString("username"));
                    userData.put("email", rs.getString("email"));
                    userData.put("password", rs.getString("password"));
                }

            } catch (SQLException e) {
                System.out.println("500: Internal Server Error");
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            JSONObject jsonObject = new JSONObject(userData);
            sendJsonResponse(exchange, jsonObject, 200);
        } else {
            System.out.println("404: Not Found");
            exchange.sendResponseHeaders(404, -1);
        }
    }


    private static void sendValidResponse(HttpExchange exchange, int id) throws IOException {
        Map<String, Object> Data = new HashMap<>();

        String query = "SELECT * FROM Users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, id);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Data.put("id", rs.getInt("id"));
                Data.put("username", rs.getString("username"));
                Data.put("email", rs.getString("email"));
                Data.put("password", rs.getString("password"));
            }

        } catch (SQLException e) {
            System.out.println("500: Internal Server Error");
            exchange.sendResponseHeaders(500, -1);
        }
        JSONObject jsonObject = new JSONObject(Data);
        sendJsonResponse(exchange, jsonObject, 200);
    }

    private static void sendJsonResponse(HttpExchange exchange, JSONObject jsonResponse, int statusCode) throws IOException {
        String responseString = jsonResponse.toString();

        System.out.println(responseString);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        System.out.println("200" + " " + responseString);
        exchange.sendResponseHeaders(statusCode, responseString.getBytes(StandardCharsets.UTF_8).length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseString.getBytes(StandardCharsets.UTF_8));
        }
    }
}//End of Class