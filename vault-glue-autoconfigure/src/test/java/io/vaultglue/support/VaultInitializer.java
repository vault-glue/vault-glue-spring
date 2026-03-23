package io.vaultglue.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class VaultInitializer {

    private final String vaultAddr;
    private final String vaultToken;
    private final HttpClient httpClient;

    public VaultInitializer(String vaultAddr, String vaultToken) {
        this.vaultAddr = vaultAddr;
        this.vaultToken = vaultToken;
        this.httpClient = HttpClient.newHttpClient();
    }

    // ─── Engine Enable ───

    public void enableKvV2(String path) {
        enableEngine(path, "{\"type\":\"kv\",\"options\":{\"version\":\"2\"}}");
    }

    public void enableTransit(String path) {
        enableEngine(path, "{\"type\":\"transit\"}");
    }

    public void enableDatabase(String path) {
        enableEngine(path, "{\"type\":\"database\"}");
    }

    private void enableEngine(String path, String body) {
        try {
            post("/v1/sys/mounts/" + path, body);
        } catch (RuntimeException e) {
            if (!e.getMessage().contains("400")) {
                throw e;
            }
            // Engine already mounted — ignore
        }
    }

    // ─── KV Operations ───

    public void kvPut(String mount, String path, String jsonData) {
        post("/v1/" + mount + "/data/" + path, "{\"data\":" + jsonData + "}");
    }

    // ─── Transit Operations ───

    public void createTransitKey(String mount, String keyName, String type) {
        post("/v1/" + mount + "/keys/" + keyName, "{\"type\":\"" + type + "\"}");
    }

    // ─── Database Operations ───

    public void configureDatabaseConnection(String mount, String name, String plugin, String connectionUrl,
                                             String allowedRoles, String username, String password) {
        String body = String.format(
                "{\"plugin_name\":\"%s\",\"connection_url\":\"%s\",\"allowed_roles\":\"%s\","
                        + "\"username\":\"%s\",\"password\":\"%s\"}",
                plugin, connectionUrl, allowedRoles, username, password);
        post("/v1/" + mount + "/config/" + name, body);
    }

    public void createStaticRole(String mount, String roleName, String dbName, String dbUsername,
                                  int rotationPeriod) {
        String body = String.format(
                "{\"db_name\":\"%s\",\"username\":\"%s\",\"rotation_period\":%d}",
                dbName, dbUsername, rotationPeriod);
        post("/v1/" + mount + "/static-roles/" + roleName, body);
    }

    public void createDynamicRole(String mount, String roleName, String dbName,
                                   String creationStatements, String defaultTtl, String maxTtl) {
        String body = String.format(
                "{\"db_name\":\"%s\",\"creation_statements\":[\"%s\"],\"default_ttl\":\"%s\",\"max_ttl\":\"%s\"}",
                dbName, creationStatements, defaultTtl, maxTtl);
        post("/v1/" + mount + "/roles/" + roleName, body);
    }

    // ─── HTTP ───

    private void post(String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddr + path))
                    .header("X-Vault-Token", vaultToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Vault API error " + response.statusCode()
                        + " for " + path + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Vault API call interrupted", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Vault API call failed: " + path, e);
        }
    }
}
