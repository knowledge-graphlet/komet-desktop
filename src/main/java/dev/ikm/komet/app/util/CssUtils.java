/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.komet.app.util;

import fr.brouillard.oss.cssfx.CSSFX;
import fr.brouillard.oss.cssfx.api.URIToPathConverter;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class for managing and applying CSS stylesheets in JavaFX applications.
 * <p>
 * This class provides static methods to add CSS stylesheets to JavaFX {@link Scene} objects.
 * It first attempts to load CSS files from the local file system for development purposes
 * and falls back to loading them from the classpath resources for production environments.
 * Additionally, it integrates with CSSFX to enable live-reloading of CSS files during development.
 * </p>
 * <p>
 * Supported CSS files are declared within the {@link CssFile} enum. To include additional CSS files,
 * declare new enum constants in {@link CssFile} and ensure they are referenced appropriately in the methods.
 * </p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * import static dev.ikm.komet.app.util.CssFile.*;
 * import dev.ikm.komet.app.util.CssUtils;
 * import javafx.scene.Scene;
 * import javafx.scene.layout.BorderPane;
 *
 * // ...
 *
 * BorderPane root = new BorderPane();
 * Scene scene = new Scene(root, 800, 600);
 *
 * // Apply CSS stylesheets using the CssFile enum
 * CssUtils.addStylesheets(scene, KOMET_CSS, KVIEW_CSS);
 *
 * // Set up and show the stage
 * primaryStage.setScene(scene);
 * primaryStage.show();
 * }</pre>
 *
 * @see CssFile
 */
public final class CssUtils {

    private static final Logger LOG = LoggerFactory.getLogger(CssUtils.class);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CssUtils() {
        throw new UnsupportedOperationException("CssUtils class should not be instantiated.");
    }

    /**
     * Adds the specified CSS files to the given JavaFX {@link Scene}. This method attempts to load the CSS files
     * from the local file system first, which is suitable for development environments where CSS files may change frequently.
     * If the CSS files are not found in the local file system, it falls back to loading them from the classpath resources,
     * which is appropriate for production environments. Additionally, this method sets up CSSFX to enable live-reloading
     * of CSS files when changes are detected in the local file system.
     *
     * @param scene    the JavaFX {@link Scene} to which the stylesheets will be added
     * @param cssFiles a variable number of {@link CssFile} enums representing the CSS files to be added
     * @throws NullPointerException if the {@code scene} parameter is {@code null}
     */
    public static void addStylesheets(Scene scene, CssFile... cssFiles) {
        Objects.requireNonNull(scene, "The scene parameter cannot be null.");

        if (cssFiles == null || cssFiles.length == 0) {
            LOG.warn("No CSS files provided to addStylesheets.");
            return;
        }

        final Path workingDirPath = Paths.get(System.getProperty("user.dir"));
        LOG.info("Working directory: {}", workingDirPath);

        final List<String> cssUris = new ArrayList<>();
        final List<CssFile> loadedFromFileSystemList = new ArrayList<>();

        for (CssFile cssFile : cssFiles) {
            Path cssPath = cssFile.resolveAbsolutePath(workingDirPath);
            LOG.debug("Attempting to load CSS '{}' from path: {}", cssFile.getFileName(), cssPath);

            if (Files.exists(cssPath)) {
                String cssUri = cssPath.toUri().toString();
                cssUris.add(cssUri);
                loadedFromFileSystemList.add(cssFile);
                LOG.info("Loaded CSS '{}' from local file system: {}", cssFile.getFileName(), cssUri);
            } else {
                LOG.info("No CSS file '{}' found at local file system path '{}'", cssFile.getFileName(), cssPath);
                LOG.info("Trying to load CSS '{}' from class loader resources...", cssFile.getFileName());
                loadFromResource(cssFile, cssUris);
            }
        }

        if (!cssUris.isEmpty()) {
            scene.getStylesheets().addAll(cssUris);
            LOG.info("Added {} stylesheet(s) to the scene.", cssUris.size());
        } else {
            LOG.warn("No CSS stylesheets were added to the scene.");
        }

        if (!loadedFromFileSystemList.isEmpty()) {
            setupCssMonitor(loadedFromFileSystemList.toArray(new CssFile[0]), workingDirPath);
        }
    }

