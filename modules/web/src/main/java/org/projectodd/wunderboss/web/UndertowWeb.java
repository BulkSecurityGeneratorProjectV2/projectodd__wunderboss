package org.projectodd.wunderboss.web;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.resource.CachingResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.util.Headers;
import org.jboss.logging.Logger;
import org.projectodd.wunderboss.Options;
import org.projectodd.wunderboss.WunderBoss;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.projectodd.wunderboss.web.Web.ComponentOption.HOST;
import static org.projectodd.wunderboss.web.Web.ComponentOption.PORT;
import static org.projectodd.wunderboss.web.Web.RegisterOption.*;

public class UndertowWeb implements Web<Undertow, HttpHandler> {

    public UndertowWeb(String name, Options<ComponentOption> opts) {
        this.name = name;
        configure(opts);
    }

    @Override
    public String name() { return name; }

    @Override
    public void start() {
        // TODO: Configurable non-lazy boot of Undertow
        if (!started) {
            undertow.start();
            log.info("Undertow listening on " + host + ":" + port);
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            undertow.stop();
            log.info("Undertow stopped");
            started = false;
        }
    }

    @Override
    public Undertow implementation() {
        return this.undertow;
    }

    private void configure(Options<ComponentOption> options) {
        port = options.getInt(PORT, 8080);
        host = options.getString(HOST, "localhost");
        undertow = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(Handlers.header(pathHandler, Headers.SERVER_STRING, "undertow"))
                .build();
    }

    public Web registerHandler(HttpHandler httpHandler, Map<RegisterOption, Object> opts) {
        final Options<RegisterOption> options = new Options<>(opts);
        final String context = getContextPath(options);
        if (options.has(STATIC_DIR)) {
            httpHandler = wrapWithStaticHandler(httpHandler, options.getString(STATIC_DIR));
        }
        pathHandler.addPrefixPath(context, httpHandler);
        if (options.has(INIT)) {
            ((Runnable) options.get(INIT)).run();
        }
        epilogue(options, new Runnable() { 
                public void run() { 
                    pathHandler.removePrefixPath(context);
                }});
        start();
        log.info("Started web context " + context);
        return this;
    }

    public Web registerServlet(Servlet servlet, Map<RegisterOption, Object> opts) {
        Options<RegisterOption> options = new Options<>(opts);
        String context = getContextPath(options);
        Class servletClass = servlet.getClass();
        final ServletInfo servletInfo = Servlets.servlet(servletClass.getSimpleName(), 
                                                         servletClass,
                                                         new ImmediateInstanceFactory(servlet))
            .addMapping("/*");

        final DeploymentInfo servletBuilder = Servlets.deployment()
                .setClassLoader(WunderBoss.class.getClassLoader())
                .setContextPath(context)
                .setDeploymentName(context)
                .addServlet(servletInfo);

        final DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();
        try {
            registerHandler(manager.start(), options);
            epilogue(options, new Runnable() { 
                    public void run() { 
                        try {
                            manager.stop();
                            manager.undeploy();
                            Servlets.defaultContainer().removeDeployment(servletBuilder);
                        } catch (ServletException e) {
                            e.printStackTrace();
                        }}});
        } catch (ServletException e) {
            // TODO: something better
            e.printStackTrace();
        }
        return this;
    }

    public Web unregister(String context) {
        Runnable f = contextRegistrar.remove(context);
        if (f != null) {
            f.run();
            log.info("Stopped web context at path " + context);
            return this;
        } else {
            log.warn("No context registered at path " + context);
            return null;
        }
    }

    /**
     * Associate a resource cleanup function with a context path,
     * invoked in the unregister method. The context is obtained from
     * the passed options. If the options contain an entry for a
     * "destroy" function, it will be run as well.
     */
    protected void epilogue(Options<RegisterOption> options, final Runnable cleanup) {
        String context = getContextPath(options);
        if (options.has(DESTROY)) {
            final Runnable destroy = (Runnable) options.get(DESTROY);
            contextRegistrar.put(context, new Runnable() {
                    public void run() {
                        cleanup.run();
                        destroy.run();
                    }});
        } else {
            contextRegistrar.put(context, cleanup);
        }
    }

    protected HttpHandler wrapWithStaticHandler(HttpHandler baseHandler, String path) {
        final ResourceManager resourceManager =
                new CachingResourceManager(1000, 1L, null,
                                           new FileResourceManager(new File(path), 1 * 1024 * 1024), 250);
        final ResourceHandler resourceHandler = new ResourceHandler()
                .setResourceManager(resourceManager)
                .setDirectoryListingEnabled(false);

        return new PredicateHandler(new Predicate() {
                @Override
                public boolean resolve(HttpServerExchange value) {
                    try {
                        return value.getRelativePath().length() > 0 &&
                                !value.getRelativePath().equals("/") &&
                                resourceManager.getResource(value.getRelativePath()) != null;
                    } catch (IOException ex) {
                        return false;
                    }
                }
        }, resourceHandler, baseHandler);
    }

    protected static String getContextPath(Options<RegisterOption> options) {
        // Maybe accept "context" as a key, too?
        return options.getString(CONTEXT_PATH, "/");
    }
    
    private final String name;
    private int port;
    private String host;
    private Undertow undertow;
    private PathHandler pathHandler = new PathHandler();
    private boolean started;
    private Map<String, Runnable> contextRegistrar = new HashMap<>();

    private static final Logger log = Logger.getLogger(Web.class);
}
