package com.fueledbychai.drift.common.api;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

/**
 * Manages the Drift Gateway Docker container lifecycle.
 *
 * <p>On {@link #ensureRunning()}, checks if the gateway is already healthy
 * (responds to HTTP health check). If so, reuses it — multiple Java processes
 * can safely share a single gateway instance. If the gateway is not reachable,
 * starts a new Docker container with {@code --restart unless-stopped} so it
 * persists across JVM restarts.</p>
 *
 * <p>The gateway is intentionally never stopped automatically on disconnect.
 * Call {@link #stop()} explicitly if you want to tear it down.</p>
 */
public class DriftGatewayManager {

    private static final Logger logger = LoggerFactory.getLogger(DriftGatewayManager.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration READY_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration PULL_TIMEOUT = Duration.ofMinutes(5);

    private final DriftConfiguration config;
    private DockerClient dockerClient;

    public DriftGatewayManager() {
        this(DriftConfiguration.getInstance());
    }

    public DriftGatewayManager(DriftConfiguration config) {
        this.config = config;
    }

    /**
     * Ensures the Drift Gateway is running and healthy. If it is already reachable,
     * this is a no-op. If not, starts a Docker container and waits for it to become ready.
     *
     * @throws DriftGatewayException if the gateway cannot be started or does not become healthy
     */
    public void ensureRunning() {
        if (isHealthy()) {
            logger.info("Drift Gateway already running at {}", config.getGatewayRestUrl());
            return;
        }

        logger.info("Drift Gateway not reachable, starting Docker container...");
        validateAutoStartConfig();

        String containerName = config.getGatewayContainerName();
        DockerClient client = getDockerClient();

        // Check if a container with this name already exists (stopped)
        String existingContainerId = findExistingContainer(client, containerName);
        if (existingContainerId != null) {
            logger.info("Found existing container '{}', starting it", containerName);
            try {
                client.startContainerCmd(existingContainerId).exec();
            } catch (NotModifiedException e) {
                logger.debug("Container already running");
            }
        } else {
            createAndStartContainer(client, containerName);
        }

        waitForHealthy();
        logger.info("Drift Gateway is ready at {}", config.getGatewayRestUrl());
    }

    /**
     * Stops and removes the Drift Gateway container if it is running.
     * Only call this explicitly — this is never called automatically on disconnect.
     */
    public void stop() {
        String containerName = config.getGatewayContainerName();
        DockerClient client = getDockerClient();
        String containerId = findExistingContainer(client, containerName);
        if (containerId == null) {
            logger.info("No Drift Gateway container '{}' found to stop", containerName);
            return;
        }

        logger.info("Stopping Drift Gateway container '{}'", containerName);
        try {
            client.stopContainerCmd(containerId).withTimeout(10).exec();
        } catch (NotModifiedException e) {
            logger.debug("Container already stopped");
        }
        try {
            client.removeContainerCmd(containerId).exec();
            logger.info("Drift Gateway container '{}' removed", containerName);
        } catch (NotFoundException e) {
            logger.debug("Container already removed");
        }
    }

    /**
     * Returns true if the gateway REST API is reachable and responding.
     */
    public boolean isHealthy() {
        try {
            String url = config.getGatewayRestUrl() + "/v2/markets";
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout((int) HEALTH_CHECK_TIMEOUT.toMillis());
            conn.setReadTimeout((int) HEALTH_CHECK_TIMEOUT.toMillis());
            int status = conn.getResponseCode();
            conn.disconnect();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateAutoStartConfig() {
        if (config.getGatewayKey() == null || config.getGatewayKey().isBlank()) {
            throw new DriftGatewayException("drift.gateway.key is required for auto-start. "
                    + "Set it to a base58-encoded Solana private key or a file path to a keypair JSON.");
        }
        if (config.getGatewayRpcUrl() == null || config.getGatewayRpcUrl().isBlank()) {
            throw new DriftGatewayException("drift.gateway.rpc.url is required for auto-start. "
                    + "Set it to your Solana RPC endpoint.");
        }
    }

    private void createAndStartContainer(DockerClient client, String containerName) {
        String image = config.getGatewayImage();

        // Pull image if not present
        pullImageIfNeeded(client, image);

        int restPort = config.getGatewayRestPort();
        int wsPort = config.getGatewayWsPort();

        // Build command arguments
        List<String> cmd = buildGatewayCommand();

        // Configure port bindings
        ExposedPort exposedRest = ExposedPort.tcp(8080);
        ExposedPort exposedWs = ExposedPort.tcp(1337);
        Ports portBindings = new Ports();
        portBindings.bind(exposedRest, Ports.Binding.bindPort(restPort));
        portBindings.bind(exposedWs, Ports.Binding.bindPort(wsPort));

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPortBindings(portBindings)
                .withRestartPolicy(RestartPolicy.unlessStoppedRestart());

        // If the key looks like a file path, bind-mount it into the container
        String gatewayKey = config.getGatewayKey();
        String envKey;
        if (gatewayKey.startsWith("/") || gatewayKey.startsWith("~")) {
            String containerKeyPath = "/keys/keypair.json";
            hostConfig = hostConfig.withBinds(Bind.parse(gatewayKey + ":" + containerKeyPath + ":ro"));
            envKey = containerKeyPath;
        } else {
            envKey = gatewayKey;
        }

        CreateContainerResponse container = client.createContainerCmd(image)
                .withName(containerName)
                .withEnv("DRIFT_GATEWAY_KEY=" + envKey)
                .withExposedPorts(exposedRest, exposedWs)
                .withHostConfig(hostConfig)
                .withCmd(cmd)
                .withPlatform("linux/amd64")
                .exec();

        client.startContainerCmd(container.getId()).exec();
        logger.info("Started Drift Gateway container '{}' (id: {})", containerName,
                container.getId().substring(0, 12));
    }

    private List<String> buildGatewayCommand() {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(config.getGatewayRpcUrl());
        cmd.add("--host");
        cmd.add("0.0.0.0");

        String markets = config.getGatewayMarkets();
        if (markets != null && !markets.isBlank()) {
            cmd.add("--markets");
            cmd.add(markets);
        }

        if ("devnet".equalsIgnoreCase(config.getEnvironment())) {
            cmd.add("--dev");
        }

        return cmd;
    }

    private void pullImageIfNeeded(DockerClient client, String image) {
        try {
            client.inspectImageCmd(image).exec();
            logger.debug("Image '{}' already available locally", image);
        } catch (NotFoundException e) {
            logger.info("Pulling image '{}' (this may take a minute)...", image);
            try {
                client.pullImageCmd(image)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion(PULL_TIMEOUT.toMinutes(), java.util.concurrent.TimeUnit.MINUTES);
                logger.info("Image '{}' pulled successfully", image);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new DriftGatewayException("Interrupted while pulling image: " + image, ie);
            }
        }
    }

    private void waitForHealthy() {
        long deadline = System.currentTimeMillis() + READY_TIMEOUT.toMillis();
        logger.info("Waiting for Drift Gateway to become healthy (timeout: {}s)...", READY_TIMEOUT.toSeconds());

        while (System.currentTimeMillis() < deadline) {
            if (isHealthy()) {
                return;
            }
            try {
                Thread.sleep(READY_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DriftGatewayException("Interrupted while waiting for gateway to become healthy", e);
            }
        }

        throw new DriftGatewayException("Drift Gateway did not become healthy within "
                + READY_TIMEOUT.toSeconds() + " seconds at " + config.getGatewayRestUrl());
    }

    private String findExistingContainer(DockerClient client, String containerName) {
        List<Container> containers = client.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(Collections.singletonList(containerName))
                .exec();

        for (Container container : containers) {
            String[] names = container.getNames();
            if (names != null) {
                for (String name : names) {
                    // Docker prefixes container names with "/"
                    if (name.equals("/" + containerName)) {
                        return container.getId();
                    }
                }
            }
        }
        return null;
    }

    private synchronized DockerClient getDockerClient() {
        if (dockerClient == null) {
            try {
                DefaultDockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .build();
                ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                        .dockerHost(clientConfig.getDockerHost())
                        .sslConfig(clientConfig.getSSLConfig())
                        .maxConnections(5)
                        .connectionTimeout(Duration.ofSeconds(10))
                        .responseTimeout(Duration.ofSeconds(30))
                        .build();
                dockerClient = DockerClientImpl.getInstance(clientConfig, httpClient);
                // Verify Docker is available
                dockerClient.pingCmd().exec();
            } catch (Exception e) {
                throw new DriftGatewayException("Cannot connect to Docker. "
                        + "Docker is required because Drift only publishes the gateway as a Linux/x86_64 Docker image "
                        + "(no standalone binaries are available). "
                        + "Install Docker Desktop (Mac/Windows) or Docker Engine (Linux), then retry. "
                        + "Alternatively, set drift.gateway.auto.start=false and manage the gateway manually.", e);
            }
        }
        return dockerClient;
    }

    /**
     * Returns the current status of the gateway container, or "not found" if none exists.
     */
    public String getContainerStatus() {
        try {
            DockerClient client = getDockerClient();
            String containerId = findExistingContainer(client, config.getGatewayContainerName());
            if (containerId == null) {
                return "not found";
            }
            InspectContainerResponse info = client.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = info.getState();
            return state != null && state.getStatus() != null ? state.getStatus() : "unknown";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}
