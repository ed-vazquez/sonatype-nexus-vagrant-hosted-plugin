package org.sonatype.nexus.plugins.vagrant.datastore.internal;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.plugins.vagrant.datastore.VagrantContentFacet;
import org.sonatype.nexus.plugins.vagrant.internal.VagrantFormat;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.content.facet.ContentFacetSupport;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.store.FormatStoreManager;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.TempBlob;

import static java.util.Arrays.asList;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;

@Facet.Exposed
@Named(VagrantFormat.NAME)
public class VagrantContentFacetImpl
    extends ContentFacetSupport
    implements VagrantContentFacet
{
  @Inject
  public VagrantContentFacetImpl(
      @Named(VagrantFormat.NAME) final FormatStoreManager formatStoreManager)
  {
    super(formatStoreManager);
  }

  @Override
  public Optional<Content> get(final String path) {
    return assets().path(path).find().map(FluentAsset::download);
  }

  @Override
  public FluentAsset put(final String path, final Payload payload,
                         final String org, final String name,
                         final String version, final String provider) throws IOException
  {
    try (TempBlob tempBlob = blobs().ingest(payload, asList(SHA256))) {
      FluentComponent component = components()
          .name(name)
          .namespace(org)
          .version(version)
          .getOrCreate();

      return assets()
          .path(path)
          .kind(provider)
          .component(component)
          .blob(tempBlob)
          .save();
    }
  }

  @Override
  public boolean delete(final String path) {
    return assets().path(path).find()
        .map(asset -> {
          asset.delete();
          return true;
        })
        .orElse(false);
  }

  @Override
  public Iterable<FluentAsset> getBoxAssets(final String org, final String name) {
    return assets().browse(Integer.MAX_VALUE, null);
  }
}
