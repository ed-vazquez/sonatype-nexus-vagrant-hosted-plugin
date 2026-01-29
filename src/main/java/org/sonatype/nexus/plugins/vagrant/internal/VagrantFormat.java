package org.sonatype.nexus.plugins.vagrant.internal;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.Format;

@Named(VagrantFormat.NAME)
@Singleton
public class VagrantFormat
    extends Format
{
  public static final String NAME = "vagrant";

  public VagrantFormat() {
    super(NAME);
  }
}
