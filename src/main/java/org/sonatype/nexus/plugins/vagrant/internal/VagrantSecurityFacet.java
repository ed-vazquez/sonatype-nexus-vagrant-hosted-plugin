package org.sonatype.nexus.plugins.vagrant.internal;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.repository.security.ContentPermissionChecker;
import org.sonatype.nexus.repository.security.SecurityFacetSupport;
import org.sonatype.nexus.repository.security.VariableResolverAdapter;

@Named
public class VagrantSecurityFacet
    extends SecurityFacetSupport
{
  @Inject
  public VagrantSecurityFacet(final VagrantFormatSecurityContributor securityContributor,
                              @Named("simple") final VariableResolverAdapter variableResolverAdapter,
                              final ContentPermissionChecker contentPermissionChecker)
  {
    super(securityContributor, variableResolverAdapter, contentPermissionChecker);
  }
}
