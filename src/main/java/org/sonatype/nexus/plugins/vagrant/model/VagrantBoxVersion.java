package org.sonatype.nexus.plugins.vagrant.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VagrantBoxVersion {

  @JsonProperty("version")
  private String version;

  @JsonProperty("status")
  private String status;

  @JsonProperty("providers")
  private List<VagrantBoxProvider> providers;

  public VagrantBoxVersion() {
    this.providers = new ArrayList<>();
  }

  public VagrantBoxVersion(final String version) {
    this.version = version;
    this.status = "active";
    this.providers = new ArrayList<>();
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public List<VagrantBoxProvider> getProviders() {
    return providers;
  }

  public void setProviders(final List<VagrantBoxProvider> providers) {
    this.providers = providers;
  }

  public void addProvider(final VagrantBoxProvider provider) {
    this.providers.add(provider);
  }
}
