package org.sonatype.nexus.plugins.vagrant.rest;

import org.sonatype.nexus.plugins.vagrant.internal.VagrantFormat;
import org.sonatype.nexus.repository.rest.api.model.CleanupPolicyAttributes;
import org.sonatype.nexus.repository.rest.api.model.ComponentAttributes;
import org.sonatype.nexus.repository.rest.api.model.HostedStorageAttributes;
import org.sonatype.nexus.repository.rest.api.model.SimpleApiHostedRepository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class VagrantHostedApiRepository
    extends SimpleApiHostedRepository
{
  @JsonCreator
  public VagrantHostedApiRepository(
      @JsonProperty("name") final String name,
      @JsonProperty("url") final String url,
      @JsonProperty("online") final Boolean online,
      @JsonProperty("storage") final HostedStorageAttributes storage,
      @JsonProperty("cleanup") final CleanupPolicyAttributes cleanup,
      @JsonProperty("component") final ComponentAttributes component)
  {
    super(name, VagrantFormat.NAME, url, online, storage, cleanup, component);
  }
}
