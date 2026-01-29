package org.sonatype.nexus.plugins.vagrant.datastore.internal.browse;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.plugins.vagrant.internal.VagrantFormat;
import org.sonatype.nexus.repository.content.browse.AssetPathBrowseNodeGenerator;

@Singleton
@Named(VagrantFormat.NAME)
public class VagrantBrowseNodeGenerator
    extends AssetPathBrowseNodeGenerator
{
}
