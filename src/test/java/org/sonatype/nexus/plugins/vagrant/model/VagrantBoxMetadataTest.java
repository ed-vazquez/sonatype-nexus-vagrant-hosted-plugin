package org.sonatype.nexus.plugins.vagrant.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class VagrantBoxMetadataTest
    extends TestSupport
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void defaultConstructorInitializesEmptyVersions() {
    VagrantBoxMetadata metadata = new VagrantBoxMetadata();
    assertThat(metadata.getVersions(), is(notNullValue()));
    assertThat(metadata.getVersions(), is(empty()));
  }

  @Test
  public void nameConstructorSetsName() {
    VagrantBoxMetadata metadata = new VagrantBoxMetadata("myorg/mybox");
    assertThat(metadata.getName(), is("myorg/mybox"));
    assertThat(metadata.getVersions(), is(empty()));
  }

  @Test
  public void addVersionAccumulatesVersions() {
    VagrantBoxMetadata metadata = new VagrantBoxMetadata("myorg/mybox");
    metadata.addVersion(new VagrantBoxVersion("1.0.0"));
    metadata.addVersion(new VagrantBoxVersion("2.0.0"));

    assertThat(metadata.getVersions(), hasSize(2));
    assertThat(metadata.getVersions().get(0).getVersion(), is("1.0.0"));
    assertThat(metadata.getVersions().get(1).getVersion(), is("2.0.0"));
  }

  @Test
  public void serializesToExpectedJson() throws Exception {
    VagrantBoxMetadata metadata = new VagrantBoxMetadata("myorg/mybox");
    metadata.setDescription("A test box");

    VagrantBoxVersion version = new VagrantBoxVersion("1.0.0");
    version.addProvider(new VagrantBoxProvider(
        "virtualbox",
        "http://nexus/repository/vagrant/myorg/mybox/1.0.0/virtualbox/mybox.box",
        "sha256",
        "abc123"
    ));
    metadata.addVersion(version);

    String json = MAPPER.writeValueAsString(metadata);

    assertThat(json, containsString("\"name\":\"myorg/mybox\""));
    assertThat(json, containsString("\"description\":\"A test box\""));
    assertThat(json, containsString("\"version\":\"1.0.0\""));
    assertThat(json, containsString("\"status\":\"active\""));
    assertThat(json, containsString("\"name\":\"virtualbox\""));
    assertThat(json, containsString("\"checksum_type\":\"sha256\""));
    assertThat(json, containsString("\"checksum\":\"abc123\""));
  }

  @Test
  public void deserializesFromJson() throws Exception {
    String json = "{\"name\":\"org/box\",\"description\":\"desc\",\"versions\":[" +
        "{\"version\":\"1.0.0\",\"status\":\"active\",\"providers\":[" +
        "{\"name\":\"libvirt\",\"url\":\"http://example.com/box.box\"," +
        "\"checksum_type\":\"sha256\",\"checksum\":\"deadbeef\"}" +
        "]}]}";

    VagrantBoxMetadata metadata = MAPPER.readValue(json, VagrantBoxMetadata.class);

    assertThat(metadata.getName(), is("org/box"));
    assertThat(metadata.getDescription(), is("desc"));
    assertThat(metadata.getVersions(), hasSize(1));

    VagrantBoxVersion version = metadata.getVersions().get(0);
    assertThat(version.getVersion(), is("1.0.0"));
    assertThat(version.getStatus(), is("active"));
    assertThat(version.getProviders(), hasSize(1));

    VagrantBoxProvider provider = version.getProviders().get(0);
    assertThat(provider.getName(), is("libvirt"));
    assertThat(provider.getUrl(), is("http://example.com/box.box"));
    assertThat(provider.getChecksumType(), is("sha256"));
    assertThat(provider.getChecksum(), is("deadbeef"));
  }

  @Test
  public void nullFieldsOmittedInJson() throws Exception {
    VagrantBoxMetadata metadata = new VagrantBoxMetadata("org/box");
    // description is null

    String json = MAPPER.writeValueAsString(metadata);
    assertThat(json, not(containsString("description")));
  }
}
