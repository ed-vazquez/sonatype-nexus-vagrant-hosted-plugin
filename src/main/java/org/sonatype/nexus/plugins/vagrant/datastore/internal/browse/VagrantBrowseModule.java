package org.sonatype.nexus.plugins.vagrant.datastore.internal.browse;

import javax.inject.Named;

import org.sonatype.nexus.plugins.vagrant.internal.VagrantFormat;
import org.sonatype.nexus.repository.content.browse.store.FormatBrowseModule;

@Named(VagrantFormat.NAME)
public class VagrantBrowseModule
    extends FormatBrowseModule<VagrantBrowseNodeDAO>
{
}
