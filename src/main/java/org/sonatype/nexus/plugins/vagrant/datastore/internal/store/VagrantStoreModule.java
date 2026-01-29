package org.sonatype.nexus.plugins.vagrant.datastore.internal.store;

import javax.inject.Named;

import org.sonatype.nexus.plugins.vagrant.internal.VagrantFormat;
import org.sonatype.nexus.repository.content.store.FormatStoreModule;

@Named(VagrantFormat.NAME)
public class VagrantStoreModule
    extends FormatStoreModule<VagrantContentRepositoryDAO,
                              VagrantComponentDAO,
                              VagrantAssetDAO,
                              VagrantAssetBlobDAO>
{
}
