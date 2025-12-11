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
        // Log system properties for debugging
        LOG.debug("System properties:");
        LOG.debug("  jpackage.app-path: {}", System.getProperty("jpackage.app-path"));
        LOG.debug("  user.dir: {}", System.getProperty("user.dir"));
        LOG.debug("  user.home: {}", System.getProperty("user.home"));
        LOG.debug("  os.name: {}", System.getProperty("os.name"));
        
        // First, check if we're running from a jpackage-installed application
        String jpackageAppPath = System.getProperty("jpackage.app-path");
        if (jpackageAppPath != null) {
            LOG.info("Detected jpackage.app-path: {}", jpackageAppPath);
            Path appPath = Path.of(jpackageAppPath);
            
            // jpackage.app-path points to: .../Contents/MacOS/<executable-name>
            // We need to go up to Contents directory, then navigate to runtime/Contents/Home/plugins
            Path macosDir = appPath.getParent();  // Go to MacOS directory
            Path contentsDir = macosDir.getParent();  // Go to Contents directory
            
            LOG.info("Contents directory: {}", contentsDir.toAbsolutePath());
            
            // Navigate to runtime/Contents/Home/plugins from Contents
            Path installedPluginPath = contentsDir.resolve("runtime").resolve("Contents").resolve("Home").resolve("plugins");
            LOG.info("Checking jpackage plugin path: {}", installedPluginPath.toAbsolutePath());
            LOG.info("  Path exists: {}", Files.exists(installedPluginPath));
            LOG.info("  Path is directory: {}", Files.isDirectory(installedPluginPath));
            if (Files.exists(installedPluginPath)) {
                LOG.info("  Path is writable: {}", Files.isWritable(installedPluginPath));
            }
            
            // Check if this path exists
            if (Files.exists(installedPluginPath)) {
                LOG.info("Using jpackage plugin directory: {}", installedPluginPath);
                return installedPluginPath;
            }
            
            // Fallback: try Contents/plugins (in case structure differs)
            Path contentsPlugins = contentsDir.resolve("plugins");
            LOG.info("Trying fallback path: {}", contentsPlugins.toAbsolutePath());
            LOG.info("  Fallback path exists: {}", Files.exists(contentsPlugins));
            
            if (Files.exists(contentsPlugins)) {
                LOG.info("Using fallback jpackage plugin directory: {}", contentsPlugins);
                return contentsPlugins;
            }
            
            // Default to the runtime location even if it doesn't exist yet
            LOG.warn("No existing jpackage plugin directory found. Will use (and create): {}", installedPluginPath);
            return installedPluginPath;
        }

        // If not a jpackage app, check if we're running from a jlink image
        Path userDir = Path.of(System.getProperty("user.dir"));
        LOG.info("Not a jpackage app. Checking jlink image structure from user.dir: {}", userDir);
        
        // Check for jlink image structure: if user.dir is <image>/bin, plugins are at <image>/plugins
        Path parent = userDir.getParent();
        LOG.debug("  user.dir parent: {}", parent);
        
        if (parent != null) {
            Path jlinkPluginPath = parent.resolve("plugins");
            LOG.info("Checking jlink plugin path: {}", jlinkPluginPath.toAbsolutePath());
            LOG.info("  Path exists: {}", Files.exists(jlinkPluginPath));
            
            if (Files.exists(jlinkPluginPath)) {
                LOG.info("Using jlink runtime image plugin directory: {}", jlinkPluginPath);
                return jlinkPluginPath;
            } else {
                LOG.debug("Jlink plugin path does not exist: {}", jlinkPluginPath);
            }
        } else {
            LOG.warn("user.dir has no parent (might be root directory)");
        }
        
        // For local maven builds, check for target/kometRuntimeImage/plugins or target/plugins
        Path targetDir = userDir.resolve("target");
        LOG.info("Checking Maven build directories. Target dir: {}", targetDir.toAbsolutePath());
        LOG.info("  Target dir exists: {}", Files.exists(targetDir));
        
        if (Files.exists(targetDir)) {
            // Check target/kometRuntimeImage/plugins
            Path runtimeImagePlugins = targetDir.resolve("kometRuntimeImage").resolve("plugins");
            LOG.info("Checking Maven runtime image plugin path: {}", runtimeImagePlugins.toAbsolutePath());
            LOG.info("  Path exists: {}", Files.exists(runtimeImagePlugins));
            
            if (Files.exists(runtimeImagePlugins)) {
                LOG.info("Using Maven build runtime image plugin directory: {}", runtimeImagePlugins);
                return runtimeImagePlugins;
            }
            
            // Check target/plugins
            Path targetPlugins = targetDir.resolve("plugins");
            LOG.info("Checking Maven target plugin path: {}", targetPlugins.toAbsolutePath());
            LOG.info("  Path exists: {}", Files.exists(targetPlugins));
            
            if (Files.exists(targetPlugins)) {
                LOG.info("Using Maven build plugin directory: {}", targetPlugins);
                return targetPlugins;
            }
        }

        // Default fallback: try to create plugins directory relative to current location
        Path defaultPath = parent != null ? parent.resolve("plugins") : userDir.resolve("plugins");
        LOG.warn("No existing plugin directory found anywhere!");
        LOG.warn("Attempted paths:");
        if (jpackageAppPath != null) {
            Path appPath = Path.of(jpackageAppPath);
            Path contentsDir = appPath.getParent().getParent();
            LOG.warn("  - jpackage: {}/runtime/Contents/Home/plugins", contentsDir);
            LOG.warn("  - jpackage fallback: {}/plugins", contentsDir);
        }
        if (parent != null) {
            LOG.warn("  - jlink: {}/plugins", parent);
        }
        LOG.warn("  - maven: {}/target/kometRuntimeImage/plugins", userDir);
        LOG.warn("  - maven: {}/target/plugins", userDir);
        LOG.warn("Will create default plugin directory at: {}", defaultPath.toAbsolutePath());
        
        return defaultPath;
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