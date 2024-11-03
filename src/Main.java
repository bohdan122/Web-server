import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {

    private static final List<Product> products = new ArrayList<>();
    private static final String LOG_FILE = "server.log";

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8088);
        System.out.println("Server started on port 8088");

        initializeProducts();

        while (true) {
            Socket clientSocket = serverSocket.accept();
            handleClientRequest(clientSocket);
        }
    }

    private static void initializeProducts() {
        products.add(new Product("Product1", 100));
        products.add(new Product("Product2", 200));
        products.add(new Product("Product3", 300));
        products.add(new Product("Product4", 400));
        products.add(new Product("Product5", 500));
    }

    private static void handleClientRequest(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            Request request = new Request(in);
            Response response = new Response(out);

            System.out.println("Method: " + request.getMethod());
            System.out.println("Path: " + request.getUrl());
            System.out.println("User-Agent: " + request.getHeaders().get("User-Agent"));

            logRequestToFile(request);

            if ("GET".equals(request.getMethod())) {
                routeRequest(request, response);
            } else if ("POST".equals(request.getMethod()) && "/add-product".equals(request.getUrl())) {
                addProduct(request, response);
            } else {
                response.sendNotFound();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void logRequestToFile(Request request) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String userAgent = request.getHeaders().getOrDefault("User-Agent", "Unknown");

            out.println("[" + timestamp + "] Method: " + request.getMethod() +
                        ", Path: " + request.getUrl() +
                        ", User-Agent: " + userAgent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void routeRequest(Request request, Response response) {
        String path = request.getUrl();
        switch (path) {
            case "/home":
                response.sendHomePage();
                break;
            case "/about":
                response.sendAboutPage();
                break;
            case "/products":
                response.sendFilteredProducts(request);
                break;
            default:
                response.sendNotFound();
                break;
        }
    }

    private static void addProduct(Request request, Response response) {
        Map<String, String> queryParams = request.getQueryParams();
        String name = queryParams.get("name");
        String priceStr = queryParams.get("price");

        if (name != null && priceStr != null) {
            try {
                int price = Integer.parseInt(priceStr);
                products.add(new Product(name, price));
                response.sendResponse(200, "text/html", "Product added successfully");
            } catch (NumberFormatException e) {
                response.sendResponse(400, "text/html", "Invalid price format");
            }
        } else {
            response.sendResponse(400, "text/html", "Missing name or price");
        }
    }

    static class Request {
        private final String method;
        private final String url;
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> queryParams = new HashMap<>();

        public Request(BufferedReader in) throws IOException {
            String requestLine = in.readLine();
            String[] parts = requestLine.split(" ");
            this.method = parts[0];
            this.url = parseUrl(parts[1]);
            parseHeaders(in);
        }

        private String parseUrl(String path) {
            String[] urlParts = path.split("\\?");
            if (urlParts.length > 1) {
                String[] params = urlParts[1].split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2) {
                        queryParams.put(keyValue[0], keyValue[1]);
                    }
                }
            }
            return urlParts[0];
        }

        private void parseHeaders(BufferedReader in) throws IOException {
            String line;
            while (!(line = in.readLine()).isEmpty()) {
                String[] headerParts = line.split(": ");
                if (headerParts.length == 2) {
                    headers.put(headerParts[0], headerParts[1]);
                }
            }
        }

        public String getMethod() {
            return method;
        }

        public String getUrl() {
            return url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public Map<String, String> getQueryParams() {
            return queryParams;
        }
    }

    static class Response {
        private final PrintWriter out;

        public Response(PrintWriter out) {
            this.out = out;
        }

        public void sendHomePage() {
            String body = "<html><body><h1>Welcome to the Home Page</h1></body></html>";
            sendResponse(200, "text/html", body);
        }

        public void sendAboutPage() {
            String body = "<html><body><h1>About Page</h1><p>Last name: Sere, First name: Bohdan, Group: PS-4-1, " +
                    "Favorite anime: JoJo, Favorite song: Come As You Are</p></body></html>";
            sendResponse(200, "text/html", body);
        }

        public void sendFilteredProducts(Request request) {
            Map<String, String> queryParams = request.getQueryParams();
            int minPrice = Integer.parseInt(queryParams.getOrDefault("minPrice", "0"));
            int maxPrice = Integer.parseInt(queryParams.getOrDefault("topPrice", String.valueOf(Integer.MAX_VALUE)));
            int limit = Integer.parseInt(queryParams.getOrDefault("limit", String.valueOf(products.size())));

            List<Product> filteredProducts = filterProducts(minPrice, maxPrice, limit);
            StringBuilder body = new StringBuilder("<html><body><h1>Products</h1><ul>");
            for (Product product : filteredProducts) {
                body.append("<li>").append(product.getName()).append(" - ").append(product.getPrice()).append("</li>");
            }
            body.append("</ul></body></html>");
            sendResponse(200, "text/html", body.toString());
        }

        public void sendNotFound() {
            String body = "<html><body><h1>404 Not Found</h1></body></html>";
            sendResponse(404, "text/html", body);
        }

        public void sendResponse(int statusCode, String contentType, String body) {
            out.println("HTTP/1.1 " + statusCode);
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + body.length());
            out.println();
            out.println(body);
        }

        private List<Product> filterProducts(int minPrice, int maxPrice, int limit) {
            return products.stream()
                    .filter(p -> p.getPrice() >= minPrice && p.getPrice() <= maxPrice)
                    .limit(limit)
                    .toList();
        }
    }

    static class Product {
        private final String name;
        private final int price;

        public Product(String name, int price) {
            this.name = name;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public int getPrice() {
            return price;
        }
    }
}
