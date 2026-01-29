package org.sonatype.nexus.plugins.vagrant.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VagrantBoxMetadata {

  @JsonProperty("name")
  private String name;

  @JsonProperty("description")
  private String description;

  @JsonProperty("versions")
  private List<VagrantBoxVersion> versions;

  public VagrantBoxMetadata() {
    this.versions = new ArrayList<>();
  }

  public VagrantBoxMetadata(final String name) {
    this.name = name;
    this.versions = new ArrayList<>();
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public List<VagrantBoxVersion> getVersions() {
    return versions;
  }

  public void setVersions(final List<VagrantBoxVersion> versions) {
    this.versions = versions;
  }

  public void addVersion(final VagrantBoxVersion version) {
    this.versions.add(version);
  }
}
