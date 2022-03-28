/*
 * Copyright 2019-2022 CloudNetService team & contributors
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

package eu.cloudnetservice.cloudnet.node.config;

import com.google.common.collect.Lists;
import eu.cloudnetservice.cloudnet.common.StringUtil;
import eu.cloudnetservice.cloudnet.common.document.gson.JsonDocument;
import eu.cloudnetservice.cloudnet.common.io.FileUtil;
import eu.cloudnetservice.cloudnet.driver.network.HostAndPort;
import eu.cloudnetservice.cloudnet.driver.network.cluster.NetworkCluster;
import eu.cloudnetservice.cloudnet.driver.network.cluster.NetworkClusterNode;
import eu.cloudnetservice.cloudnet.driver.network.ssl.SSLConfiguration;
import eu.cloudnetservice.cloudnet.driver.service.ProcessSnapshot;
import eu.cloudnetservice.cloudnet.node.Node;
import eu.cloudnetservice.cloudnet.node.setup.DefaultConfigSetup;
import eu.cloudnetservice.cloudnet.node.util.NetworkUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.NonNull;

public final class JsonConfiguration implements Configuration {

  public static final Path CONFIG_FILE_PATH = Path.of(
    System.getProperty("cloudnet.config.json.path", "config.json"));

  private static final Function<String, SSLConfiguration> SSL_CONFIG_PARSER = value -> {
    var values = value.split(";");
    if (values.length == 4 || values.length == 5) {
      return new SSLConfiguration(
        Boolean.parseBoolean(values[0]),
        Boolean.parseBoolean(values[1]),
        values.length == 5 ? Path.of(values[2]) : null,
        Path.of(values[values.length - 2]),
        Path.of(values[values.length - 1]));
    }
    // unable to parse
    return null;
  };

  private String language;

  private NetworkClusterNode identity;
  private NetworkCluster clusterConfig;

  private Set<String> ipWhitelist;

  private double maxCPUUsageToStartServices;

  private int maxMemory;
  private int maxServiceConsoleLogCacheSize;
  private int processTerminationTimeoutSeconds;

  private Boolean forceInitialClusterDataSync;
  private Boolean printErrorStreamLinesFromServices;
  private Boolean runBlockedServiceStartTryLaterAutomatic;

  private String jvmCommand;
  private String hostAddress;
  private String connectHostAddress;

  private Collection<HostAndPort> httpListeners;
  private AccessControlConfiguration accessControlConfiguration;

  private SSLConfiguration clientSslConfig;
  private SSLConfiguration serverSslConfig;
  private SSLConfiguration webSslConfig;

  private JsonDocument properties;

  public JsonConfiguration() {
    // TODO: remove in 4.1
    var oldRegistry = Path.of("local", "registry");
    if (Files.exists(oldRegistry)) {
      var entries = JsonDocument.newDocument(oldRegistry).getDocument("entries");
      if (!entries.empty()) {
        this.properties = JsonDocument.newDocument();
        this.properties.append(entries);
      }
      // remove the old file
      FileUtil.delete(oldRegistry);
    }
  }

  public static @NonNull Configuration loadFromFile(@NonNull Node nodeInstance) {
    if (Files.notExists(CONFIG_FILE_PATH)) {
      // register the setup if the file does not exists
      nodeInstance.installation().registerSetup(new DefaultConfigSetup());
      return new JsonConfiguration().load();
    } else {
      return JsonDocument.newDocument(CONFIG_FILE_PATH).toInstanceOf(JsonConfiguration.class).load();
    }
  }

  @Override
  public boolean fileExists() {
    return Files.exists(CONFIG_FILE_PATH);
  }

  @Override
  public @NonNull Configuration load() {
    if (this.language == null) {
      this.language = ConfigurationUtil.get(
        "cloudnet.config.language",
        "en_US");
    }

    if (this.identity == null) {
      this.identity = new NetworkClusterNode(
        ConfigurationUtil.get(
          "cloudnet.config.identity",
          "Node-" + StringUtil.generateRandomString(4)),
        ConfigurationUtil.get(
          "cloudnet.config.listeners",
          Lists.newArrayList(new HostAndPort(NetworkUtil.localAddress(), 1410)),
          ConfigurationUtil.HOST_AND_PORT_PARSER));
    }

    if (this.clusterConfig == null) {
      this.clusterConfig = new NetworkCluster(
        ConfigurationUtil.get("cloudnet.cluster.id", UUID.randomUUID(), UUID::fromString),
        ConfigurationUtil.get(
          "cloudnet.config.clusterConfig",
          Collections.emptyList(),
          value -> {
            Collection<NetworkClusterNode> nodes = new HashSet<>();
            // collection entries are seperated by ','
            var entries = value.split(",");
            for (var entry : entries) {
              // split at '-': <name>-<listeners>
              var info = entry.split("-");
              if (info.length == 2) {
                nodes.add(new NetworkClusterNode(info[0], ConfigurationUtil.HOST_AND_PORT_PARSER.apply(info[1])));
              }
            }
            return nodes;
          }));
    }

    if (this.ipWhitelist == null) {
      this.ipWhitelist = ConfigurationUtil.get(
        "cloudnet.config.ipWhitelist",
        NetworkUtil.availableIPAddresses(),
        value -> Set.of(value.split(",")));
    }

    if (this.maxCPUUsageToStartServices <= 0) {
      this.maxCPUUsageToStartServices = ConfigurationUtil.get(
        "cloudnet.config.maxCPUUsageToStartServices",
        90D,
        Double::parseDouble);
    }

    if (this.maxMemory <= 0) {
      this.maxMemory = ConfigurationUtil.get(
        "cloudnet.config.maxMemory",
        (int) ((ProcessSnapshot.OS_BEAN.getTotalMemorySize() / (1024 * 1024)) - 512),
        Integer::parseInt);
    }

    if (this.maxServiceConsoleLogCacheSize <= 0) {
      this.maxServiceConsoleLogCacheSize = ConfigurationUtil.get(
        "cloudnet.config.maxServiceConsoleLogCacheSize",
        64,
        Integer::parseInt);
    }

    if (this.processTerminationTimeoutSeconds <= 0) {
      this.processTerminationTimeoutSeconds = ConfigurationUtil.get(
        "cloudnet.config.processTerminationTimeoutSeconds",
        5,
        Integer::parseInt);
    }

    if (this.forceInitialClusterDataSync == null) {
      this.forceInitialClusterDataSync = ConfigurationUtil.get(
        "cloudnet.config.forceInitialClusterDataSync",
        false,
        Boolean::parseBoolean);
    }

    if (this.printErrorStreamLinesFromServices == null) {
      this.printErrorStreamLinesFromServices = ConfigurationUtil.get(
        "cloudnet.config.printErrorStreamLinesFromServices",
        true,
        Boolean::parseBoolean);
    }

    if (this.runBlockedServiceStartTryLaterAutomatic == null) {
      this.runBlockedServiceStartTryLaterAutomatic = ConfigurationUtil.get(
        "cloudnet.config.runBlockedServiceStartTryLaterAutomatic",
        true,
        Boolean::parseBoolean);
    }

    if (this.jvmCommand == null) {
      this.jvmCommand = ConfigurationUtil.get("cloudnet.config.jvmCommand", "java");
    }

    if (this.hostAddress == null) {
      this.hostAddress = ConfigurationUtil.get("cloudnet.config.hostAddress", NetworkUtil.localAddress());
    }

    if (this.connectHostAddress == null) {
      this.connectHostAddress = ConfigurationUtil.get("cloudnet.config.connectHostAddress", this.hostAddress);
    }

    if (this.httpListeners == null) {
      this.httpListeners = ConfigurationUtil.get(
        "cloudnet.config.httpListeners",
        Lists.newArrayList(new HostAndPort("0.0.0.0", 2812)),
        ConfigurationUtil.HOST_AND_PORT_PARSER);
    }

    if (this.accessControlConfiguration == null) {
      this.accessControlConfiguration = ConfigurationUtil.get(
        "cloudnet.config.accessControlConfiguration",
        new AccessControlConfiguration("*", 3600),
        value -> {
          var parts = value.split(";");
          if (parts.length == 2) {
            return new AccessControlConfiguration(parts[0], Integer.parseInt(parts[1]));
          }
          // unable to parse
          return null;
        });
    }

    if (this.clientSslConfig == null) {
      this.clientSslConfig = ConfigurationUtil.get(
        "cloudnet.config.clientSslConfig",
        new SSLConfiguration(
          false,
          false,
          null,
          Path.of("cert.pem"),
          Path.of("private.pem")),
        SSL_CONFIG_PARSER);
    }

    if (this.serverSslConfig == null) {
      this.serverSslConfig = ConfigurationUtil.get(
        "cloudnet.config.serverSslConfig",
        new SSLConfiguration(
          false,
          false,
          null,
          Path.of("cert.pem"),
          Path.of("private.pem")),
        SSL_CONFIG_PARSER);
    }

    if (this.webSslConfig == null) {
      this.webSslConfig = ConfigurationUtil.get(
        "cloudnet.config.webSslConfig",
        new SSLConfiguration(
          false,
          false,
          null,
          Path.of("cert.pem"),
          Path.of("private.pem")),
        SSL_CONFIG_PARSER);
    }

    if (this.properties == null) {
      this.properties = ConfigurationUtil.get(
        "cloudnet.config.properties",
        JsonDocument.newDocument("database_provider", "xodus"),
        JsonDocument::fromJsonString);
    }

    return this.save();
  }

  @Override
  public @NonNull Configuration save() {
    JsonDocument.newDocument(this).write(CONFIG_FILE_PATH);
    return this;
  }

  @Override
  public void reloadFrom(@NonNull Configuration configuration) {
    // supported identity changes
    this.identity.properties().append(configuration.identity().properties());

    // collection configurations
    this.identity.listeners().clear();
    this.identity.listeners().addAll(configuration.identity().listeners());

    this.ipWhitelist.clear();
    this.ipWhitelist.addAll(configuration.ipWhitelist());

    this.clusterConfig.nodes().clear();
    this.clusterConfig.nodes().addAll(configuration.clusterConfig().nodes());

    // general configuration
    this.maxMemory = configuration.maxMemory();
    this.maxCPUUsageToStartServices = configuration.maxCPUUsageToStartServices();
    this.maxServiceConsoleLogCacheSize = configuration.maxServiceConsoleLogCacheSize();
    this.processTerminationTimeoutSeconds = configuration.processTerminationTimeoutSeconds();

    this.forceInitialClusterDataSync = configuration.forceInitialClusterDataSync();
    this.printErrorStreamLinesFromServices = configuration.printErrorStreamLinesFromServices();
    this.runBlockedServiceStartTryLaterAutomatic = configuration.runBlockedServiceStartTryLaterAutomatic();

    this.jvmCommand = configuration.javaCommand();
    this.hostAddress = configuration.hostAddress();
    this.connectHostAddress = configuration.connectHostAddress();

    this.properties = configuration.properties();
    this.accessControlConfiguration = configuration.accessControlConfig();
  }

  @Override
  public @NonNull String language() {
    return this.language;
  }

  @Override
  public void language(@NonNull String language) {
    this.language = language;
  }

  @Override
  public @NonNull NetworkClusterNode identity() {
    return this.identity;
  }

  @Override
  public void identity(@NonNull NetworkClusterNode identity) {
    this.identity = identity;
  }

  @Override
  public @NonNull NetworkCluster clusterConfig() {
    return this.clusterConfig;
  }

  @Override
  public void clusterConfig(@NonNull NetworkCluster clusterConfig) {
    this.clusterConfig = clusterConfig;
  }

  @Override
  public @NonNull Collection<String> ipWhitelist() {
    return this.ipWhitelist != null ? this.ipWhitelist : (this.ipWhitelist = new HashSet<>());
  }

  @Override
  public void ipWhitelist(@NonNull Collection<String> whitelist) {
    this.ipWhitelist = new HashSet<>(whitelist);
  }

  @Override
  public double maxCPUUsageToStartServices() {
    return this.maxCPUUsageToStartServices;
  }

  @Override
  public void maxCPUUsageToStartServices(double value) {
    this.maxCPUUsageToStartServices = value;
  }

  @Override
  public boolean runBlockedServiceStartTryLaterAutomatic() {
    return this.runBlockedServiceStartTryLaterAutomatic;
  }

  @Override
  public void runBlockedServiceStartTryLaterAutomatic(boolean runBlockedServiceStartTryLaterAutomatic) {
    this.runBlockedServiceStartTryLaterAutomatic = runBlockedServiceStartTryLaterAutomatic;
  }

  @Override
  public int maxMemory() {
    return this.maxMemory;
  }

  @Override
  public void maxMemory(int memory) {
    this.maxMemory = memory;
  }

  @Override
  public int maxServiceConsoleLogCacheSize() {
    return this.maxServiceConsoleLogCacheSize;
  }

  @Override
  public void maxServiceConsoleLogCacheSize(int maxServiceConsoleLogCacheSize) {
    this.maxServiceConsoleLogCacheSize = maxServiceConsoleLogCacheSize;
  }

  @Override
  public boolean printErrorStreamLinesFromServices() {
    return this.printErrorStreamLinesFromServices;
  }

  @Override
  public void printErrorStreamLinesFromServices(boolean printErrorStreamLinesFromServices) {
    this.printErrorStreamLinesFromServices = printErrorStreamLinesFromServices;
  }

  @Override
  public @NonNull String hostAddress() {
    return this.hostAddress;
  }

  @Override
  public void hostAddress(@NonNull String hostAddress) {
    this.hostAddress = hostAddress;
  }

  @Override
  public @NonNull Collection<HostAndPort> httpListeners() {
    return this.httpListeners != null ? this.httpListeners : (this.httpListeners = new ArrayList<>());
  }

  @Override
  public void httpListeners(@NonNull Collection<HostAndPort> httpListeners) {
    this.httpListeners = httpListeners;
  }

  @Override
  public @NonNull AccessControlConfiguration accessControlConfig() {
    return this.accessControlConfiguration;
  }

  @Override
  public void accessControlConfig(@NonNull AccessControlConfiguration configuration) {
    this.accessControlConfiguration = configuration;
  }

  @Override
  public @NonNull String connectHostAddress() {
    return this.connectHostAddress;
  }

  @Override
  public void connectHostAddress(@NonNull String connectHostAddress) {
    this.connectHostAddress = connectHostAddress;
  }

  @Override
  public @NonNull SSLConfiguration clientSSLConfig() {
    return this.clientSslConfig;
  }

  @Override
  public void clientSSLConfig(@NonNull SSLConfiguration clientSslConfig) {
    this.clientSslConfig = clientSslConfig;
  }

  @Override
  public @NonNull SSLConfiguration serverSSLConfig() {
    return this.serverSslConfig;
  }

  @Override
  public void serverSSLConfig(@NonNull SSLConfiguration serverSslConfig) {
    this.serverSslConfig = serverSslConfig;
  }

  @Override
  public @NonNull SSLConfiguration webSSLConfig() {
    return this.webSslConfig;
  }

  @Override
  public void webSSLConfig(@NonNull SSLConfiguration webSslConfig) {
    this.webSslConfig = webSslConfig;
  }

  @Override
  public @NonNull String javaCommand() {
    return this.jvmCommand;
  }

  @Override
  public void javaCommand(@NonNull String jvmCommand) {
    this.jvmCommand = jvmCommand;
  }

  @Override
  public int processTerminationTimeoutSeconds() {
    return Math.max(1, this.processTerminationTimeoutSeconds);
  }

  @Override
  public void processTerminationTimeoutSeconds(int processTerminationTimeoutSeconds) {
    this.processTerminationTimeoutSeconds = processTerminationTimeoutSeconds;
  }

  @Override
  public boolean forceInitialClusterDataSync() {
    return this.forceInitialClusterDataSync;
  }

  @Override
  public void forceInitialClusterDataSync(boolean forceInitialClusterDataSync) {
    this.forceInitialClusterDataSync = forceInitialClusterDataSync;
  }

  @Override
  public @NonNull JsonDocument properties() {
    return this.properties;
  }

  @Override
  public void properties(@NonNull JsonDocument properties) {
    this.properties = properties;
  }
}
