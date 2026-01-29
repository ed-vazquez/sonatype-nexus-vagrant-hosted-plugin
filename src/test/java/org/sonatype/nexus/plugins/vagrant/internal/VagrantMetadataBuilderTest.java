package org.sonatype.nexus.plugins.vagrant.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxMetadata;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxProvider;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxVersion;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;

public class VagrantMetadataBuilderTest
    extends TestSupport
{
  private static final String BASE_URL = "http://nexus:8081/repository/vagrant-local";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock
  private FluentAsset asset1;

  @Mock
  private FluentAsset asset2;

  @Mock
  private FluentAsset asset3;

  @Mock
  private FluentAsset assetDifferentBox;

  @Mock
  private AssetBlob blob1;

  @Mock
  private AssetBlob blob2;

  private VagrantMetadataBuilder underTest;

  @Before
  public void setUp() {
    underTest = new VagrantMetadataBuilder();

    when(asset1.path()).thenReturn("/myorg/mybox/1.0.0/virtualbox/mybox.box");
    when(asset1.blob()).thenReturn(Optional.of(blob1));
    when(blob1.checksums()).thenReturn(Collections.singletonMap("SHA256", "aaa111"));

    when(asset2.path()).thenReturn("/myorg/mybox/1.0.0/libvirt/mybox.box");
    when(asset2.blob()).thenReturn(Optional.of(blob2));
    when(blob2.checksums()).thenReturn(Collections.singletonMap("SHA256", "bbb222"));

    when(asset3.path()).thenReturn("/myorg/mybox/2.0.0/virtualbox/mybox.box");
    when(asset3.blob()).thenReturn(Optional.empty());

    when(assetDifferentBox.path()).thenReturn("/myorg/otherbox/1.0.0/virtualbox/otherbox.box");
    when(assetDifferentBox.blob()).thenReturn(Optional.empty());
  }

  @Test
  public void buildMetadataSetsNameAndDescription() {
    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Collections.singletonList(asset1));

    assertThat(metadata.getName(), is("myorg/mybox"));
    assertThat(metadata.getDescription(), is("Vagrant box myorg/mybox"));
  }

  @Test
  public void buildMetadataGroupsProvidersByVersion() {
    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Arrays.asList(asset1, asset2, asset3));

    assertThat(metadata.getVersions(), hasSize(2));

    VagrantBoxVersion v1 = metadata.getVersions().get(0);
    assertThat(v1.getVersion(), is("1.0.0"));
    assertThat(v1.getStatus(), is("active"));
    assertThat(v1.getProviders(), hasSize(2));
    assertThat(v1.getProviders().get(0).getName(), is("virtualbox"));
    assertThat(v1.getProviders().get(1).getName(), is("libvirt"));

    VagrantBoxVersion v2 = metadata.getVersions().get(1);
    assertThat(v2.getVersion(), is("2.0.0"));
    assertThat(v2.getProviders(), hasSize(1));
    assertThat(v2.getProviders().get(0).getName(), is("virtualbox"));
  }

  @Test
  public void buildMetadataGeneratesCorrectDownloadUrls() {
    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Collections.singletonList(asset1));

    VagrantBoxProvider provider = metadata.getVersions().get(0).getProviders().get(0);
    assertThat(provider.getUrl(),
        is("http://nexus:8081/repository/vagrant-local/myorg/mybox/1.0.0/virtualbox/mybox.box"));
  }

  @Test
  public void buildMetadataIncludesChecksumWhenPresent() {
    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Collections.singletonList(asset1));

    VagrantBoxProvider provider = metadata.getVersions().get(0).getProviders().get(0);
    assertThat(provider.getChecksumType(), is("sha256"));
    assertThat(provider.getChecksum(), is("aaa111"));
  }

  @Test
  public void buildMetadataOmitsChecksumWhenNoBlobPresent() {
    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Collections.singletonList(asset3));

    VagrantBoxProvider provider = metadata.getVersions().get(0).getProviders().get(0);
    assertThat(provider.getChecksumType(), is(nullValue()));
    assertThat(provider.getChecksum(), is(nullValue()));
  }

  @Test
  public void buildMetadataFiltersOutNonMatchingBoxes() {
    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Arrays.asList(asset1, assetDifferentBox));

    assertThat(metadata.getVersions(), hasSize(1));
    assertThat(metadata.getVersions().get(0).getProviders(), hasSize(1));
  }

  @Test
  public void buildMetadataReturnsEmptyVersionsWhenNoAssetsMatch() {
    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Collections.singletonList(assetDifferentBox));

    assertThat(metadata.getVersions(), is(empty()));
  }

  @Test
  public void buildMetadataIgnoresNonBoxPaths() {
    when(asset1.path()).thenReturn("/myorg/mybox/readme.txt");

    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Collections.singletonList(asset1));

    assertThat(metadata.getVersions(), is(empty()));
  }

  @Test
  public void toJsonProducesValidJson() throws Exception {
    VagrantBoxMetadata metadata = underTest.buildMetadata(BASE_URL, "myorg", "mybox",
        Arrays.asList(asset1, asset2));

    String json = underTest.toJson(metadata);
    JsonNode root = MAPPER.readTree(json);

    assertThat(root.get("name").asText(), is("myorg/mybox"));
    assertThat(root.get("versions").isArray(), is(true));
    assertThat(root.get("versions").size(), is(1));

    JsonNode version = root.get("versions").get(0);
    assertThat(version.get("version").asText(), is("1.0.0"));
    assertThat(version.get("providers").size(), is(2));
  }
}
