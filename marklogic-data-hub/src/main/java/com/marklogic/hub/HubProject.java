package com.marklogic.hub;

import com.marklogic.hub.util.FileUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class for creating a hub Project
 */
public class HubProject {

    public static final String HUB_CONFIG_DIR = "hub-internal-config";
    public static final String USER_CONFIG_DIR = "user-config";
    public static final String ENTITY_CONFIG_DIR = "entity-config";

    private Path projectDir;
    private Path pluginsDir;

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HubProject(String projectDirStr) {
        this.projectDir = Paths.get(projectDirStr).toAbsolutePath();
        this.pluginsDir = this.projectDir.resolve("plugins");
    }

    public Path getHubPluginsDir() {
        return this.pluginsDir;
    }

    public Path getHubEntitiesDir() {
        return this.pluginsDir.resolve("entities");
    }

    public Path getHubConfigDir() {
        return this.projectDir.resolve(HUB_CONFIG_DIR);
    }

    public Path getHubDatabaseDir() {
        return getHubConfigDir().resolve("databases");
    }

    public Path getHubServersDir() {
        return getHubConfigDir().resolve("servers");
    }

    public Path getHubSecurityDir() {
        return getHubConfigDir().resolve("security");
    }

    public Path getHubMimetypesDir() {
        return getHubConfigDir().resolve("mimetypes");
    }

    public Path getUserConfigDir() {
        return this.projectDir.resolve(USER_CONFIG_DIR);
    }

    public Path getUserSecurityDir() {
        return getUserConfigDir().resolve("security");
    }

    public Path getUserDatabaseDir() {
        return getUserConfigDir().resolve("databases");
    }

    public Path getUserSchemasDir() {
        return getUserConfigDir().resolve("schemas");
    }

    public Path getUserServersDir() {
        return getUserConfigDir().resolve("servers");
    }

    public Path getEntityConfigDir() {
        return this.projectDir.resolve(ENTITY_CONFIG_DIR);
    }

    public Path getEntityDatabaseDir() {
        return getEntityConfigDir().resolve("databases");
    }



    public boolean isInitialized() {
        File buildGradle = this.projectDir.resolve("build.gradle").toFile();
        File gradleProperties = this.projectDir.resolve("gradle.properties").toFile();

        File hubConfigDir = getHubConfigDir().toFile();
        File userConfigDir = getUserConfigDir().toFile();
        File databasesDir = getHubDatabaseDir().toFile();
        File serversDir = getHubServersDir().toFile();
        File securityDir = getHubSecurityDir().toFile();

        boolean newConfigInitialized =
            hubConfigDir.exists() &&
                hubConfigDir.isDirectory() &&
                userConfigDir.exists() &&
                userConfigDir.isDirectory() &&
                databasesDir.exists() &&
                databasesDir.isDirectory() &&
                serversDir.exists() &&
                serversDir.isDirectory() &&
                securityDir.exists() &&
                securityDir.isDirectory();

        return buildGradle.exists() &&
            gradleProperties.exists() &&
            newConfigInitialized;
    }

