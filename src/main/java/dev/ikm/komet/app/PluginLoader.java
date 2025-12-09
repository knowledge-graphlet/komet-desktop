package dev.ikm.komet.app;

import dev.ikm.plugin.layer.IkeServiceManager;
import dev.ikm.tinkar.common.service.PluggableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * High-level utility class for loading and managing plugins in applications.
 * This class provides a simplified interface over {@link IkeServiceManager} for
 * common plugin loading scenarios.
 *
 * <p>For simple use cases where you have a single plugin directory, use this class.
 * For advanced scenarios (multiple plugin directories, custom configurations), use
 * {@link IkeServiceManager} directly.
 *
 * @see IkeServiceManager
 */
public class PluginLoader {
    private static final Logger LOG = LoggerFactory.getLogger(PluginLoader.class);
    private static volatile boolean initialized = false;
    private static Path pluginDirectory;

    /**
     * Private constructor to prevent instantiation
     */
    private PluginLoader() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Initializes the plugin system with the default plugin directory location.
     * This delegates to {@link IkeServiceManager#setPluginDirectory(Path)}.
     *
     * @return the resolved plugin directory path
     * @throws IllegalStateException if the plugin system has already been initialized
     */
    public static synchronized Path initialize() {
        Path pluginPath = resolveDefaultPluginPath();
        return initialize(pluginPath);
    }

    /**
     * Initializes the plugin system with a specified plugin directory.
     * This delegates to {@link IkeServiceManager#setPluginDirectory(Path)}.
     *
     * @param pluginPath the directory containing plugin JAR files
     * @return the plugin directory path
     * @throws IllegalStateException if the plugin system has already been initialized
     */
    public static synchronized Path initialize(Path pluginPath) {
        if (initialized) {
            throw new IllegalStateException(
                    "PluginLoader has already been initialized with directory: " + pluginDirectory);
        }

        // Create plugin directory if it doesn't exist
        try {
            Files.createDirectories(pluginPath);
        } catch (Exception e) {
            LOG.error("Failed to create plugin directory: {}", pluginPath, e);
            throw new RuntimeException("Failed to create plugin directory", e);
        }

        LOG.info("Initializing plugin system with directory: {}", pluginPath.toAbsolutePath());

        // Delegate to IkmServiceManager for actual plugin loading
        IkeServiceManager.setPluginDirectory(pluginPath);

        pluginDirectory = pluginPath;
        initialized = true;

        LOG.info("Plugin system initialized successfully");
        return pluginPath;
    }

    /**
     * Loads all available services of the specified type from the plugin system.
     * This delegates to {@link PluggableService#load(Class)}.
     *
     * @param <T> the service type
     * @param serviceClass the class of the service to load
     * @return a ServiceLoader containing all available implementations
     * @throws IllegalStateException if the plugin system has not been initialized
     */
    public static <T> ServiceLoader<T> loadServices(Class<T> serviceClass) {
        ensureInitialized();
        LOG.debug("Loading plugin services for type: {}", serviceClass.getName());
        return PluggableService.load(serviceClass);
    }

    /**
     * Finds the first available service of the specified type.
     *
     * @param <T> the service type
     * @param serviceClass the class of the service to find
     * @return an Optional containing the first service found, or empty if none found
     * @throws IllegalStateException if the plugin system has not been initialized
     */
    public static <T> Optional<T> findFirst(Class<T> serviceClass) {
        ServiceLoader<T> services = loadServices(serviceClass);
        return services.findFirst();
    }

    /**
     * Executes an action with the first available service of the specified type,
     * or runs an alternative action if no service is found.
     *
     * @param <T> the service type
     * @param serviceClass the class of the service to find
     * @param action the action to execute if a service is found
     * @param emptyAction the action to execute if no service is found
     * @throws IllegalStateException if the plugin system has not been initialized
     */
    public static <T> void withService(Class<T> serviceClass,
                                       java.util.function.Consumer<T> action,
                                       Runnable emptyAction) {
        findFirst(serviceClass).ifPresentOrElse(action, emptyAction);
    }

    /**
     * Returns whether the plugin system has been initialized.
     *
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the plugin directory path if the system has been initialized.
     *
     * @return an Optional containing the plugin directory, or empty if not initialized
     */
    public static Optional<Path> getPluginDirectory() {
        return Optional.ofNullable(pluginDirectory);
    }

    /**
     * Ensures the plugin system has been initialized, throwing an exception if not.
     *
     * @throws IllegalStateException if the plugin system has not been initialized
     */
    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "PluginLoader has not been initialized. Call initialize() first.");
        }
    }

    /**
     * Resolves the default plugin path based on the application environment.
     * Checks for a local build directory first, then falls back to the installed
     * application location.
     *
     * @return the resolved plugin directory path
     */
    private static Path resolveDefaultPluginPath() {
        // For local maven builds, use the target/plugins directory
        Path localPluginPath = Path.of(System.getProperty("user.dir"))
                .resolve("target")
                .resolve("plugins");

        if (Files.exists(localPluginPath)) {
            LOG.debug("Using local build plugin directory: {}", localPluginPath);
            return localPluginPath;
        }

        // For installed applications - customize this based on your deployment
        Path installedPluginPath = resolveInstalledPluginPath();
        if (Files.exists(installedPluginPath)) {
            LOG.debug("Using installed application plugin directory: {}", installedPluginPath);
            return installedPluginPath;
        }

        // Default to local target directory (will be created if doesn't exist)
        LOG.debug("Using default plugin directory: {}", localPluginPath);
        return localPluginPath;
    }

    /**
     * Resolves the installed application plugin path based on the operating system.
     * Override this method or provide a system property for custom deployments.
     *
     * @return the path to the installed plugins directory
     */
    private static Path resolveInstalledPluginPath() {
        String appName = System.getProperty("app.name", "MyApp");
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("mac")) {
            return Path.of("/Applications")
                    .resolve(appName + ".app")
                    .resolve("Contents")
                    .resolve("plugins");
        } else if (os.contains("win")) {
            return Path.of(System.getenv("ProgramFiles"))
                    .resolve(appName)
                    .resolve("plugins");
        } else {
            // Linux/Unix
            return Path.of("/opt")
                    .resolve(appName)
                    .resolve("plugins");
        }
    }

    /**
     * Alternative initialization method that reads the plugin directory from a system property.
     *
     * @param systemPropertyKey the system property key containing the plugin directory path
     * @return the resolved plugin directory path
     * @throws IllegalStateException if the plugin system has already been initialized
     */
    public static synchronized Path initializeFromSystemProperty(String systemPropertyKey) {
        String pluginPathString = System.getProperty(systemPropertyKey);
        if (pluginPathString == null || pluginPathString.isEmpty()) {
            LOG.warn("System property '{}' not set, using default plugin directory",
                    systemPropertyKey);
            return initialize();
        }

        Path pluginPath = Path.of(pluginPathString);
        LOG.info("Initializing from system property '{}': {}", systemPropertyKey, pluginPath);
        return initialize(pluginPath);
    }
}