    /**
     * Loads the specified CSS file from the class loader resources and adds its URI to the provided list.
     * This method is used as a fallback when the CSS file is not found in the local file system.
     * 
     * This method tries multiple approaches with comprehensive logging to diagnose resource loading issues.
     *
     * @param cssFile the {@link CssFile} enum representing the CSS file to load
     * @param cssUris the list to which the CSS URI will be added if the resource is found
     */
    private static void loadFromResource(CssFile cssFile, List<String> cssUris) {
        String resourcePath = cssFile.getResourcePath();
        String moduleName = cssFile.getModuleName();
        
        LOG.info("Trying to load CSS '{}' from class loader resources...", cssFile.getFileName());
        LOG.debug("  Full resource path: {}", resourcePath);
        LOG.debug("  Target module: {}", moduleName);
        
        URL resourceUrl = null;
        String successMethod = null;

        // Try 1: Context class loader (most common for runtime resources)
        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                LOG.debug("  Try 1: Context ClassLoader: {}", contextClassLoader.getClass().getName());
                resourceUrl = contextClassLoader.getResource(resourcePath);
                if (resourceUrl != null) {
                    successMethod = "Context ClassLoader";
                    LOG.info("  ✓ Found via Context ClassLoader: {}", resourceUrl);
                } else {
                    LOG.debug("  ✗ Not found via Context ClassLoader");
                }
            }
        } catch (Exception e) {
            LOG.debug("  ✗ Context ClassLoader failed: {}", e.getMessage());
        }

        // Try 2: CssUtils class loader (standard approach)
        if (resourceUrl == null) {
            try {
                ClassLoader cssUtilsClassLoader = CssUtils.class.getClassLoader();
                LOG.debug("  Try 2: CssUtils ClassLoader: {}", cssUtilsClassLoader.getClass().getName());
                resourceUrl = cssUtilsClassLoader.getResource(resourcePath);
                if (resourceUrl != null) {
                    successMethod = "CssUtils ClassLoader";
                    LOG.info("  ✓ Found via CssUtils ClassLoader: {}", resourceUrl);
                } else {
                    LOG.debug("  ✗ Not found via CssUtils ClassLoader");
                }
            } catch (Exception e) {
                LOG.debug("  ✗ CssUtils ClassLoader failed: {}", e.getMessage());
            }
        }

        // Try 3: Class.getResource with absolute path
        if (resourceUrl == null) {
            try {
                LOG.debug("  Try 3: Class.getResource with absolute path");
                resourceUrl = CssUtils.class.getResource("/" + resourcePath);
                if (resourceUrl != null) {
                    successMethod = "Class.getResource";
                    LOG.info("  ✓ Found via Class.getResource: {}", resourceUrl);
                } else {
                    LOG.debug("  ✗ Not found via Class.getResource");
                }
            } catch (Exception e) {
                LOG.debug("  ✗ Class.getResource failed: {}", e.getMessage());
            }
        }

        // Try 4: Module system via ModuleLayer (for JPMS modules)
        if (resourceUrl == null) {
            try {
                LOG.debug("  Try 4: Module system lookup");
                Optional<Module> moduleOpt = ModuleLayer.boot().findModule(moduleName);
                
                if (moduleOpt.isPresent()) {
                    Module module = moduleOpt.get();
                    LOG.debug("    Module found: {}", module.getName());
                    LOG.debug("    Module is named: {}", module.isNamed());
                    LOG.debug("    Module descriptor: {}", module.getDescriptor().toNameAndVersion());
                    
                    // Check if module opens/exports the package containing the resource
                    String packageName = resourcePath.substring(0, resourcePath.lastIndexOf('/')).replace('/', '.');
                    LOG.debug("    Target package: {}", packageName);
                    LOG.debug("    Module packages: {}", module.getPackages());
                    LOG.debug("    Is package in module: {}", module.getPackages().contains(packageName));
                    
                    // Try to get the resource via the module's class loader
                    ClassLoader moduleClassLoader = module.getClassLoader();
                    if (moduleClassLoader != null) {
                        LOG.debug("    Module ClassLoader: {}", moduleClassLoader.getClass().getName());
                        resourceUrl = moduleClassLoader.getResource(resourcePath);
                        if (resourceUrl != null) {
                            successMethod = "Module ClassLoader";
                            LOG.info("  ✓ Found via Module ClassLoader: {}", resourceUrl);
                        } else {
                            LOG.warn("    ✗ Module ClassLoader could not find resource");
                            
                            // Try with getResourceAsStream to see if it exists but isn't accessible as URL
                            try (var stream = moduleClassLoader.getResourceAsStream(resourcePath)) {
                                if (stream != null) {
                                    LOG.error("    ⚠ Resource EXISTS as stream but NOT as URL - this is a JPMS encapsulation issue!");
                                    LOG.error("    The module '{}' needs to 'open' the package '{}' to unnamed modules", 
                                            moduleName, packageName);
                                } else {
                                    LOG.warn("    Resource does not exist as stream either");
                                }
                            }
                        }
                    } else {
                        LOG.warn("    Module has no class loader (bootstrap or platform loader)");
                    }
                } else {
                    LOG.warn("    Module '{}' not found in boot layer", moduleName);
                }
            } catch (Exception e) {
                LOG.error("  ✗ Module system lookup failed", e);
            }
        }

        // Try 5: Direct module resource access via Module.getResourceAsStream
        if (resourceUrl == null) {
            try {
                LOG.debug("  Try 5: Direct Module resource lookup");
                Optional<Module> moduleOpt = ModuleLayer.boot().findModule(moduleName);
                
                if (moduleOpt.isPresent()) {
                    Module module = moduleOpt.get();
                    try (var stream = module.getResourceAsStream(resourcePath)) {
                        if (stream != null) {
                            LOG.error("  ⚠ CRITICAL: Resource found via Module.getResourceAsStream but not as URL!");
                            LOG.error("    This confirms a JPMS encapsulation issue.");
                            LOG.error("    SOLUTION: Add 'opens {}' to module-info.java of '{}'",
                                    resourcePath.substring(0, resourcePath.lastIndexOf('/')).replace('/', '.'),
                                    moduleName);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.debug("  ✗ Direct module resource lookup failed: {}", e.getMessage());
            }
        }

        // If found, add to the list
        if (resourceUrl != null) {
            String cssResourceUri = resourceUrl.toExternalForm();
            cssUris.add(cssResourceUri);
            LOG.info("Loaded CSS '{}' from {} resource: {}", cssFile.getFileName(), successMethod, cssResourceUri);
        } else {
            LOG.error("CSS resource '{}' not found in class loader after trying all methods.", resourcePath);
            LOG.error("  - Module name: {}", moduleName);
            LOG.error("  - Resource path: {}", resourcePath);
            
            // Log module information for debugging
            try {
                Optional<Module> moduleOpt = ModuleLayer.boot().findModule(moduleName);
                if (moduleOpt.isPresent()) {
                    Module module = moduleOpt.get();
                    LOG.error("  - Module found: YES");
                    LOG.error("  - Module packages: {}", module.getPackages());
                    
                    String packageName = resourcePath.substring(0, resourcePath.lastIndexOf('/')).replace('/', '.');
                    LOG.error("  - Target package '{}' in module: {}", packageName, module.getPackages().contains(packageName));
                    
                    // Check opens
                    if (module.isOpen(packageName)) {
                        LOG.error("  - Package '{}' is OPEN", packageName);
                    } else {
                        LOG.error("  - Package '{}' is NOT OPEN - THIS IS LIKELY THE PROBLEM!", packageName);
                        LOG.error("  - FIX: Add to module-info.java: opens {};", packageName);
                    }
                } else {
                    LOG.error("  - Module '{}' NOT found in boot layer", moduleName);
                }
                
                var kometModules = ModuleLayer.boot().modules().stream()
                        .map(Module::getName)
                        .filter(name -> name.contains("komet"))
                        .toList();
                LOG.error("  - Available Komet modules: {}", kometModules);
                
            } catch (Exception e) {
                LOG.debug("Could not analyze module: {}", e.getMessage());
            }
        }
    }

    /**
     * Sets up CSSFX to monitor changes in the specified CSS files that were loaded from the local file system.
     * This enables live-reloading of CSS stylesheets during development, allowing for immediate visual feedback
     * when CSS files are modified.
     *
     * @param cssFiles   the array of {@link CssFile} enums that were loaded from the file system
     * @param workingDir the working directory {@link Path} used to resolve the CSS file paths
     */
    private static void setupCssMonitor(CssFile[] cssFiles, Path workingDir) {
        final URIToPathConverter myCssConverter = uri -> {
            for (CssFile cssFile : cssFiles) {
                if (uri.endsWith(cssFile.getFileName())) { // More precise matching
                    Path cssPath = cssFile.resolvePathForMonitoring(workingDir);
                    if (Files.exists(cssPath)) {
                        LOG.debug("CSSFX will monitor changes for: {}", cssPath);
                        return cssPath;
                    } else {
                        LOG.warn("CSSFX could not find the path to monitor for CSS file '{}': {}", cssFile.getFileName(), cssPath);
                    }
                }
            }
            return null;
        };

        try {
            CSSFX.addConverter(myCssConverter).start();
            LOG.info("CSSFX has been initialized for live-reloading of CSS files.");
        } catch (Exception e) {
            LOG.error("Failed to initialize CSSFX: {}", e.getMessage(), e);
        }
    }
}
