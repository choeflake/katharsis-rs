package io.katharsis.rs;

import io.katharsis.dispatcher.RequestDispatcher;
import io.katharsis.dispatcher.registry.ControllerRegistry;
import io.katharsis.dispatcher.registry.ControllerRegistryBuilder;
import io.katharsis.errorhandling.mapper.DefaultExceptionMapperLookup;
import io.katharsis.errorhandling.mapper.ExceptionMapperLookup;
import io.katharsis.errorhandling.mapper.ExceptionMapperRegistry;
import io.katharsis.errorhandling.mapper.ExceptionMapperRegistryBuilder;
import io.katharsis.jackson.JsonApiModuleBuilder;
import io.katharsis.locator.JsonServiceLocator;
import io.katharsis.resource.field.ResourceFieldNameTransformer;
import io.katharsis.resource.information.ResourceInformationBuilder;
import io.katharsis.resource.registry.DefaultResourceLookup;
import io.katharsis.resource.registry.ResourceLookup;
import io.katharsis.resource.registry.ResourceRegistry;
import io.katharsis.resource.registry.ResourceRegistryBuilder;
import io.katharsis.utils.parser.TypeParser;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Basic Katharsis feature that initializes core classes and provides a starting point to use the framework in
 * another projects.
 * <p>
 * This feature has NO {@link Provider} annotation, thus it require to provide an instance of  {@link ObjectMapper} and
 * {@link JsonServiceLocator} to provide instances of resources.
 */
@ConstrainedTo(RuntimeType.SERVER)
public class KatharsisFeature implements Feature {

    private final JsonServiceLocator jsonServiceLocator;
    private final ObjectMapper objectMapper;

    public KatharsisFeature(ObjectMapper objectMapper, JsonServiceLocator jsonServiceLocator) {
        this.objectMapper = objectMapper;
        this.jsonServiceLocator = jsonServiceLocator;
    }
    
    public ResourceLookup createResourceLookup(FeatureContext context) {
        String resourceSearchPackage = (String) context
                .getConfiguration()
                .getProperty(KatharsisProperties.RESOURCE_SEARCH_PACKAGE);
    	
        return new DefaultResourceLookup(resourceSearchPackage);
    }
    
    public ExceptionMapperLookup createExceptionMapperLookup(FeatureContext context) {
        String resourceSearchPackage = (String) context
                .getConfiguration()
                .getProperty(KatharsisProperties.RESOURCE_SEARCH_PACKAGE);
    	
        return new DefaultExceptionMapperLookup(resourceSearchPackage);
    }

    @Override
    public boolean configure(FeatureContext context) {
        String resourceDefaultDomain = (String) context
            .getConfiguration()
            .getProperty(KatharsisProperties.RESOURCE_DEFAULT_DOMAIN);
        String webPathPrefix = (String) context
            .getConfiguration()
            .getProperty(KatharsisProperties.WEB_PATH_PREFIX);

        String serviceUrl = buildServiceUrl(resourceDefaultDomain, webPathPrefix);
        ResourceLookup resourceLookup = createResourceLookup(context);
        ResourceRegistry resourceRegistry = buildResourceRegistry(resourceLookup, serviceUrl);

        JsonApiModuleBuilder jsonApiModuleBuilder = new JsonApiModuleBuilder();
        objectMapper.registerModule(jsonApiModuleBuilder.build(resourceRegistry));

        KatharsisFilter katharsisFilter;
        try {
        	ExceptionMapperLookup exceptionMapperLookup = createExceptionMapperLookup(context);
            ExceptionMapperRegistry exceptionMapperRegistry = buildExceptionMapperRegistry(exceptionMapperLookup);
            katharsisFilter = createKatharsisFilter(resourceRegistry, exceptionMapperRegistry, webPathPrefix);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
        context.register(katharsisFilter);

        return true;
    }

    private String buildServiceUrl(String resourceDefaultDomain, String webPathPrefix) {
        return resourceDefaultDomain + (webPathPrefix != null ? webPathPrefix : "");
    }

    private ExceptionMapperRegistry buildExceptionMapperRegistry(ExceptionMapperLookup exceptionMapperLookup) throws Exception {
        ExceptionMapperRegistryBuilder mapperRegistryBuilder = new ExceptionMapperRegistryBuilder();
        return mapperRegistryBuilder.build(exceptionMapperLookup);
    }

    private ResourceRegistry buildResourceRegistry(ResourceLookup lookup, String serviceUrl) {
        ResourceRegistryBuilder registryBuilder = new ResourceRegistryBuilder(jsonServiceLocator,
            new ResourceInformationBuilder(new ResourceFieldNameTransformer()));
        return registryBuilder.build(lookup, serviceUrl);
    }

    private KatharsisFilter createKatharsisFilter(ResourceRegistry resourceRegistry,
        ExceptionMapperRegistry exceptionMapperRegistry, String webPathPrefix) throws Exception {
        RequestDispatcher requestDispatcher = createRequestDispatcher(resourceRegistry, exceptionMapperRegistry);

        return new KatharsisFilter(objectMapper, resourceRegistry, requestDispatcher, webPathPrefix);
    }

    private RequestDispatcher createRequestDispatcher(ResourceRegistry resourceRegistry,
        ExceptionMapperRegistry exceptionMapperRegistry) throws Exception {
        TypeParser typeParser = new TypeParser();
        ControllerRegistryBuilder controllerRegistryBuilder = new ControllerRegistryBuilder(resourceRegistry,
            typeParser, objectMapper);
        ControllerRegistry controllerRegistry = controllerRegistryBuilder.build();
        return new RequestDispatcher(controllerRegistry, exceptionMapperRegistry);
    }
}
