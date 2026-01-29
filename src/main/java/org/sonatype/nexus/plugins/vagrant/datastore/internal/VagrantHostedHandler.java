package org.sonatype.nexus.plugins.vagrant.datastore.internal;

import java.io.IOException;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.plugins.vagrant.datastore.VagrantContentFacet;
import org.sonatype.nexus.plugins.vagrant.internal.VagrantMetadataBuilder;
import org.sonatype.nexus.plugins.vagrant.model.VagrantBoxMetadata;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.payloads.StringPayload;

import java.util.Map;

import static org.sonatype.nexus.repository.http.HttpMethods.*;

@Named
@Singleton
public class VagrantHostedHandler
    extends ComponentSupport
    implements Handler
{
  private final VagrantMetadataBuilder metadataBuilder;

  @Inject
  public VagrantHostedHandler(final VagrantMetadataBuilder metadataBuilder) {
    this.metadataBuilder = metadataBuilder;
  }

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    String method = context.getRequest().getAction();
    Map<String, String> tokens = context.getAttributes().require(TokenMatcher.State.class).getTokens();

    String org = tokens.get("org");
    String name = tokens.get("name");

    VagrantContentFacet contentFacet = context.getRepository().facet(VagrantContentFacet.class);

    switch (method) {
      case GET:
      case HEAD:
        return handleGet(context, contentFacet, tokens, org, name);
      case PUT:
        return handlePut(context, contentFacet, tokens, org, name);
      case DELETE:
        return handleDelete(contentFacet, tokens);
      default:
        return HttpResponses.methodNotAllowed(method, GET, PUT, DELETE);
    }
  }

  private Response handleGet(final Context context, final VagrantContentFacet contentFacet,
                             final Map<String, String> tokens, final String org, final String name)
      throws Exception
  {
    if (isBoxFileRequest(tokens)) {
      String path = buildAssetPath(tokens);
      Optional<Content> content = contentFacet.get(path);
      return content.map(HttpResponses::ok).orElseGet(HttpResponses::notFound);
    }

    // Metadata request — build dynamically
    String baseUrl = context.getRepository().getUrl();
    Iterable<FluentAsset> assets = contentFacet.getBoxAssets(org, name);
    VagrantBoxMetadata metadata = metadataBuilder.buildMetadata(baseUrl, org, name, assets);

    if (metadata.getVersions().isEmpty()) {
      return HttpResponses.notFound();
    }

    String json = metadataBuilder.toJson(metadata);
    return HttpResponses.ok(new Content(new StringPayload(json, "application/json")));
  }

  private Response handlePut(final Context context, final VagrantContentFacet contentFacet,
                             final Map<String, String> tokens, final String org, final String name)
      throws IOException
  {
    String version = tokens.get("version");
    String provider = tokens.get("provider");
    String path = buildAssetPath(tokens);

    Payload payload = context.getRequest().getPayload();
    if (payload == null) {
      return HttpResponses.badRequest("Request body is required");
    }

    contentFacet.put(path, payload, org, name, version, provider);
    return HttpResponses.created();
  }

  private Response handleDelete(final VagrantContentFacet contentFacet,
                                final Map<String, String> tokens)
  {
    String path = buildAssetPath(tokens);
    boolean deleted = contentFacet.delete(path);
    return deleted ? HttpResponses.noContent() : HttpResponses.notFound();
  }

  private boolean isBoxFileRequest(final Map<String, String> tokens) {
    return tokens.containsKey("version") && tokens.containsKey("provider") && tokens.containsKey("filename");
  }

  private String buildAssetPath(final Map<String, String> tokens) {
    return String.format("/%s/%s/%s/%s/%s.box",
        tokens.get("org"),
        tokens.get("name"),
        tokens.get("version"),
        tokens.get("provider"),
        tokens.get("filename"));
  }
}
