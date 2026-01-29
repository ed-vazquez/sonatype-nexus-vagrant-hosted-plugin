package org.sonatype.nexus.plugins.vagrant.datastore.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.sonatype.nexus.plugins.vagrant.datastore.VagrantContentFacet;
import org.sonatype.nexus.plugins.vagrant.internal.VagrantFormat;
import org.sonatype.nexus.plugins.vagrant.internal.VagrantSecurityFacet;
import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.RecipeSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.Type;
import org.sonatype.nexus.repository.content.browse.BrowseFacet;
import org.sonatype.nexus.repository.content.maintenance.ContentMaintenanceFacet;
import org.sonatype.nexus.repository.content.search.SearchFacet;
import org.sonatype.nexus.repository.http.PartialFetchHandler;
import org.sonatype.nexus.repository.security.SecurityHandler;
import org.sonatype.nexus.repository.types.HostedType;
import org.sonatype.nexus.repository.view.ConfigurableViewFacet;
import org.sonatype.nexus.repository.view.Route;
import org.sonatype.nexus.repository.view.Router;
import org.sonatype.nexus.repository.view.ViewFacet;
import org.sonatype.nexus.repository.view.handlers.ConditionalRequestHandler;
import org.sonatype.nexus.repository.view.handlers.ContentHeadersHandler;
import org.sonatype.nexus.repository.view.handlers.ExceptionHandler;
import org.sonatype.nexus.repository.view.handlers.HandlerContributor;
import org.sonatype.nexus.repository.view.handlers.LastDownloadedHandler;
import org.sonatype.nexus.repository.view.handlers.TimingHandler;
import org.sonatype.nexus.repository.view.matchers.ActionMatcher;
import org.sonatype.nexus.repository.view.matchers.logic.LogicMatchers;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import static org.sonatype.nexus.repository.http.HttpHandlers.notFound;
import static org.sonatype.nexus.repository.http.HttpMethods.*;

@Named(VagrantHostedRecipe.NAME)
@Singleton
public class VagrantHostedRecipe
    extends RecipeSupport
{
  public static final String NAME = "vagrant-hosted";

  @Inject
  Provider<VagrantSecurityFacet> securityFacet;

  @Inject
  Provider<VagrantContentFacet> contentFacet;

  @Inject
  Provider<ConfigurableViewFacet> viewFacet;

  @Inject
  Provider<BrowseFacet> browseFacet;

  @Inject
  Provider<SearchFacet> searchFacet;

  @Inject
  Provider<ContentMaintenanceFacet> maintenanceFacet;

  @Inject
  TimingHandler timingHandler;

  @Inject
  SecurityHandler securityHandler;

  @Inject
  ExceptionHandler exceptionHandler;

  @Inject
  HandlerContributor handlerContributor;

  @Inject
  ConditionalRequestHandler conditionalRequestHandler;

  @Inject
  PartialFetchHandler partialFetchHandler;

  @Inject
  ContentHeadersHandler contentHeadersHandler;

  @Inject
  LastDownloadedHandler lastDownloadedHandler;

  @Inject
  VagrantHostedHandler hostedHandler;

  @Inject
  public VagrantHostedRecipe(
      @Named(HostedType.NAME) final Type type,
      @Named(VagrantFormat.NAME) final Format format)
  {
    super(type, format);
  }

  @Override
  public void apply(final Repository repository) throws Exception {
    repository.attach(securityFacet.get());
    repository.attach(configure(viewFacet.get()));
    repository.attach(contentFacet.get());
    repository.attach(maintenanceFacet.get());
    repository.attach(searchFacet.get());
    repository.attach(browseFacet.get());
  }

  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder();

    // GET /{org}/{name} — metadata
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(new ActionMatcher(GET, HEAD), new TokenMatcher("/{org}/{name}")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(hostedHandler)
        .create());

    // GET /{org}/{name}/{version}/{provider}/{filename}.box — download
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(new ActionMatcher(GET, HEAD),
            new TokenMatcher("/{org}/{name}/{version}/{provider}/{filename}.box")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(lastDownloadedHandler)
        .handler(hostedHandler)
        .create());

    // PUT /{org}/{name}/{version}/{provider}/{filename}.box — upload
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(new ActionMatcher(PUT),
            new TokenMatcher("/{org}/{name}/{version}/{provider}/{filename}.box")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(hostedHandler)
        .create());

    // DELETE /{org}/{name}/{version}/{provider}/{filename}.box — delete
    builder.route(new Route.Builder()
        .matcher(LogicMatchers.and(new ActionMatcher(DELETE),
            new TokenMatcher("/{org}/{name}/{version}/{provider}/{filename}.box")))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(hostedHandler)
        .create());

    builder.defaultHandlers(notFound());
    facet.configure(builder.create());
    return facet;
  }
}
