package org.sonatype.nexus.plugins.vagrant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VagrantBoxProvider {

  @JsonProperty("name")
  private String name;

  @JsonProperty("url")
  private String url;

  @JsonProperty("checksum_type")
  private String checksumType;

  @JsonProperty("checksum")
  private String checksum;

  public VagrantBoxProvider() {
  }

  public VagrantBoxProvider(final String name, final String url, final String checksumType, final String checksum) {
    this.name = name;
    this.url = url;
    this.checksumType = checksumType;
    this.checksum = checksum;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getChecksumType() {
    return checksumType;
  }

  public void setChecksumType(final String checksumType) {
    this.checksumType = checksumType;
  }

  public String getChecksum() {
    return checksum;
  }

  public void setChecksum(final String checksum) {
    this.checksum = checksum;
  }
}
