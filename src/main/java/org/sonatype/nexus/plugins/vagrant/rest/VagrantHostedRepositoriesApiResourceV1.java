package org.sonatype.nexus.plugins.vagrant.rest;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Path;

import org.sonatype.nexus.repository.rest.api.RepositoriesApiResourceV1;

import static org.sonatype.nexus.plugins.vagrant.rest.VagrantHostedRepositoriesApiResourceV1.RESOURCE_URI;

@Named
@Singleton
@Path(RESOURCE_URI)
public class VagrantHostedRepositoriesApiResourceV1
    extends VagrantHostedRepositoriesApiResource
{
  static final String RESOURCE_URI = RepositoriesApiResourceV1.RESOURCE_URI + "/vagrant/hosted";
}
