package org.sonatype.nexus.plugins.vagrant.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxMetadata;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxProvider;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxVersion;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds Vagrant catalog metadata JSON by scanning stored assets.
 *
 * Asset paths follow: /{org}/{name}/{version}/{provider}/{filename}.box
 */
@Named
@Singleton
public class VagrantMetadataBuilder {

  private static final Pattern ASSET_PATH_PATTERN =
      Pattern.compile("^/([^/]+)/([^/]+)/([^/]+)/([^/]+)/[^/]+\\.box$");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Build metadata for a specific box by scanning all matching assets.
   */
  public VagrantBoxMetadata buildMetadata(final String baseUrl,
                                          final String org,
                                          final String name,
                                          final Iterable<FluentAsset> assets)
  {
    VagrantBoxMetadata metadata = new VagrantBoxMetadata(org + "/" + name);
    metadata.setDescription("Vagrant box " + org + "/" + name);

    Map<String, VagrantBoxVersion> versionMap = new LinkedHashMap<>();

    for (FluentAsset asset : assets) {
      String path = asset.path();
      Matcher matcher = ASSET_PATH_PATTERN.matcher(path);
      if (!matcher.matches()) {
        continue;
      }

      String assetOrg = matcher.group(1);
      String assetName = matcher.group(2);
      if (!org.equals(assetOrg) || !name.equals(assetName)) {
        continue;
      }

      String version = matcher.group(3);
      String provider = matcher.group(4);

      VagrantBoxVersion boxVersion = versionMap.computeIfAbsent(version, VagrantBoxVersion::new);

      String downloadUrl = baseUrl + path;
      String checksum = extractChecksum(asset);

      VagrantBoxProvider boxProvider = new VagrantBoxProvider(
          provider,
          downloadUrl,
          checksum != null ? "sha256" : null,
          checksum
      );

      boxVersion.addProvider(boxProvider);
    }

    versionMap.values().forEach(metadata::addVersion);
    return metadata;
  }

  public String toJson(final VagrantBoxMetadata metadata) throws JsonProcessingException {
    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
  }

  private String extractChecksum(final FluentAsset asset) {
    return asset.blob()
        .map(blob -> {
          Map<String, String> checksums = blob.checksums();
          String sha = checksums.get("sha256");
          if (sha == null) {
            sha = checksums.get("SHA256");
          }
          return sha;
        })
        .orElse(null);
  }
}