    /**
     * Initializes a directory as a hub project directory.
     * This means putting certain files and folders in place.
     */
    public void init(Map<String, String> customTokens) {
        try {
            this.pluginsDir.toFile().mkdirs();

            Path serversDir = getHubServersDir();
            serversDir.toFile().mkdirs();
            writeResourceFile("ml-config/servers/staging-server.json", serversDir.resolve("staging-server.json"), true);
            writeResourceFile("ml-config/servers/final-server.json", serversDir.resolve("final-server.json"), true);
            writeResourceFile("ml-config/servers/trace-server.json", serversDir.resolve("trace-server.json"), true);
            writeResourceFile("ml-config/servers/job-server.json", serversDir.resolve("job-server.json"), true);

            Path databasesDir = getHubDatabaseDir();
            databasesDir.toFile().mkdirs();
            writeResourceFile("ml-config/databases/staging-database.json", databasesDir.resolve("staging-database.json"), true);
            writeResourceFile("ml-config/databases/final-database.json", databasesDir.resolve("final-database.json"), true);
            writeResourceFile("ml-config/databases/trace-database.json", databasesDir.resolve("trace-database.json"), true);
            writeResourceFile("ml-config/databases/job-database.json", databasesDir.resolve("job-database.json"), true);
            writeResourceFile("ml-config/databases/modules-database.json", databasesDir.resolve("modules-database.json"), true);
            writeResourceFile("ml-config/databases/schemas-database.json", databasesDir.resolve("schemas-database.json"), true);
            writeResourceFile("ml-config/databases/triggers-database.json", databasesDir.resolve("triggers-database.json"), true);

            Path securityDir = getHubSecurityDir();
            Path rolesDir = securityDir.resolve("roles");
            Path usersDir = securityDir.resolve("users");

            rolesDir.toFile().mkdirs();
            usersDir.toFile().mkdirs();

            writeResourceFile("ml-config/security/roles/data-hub-role.json", rolesDir.resolve("data-hub-role.json"), true);
            writeResourceFile("ml-config/security/users/data-hub-user.json", usersDir.resolve("data-hub-user.json"), true);

            Path mimetypesDir = getHubMimetypesDir();
            mimetypesDir.toFile().mkdirs();
            writeResourceFile("ml-config/mimetypes/woff.json", mimetypesDir.resolve("woff.json"), true);
            writeResourceFile("ml-config/mimetypes/woff2.json", mimetypesDir.resolve("woff2.json"), true);

            getUserServersDir().toFile().mkdirs();
            getUserDatabaseDir().toFile().mkdirs();

            Path gradlew = projectDir.resolve("gradlew");
            writeResourceFile("scaffolding/gradlew", gradlew);
            makeExecutable(gradlew);

            Path gradlewbat = projectDir.resolve("gradlew.bat");
            writeResourceFile("scaffolding/gradlew.bat", gradlewbat);
            makeExecutable(gradlewbat);

            Path gradleWrapperDir = projectDir.resolve("gradle").resolve("wrapper");
            gradleWrapperDir.toFile().mkdirs();

            writeResourceFile("scaffolding/gradle/wrapper/gradle-wrapper.jar", gradleWrapperDir.resolve("gradle-wrapper.jar"));
            writeResourceFile("scaffolding/gradle/wrapper/gradle-wrapper.properties", gradleWrapperDir.resolve("gradle-wrapper.properties"));

            writeResourceFile("scaffolding/build_gradle", projectDir.resolve("build.gradle"));
            writeResourceFileWithReplace(customTokens, "scaffolding/gradle_properties", projectDir.resolve("gradle.properties"));
            writeResourceFile("scaffolding/gradle-local_properties", projectDir.resolve("gradle-local.properties"));
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void makeExecutable(Path file) {
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);

        try {
            Files.setPosixFilePermissions(file, perms);
        } catch (Exception e) {

        }
    }

    private void writeResourceFile(String srcFile, Path dstFile) throws IOException {
        writeResourceFile(srcFile, dstFile, false);
    }

    private void writeResourceFile(String srcFile, Path dstFile, boolean overwrite) throws IOException {
        if (overwrite || !dstFile.toFile().exists()) {
            logger.info("Getting file: " + srcFile);
            InputStream inputStream = HubProject.class.getClassLoader().getResourceAsStream(srcFile);
            FileUtil.copy(inputStream, dstFile.toFile());
        }
    }

    private void writeResourceFileWithReplace(Map<String, String> customTokens, String srcFile, Path dstFile) throws IOException {
        writeResourceFileWithReplace(customTokens, srcFile, dstFile, false);
    }

    private void writeResourceFileWithReplace(Map<String, String> customTokens, String srcFile, Path dstFile, boolean overwrite) throws IOException {
        if (overwrite || !dstFile.toFile().exists()) {
            logger.info("Getting file with Replace: " + srcFile);
            InputStream inputStream = HubProject.class.getClassLoader().getResourceAsStream(srcFile);

            String fileContents = IOUtils.toString(inputStream);
            for (String key : customTokens.keySet()) {

                String value = customTokens.get(key);
                if (value != null) {
                    fileContents = fileContents.replace(key, value);
                }
            }
            FileWriter writer = new FileWriter(dstFile.toFile());
            writer.write(fileContents);
            writer.close();
        }
    }
}
