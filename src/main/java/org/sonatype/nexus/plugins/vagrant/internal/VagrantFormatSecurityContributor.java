package org.sonatype.nexus.plugins.vagrant.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.security.RepositoryFormatSecurityContributor;

@Named
@Singleton
public class VagrantFormatSecurityContributor
    extends RepositoryFormatSecurityContributor
{
  @Inject
  public VagrantFormatSecurityContributor(@Named(VagrantFormat.NAME) final Format format) {
    super(format);
  }
}
