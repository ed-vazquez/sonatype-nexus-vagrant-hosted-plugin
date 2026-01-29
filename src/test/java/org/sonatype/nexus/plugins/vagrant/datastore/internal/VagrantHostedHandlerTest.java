package org.sonatype.nexus.plugins.vagrant.datastore.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.plugins.vagrant.datastore.VagrantContentFacet;
import org.sonatype.nexus.plugins.vagrant.internal.VagrantMetadataBuilder;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxMetadata;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxProvider;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxVersion;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonatype.nexus.repository.http.HttpStatus.*;

public class VagrantHostedHandlerTest
    extends TestSupport
{
  @Mock private Context context;
  @Mock private Request request;
  @Mock private Repository repository;
  @Mock private VagrantContentFacet contentFacet;
  @Mock private Content content;
  @Mock private Payload payload;
  @Mock private TokenMatcher.State tokenState;

  private VagrantMetadataBuilder metadataBuilder;
  private VagrantHostedHandler underTest;

  @Before
  public void setUp() {
    metadataBuilder = new VagrantMetadataBuilder();
    underTest = new VagrantHostedHandler(metadataBuilder);

    when(context.getRequest()).thenReturn(request);
    when(context.getRepository()).thenReturn(repository);
    when(repository.facet(VagrantContentFacet.class)).thenReturn(contentFacet);
    when(repository.getUrl()).thenReturn("http://nexus/repository/vagrant-local");
    when(context.getAttributes()).thenReturn(new org.sonatype.nexus.common.collect.AttributesMap());
    context.getAttributes().set(TokenMatcher.State.class, tokenState);
  }

  private void setTokens(Map<String, String> tokens) {
    when(tokenState.getTokens()).thenReturn(tokens);
  }

  // -- GET box file tests --

  @Test
  public void getBoxFileReturns200WhenFound() throws Exception {
    Map<String, String> tokens = boxFileTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.get("/myorg/mybox/1.0.0/virtualbox/mybox.box")).thenReturn(Optional.of(content));

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(OK));
  }

  @Test
  public void getBoxFileReturns404WhenNotFound() throws Exception {
    Map<String, String> tokens = boxFileTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.get("/myorg/mybox/1.0.0/virtualbox/mybox.box")).thenReturn(Optional.empty());

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  // -- GET metadata tests --

  @Test
  public void getMetadataReturns200WithVersions() throws Exception {
    Map<String, String> tokens = metadataTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");

    VagrantBoxMetadata metadata = new VagrantBoxMetadata("myorg/mybox");
    VagrantBoxVersion version = new VagrantBoxVersion("1.0.0");
    version.addProvider(new VagrantBoxProvider("virtualbox", "http://example.com/box.box", null, null));
    metadata.addVersion(version);

    // Return assets that the builder will match
    when(contentFacet.getBoxAssets("myorg", "mybox")).thenReturn(Collections.emptyList());

    // With no assets matching, metadata will have empty versions -> 404
    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  @Test
  public void getMetadataReturns404WhenNoVersions() throws Exception {
    Map<String, String> tokens = metadataTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("GET");
    when(contentFacet.getBoxAssets("myorg", "mybox")).thenReturn(Collections.emptyList());

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  // -- PUT tests --

  @Test
  public void putReturns201OnSuccess() throws Exception {
    Map<String, String> tokens = boxFileTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("PUT");
    when(request.getPayload()).thenReturn(payload);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(CREATED));
    verify(contentFacet).put(
        eq("/myorg/mybox/1.0.0/virtualbox/mybox.box"),
        eq(payload),
        eq("myorg"),
        eq("mybox"),
        eq("1.0.0"),
        eq("virtualbox")
    );
  }

  @Test
  public void putReturns400WhenNoPayload() throws Exception {
    Map<String, String> tokens = boxFileTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("PUT");
    when(request.getPayload()).thenReturn(null);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(BAD_REQUEST));
  }

  // -- DELETE tests --

  @Test
  public void deleteReturns204OnSuccess() throws Exception {
    Map<String, String> tokens = boxFileTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("DELETE");
    when(contentFacet.delete("/myorg/mybox/1.0.0/virtualbox/mybox.box")).thenReturn(true);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NO_CONTENT));
  }

  @Test
  public void deleteReturns404WhenNotFound() throws Exception {
    Map<String, String> tokens = boxFileTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("DELETE");
    when(contentFacet.delete("/myorg/mybox/1.0.0/virtualbox/mybox.box")).thenReturn(false);

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(NOT_FOUND));
  }

  // -- Unsupported method --

  @Test
  public void unsupportedMethodReturns405() throws Exception {
    Map<String, String> tokens = boxFileTokens();
    setTokens(tokens);
    when(request.getAction()).thenReturn("PATCH");

    Response response = underTest.handle(context);
    assertThat(response.getStatus().getCode(), is(METHOD_NOT_ALLOWED));
  }

  // -- Helpers --

  private Map<String, String> boxFileTokens() {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("org", "myorg");
    tokens.put("name", "mybox");
    tokens.put("version", "1.0.0");
    tokens.put("provider", "virtualbox");
    tokens.put("filename", "mybox");
    return tokens;
  }

  private Map<String, String> metadataTokens() {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("org", "myorg");
    tokens.put("name", "mybox");
    return tokens;
  }
}
