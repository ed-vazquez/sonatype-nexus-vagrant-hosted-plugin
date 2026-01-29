package org.sonatype.nexus.plugins.vagrant.rest;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.plugins.vagrant.internal.VagrantFormat;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.rest.api.SimpleApiRepositoryAdapter;
import org.sonatype.nexus.repository.rest.api.model.AbstractApiRepository;
import org.sonatype.nexus.repository.routing.RoutingRuleStore;
import org.sonatype.nexus.repository.types.HostedType;

@Named(VagrantFormat.NAME)
public class VagrantRepositoryAdapter
    extends SimpleApiRepositoryAdapter
{
  @Inject
  public VagrantRepositoryAdapter(final RoutingRuleStore routingRuleStore) {
    super(routingRuleStore);
  }

  @Override
  public AbstractApiRepository adapt(final Repository repository) {
    if (HostedType.NAME.equals(repository.getType().toString())) {
      return new VagrantHostedApiRepository(
          repository.getName(),
          repository.getUrl(),
          repository.getConfiguration().isOnline(),
          getHostedStorageAttributes(repository),
          getCleanupPolicyAttributes(repository),
          getComponentAttributes(repository));
    }
    return super.adapt(repository);
  }
}
