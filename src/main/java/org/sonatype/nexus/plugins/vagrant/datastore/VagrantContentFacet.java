package org.sonatype.nexus.plugins.vagrant.datastore;

import java.io.IOException;
import java.util.Optional;

import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

@Facet.Exposed
public interface VagrantContentFacet
    extends ContentFacet
{
  Optional<Content> get(String path);

  FluentAsset put(String path, Payload payload, String org, String name,
                  String version, String provider) throws IOException;

  boolean delete(String path);

  Iterable<FluentAsset> getBoxAssets(String org, String name);
}
