package org.sonatype.nexus.plugins.vagrant.rest;

import javax.inject.Named;

import org.sonatype.nexus.repository.rest.api.HostedRepositoryApiRequestToConfigurationConverter;

@Named
public class VagrantHostedRepositoryApiRequestToConfigurationConverter
    extends HostedRepositoryApiRequestToConfigurationConverter<VagrantHostedRepositoryApiRequest>
{
}
