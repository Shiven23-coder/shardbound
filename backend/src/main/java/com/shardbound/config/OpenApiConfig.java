package com.shardbound.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The OpenAPI document the frontend's TypeScript client is generated from (P-07).
 *
 * <p>Java records do not say which fields are optional, so this customizer
 * marks every record component as required unless it is annotated
 * {@code @Nullable} (JSpecify). Null fields are left out of the JSON
 * ({@code default-property-inclusion: non_null}), which matches "optional".
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String SESSION_SCHEME = "session";
    public static final String SESSION_HEADER = "X-Session";
    public static final String PROBLEM_SCHEMA = "Problem";

    private static final List<String> RECORD_PACKAGES = List.of("com.shardbound.view", "com.shardbound.web");

    @Bean
    OpenAPI shardboundOpenApi(ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        String version = properties == null ? "dev" : properties.getVersion();
        return new OpenAPI()
                .info(new Info().title("Shardbound API").version(version)
                        .description("REST API of the Shardbound game server. Every game rule runs on the server."))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(SESSION_HEADER)
                        .description("Game session token returned as sessionId by POST /api/game/new")));
    }

    /** Every operation can fail with an RFC 9457 problem; document it once so the client can type errors. */
    @Bean
    OpenApiCustomizer problemResponses() {
        return openApi -> {
            Schema<?> problem = new ObjectSchema()
                    .addProperty("type", new StringSchema())
                    .addProperty("title", new StringSchema())
                    .addProperty("status", new IntegerSchema())
                    .addProperty("detail", new StringSchema())
                    .addProperty("instance", new StringSchema())
                    .addProperty("code", new StringSchema().description("Stable machine-readable error code"))
                    .addProperty("fields", new MapSchema().additionalProperties(new StringSchema())
                            .description("Per-field messages when code is VALIDATION_FAILED"));
            problem.setRequired(List.of("status", "code"));
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problem);
            Content content = new Content().addMediaType("application/problem+json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)));
            for (PathItem path : openApi.getPaths().values()) {
                for (Operation operation : path.readOperations()) {
                    operation.getResponses()
                            .addApiResponse("4XX", new ApiResponse().description("Client error").content(content))
                            .addApiResponse("5XX", new ApiResponse().description("Server error").content(content));
                }
            }
        };
    }

    @Bean
    OpenApiCustomizer requiredRecordComponents() {
        Map<String, Class<?>> records = recordsBySimpleName();
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            for (Map.Entry<String, Schema> entry : openApi.getComponents().getSchemas().entrySet()) {
                Class<?> type = records.get(entry.getKey());
                if (type != null) {
                    markRequired(entry.getValue(), type);
                }
            }
        };
    }

    private static void markRequired(Schema<?> schema, Class<?> recordType) {
        List<String> required = new ArrayList<>();
        for (RecordComponent component : recordType.getRecordComponents()) {
            boolean nullable = component.getAnnotatedType().isAnnotationPresent(Nullable.class);
            if (!nullable && schema.getProperties() != null && schema.getProperties().containsKey(component.getName())) {
                required.add(component.getName());
            }
        }
        schema.setRequired(required.isEmpty() ? null : required);
    }

    /** Records in the API packages, by simple name (springdoc's schema names). Nested records included. */
    private static Map<String, Class<?>> recordsBySimpleName() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        Map<String, Class<?>> result = new HashMap<>();
        for (String pkg : RECORD_PACKAGES) {
            for (BeanDefinition definition : scanner.findCandidateComponents(pkg)) {
                try {
                    Class<?> type = Class.forName(definition.getBeanClassName());
                    if (type.isRecord()) {
                        result.put(type.getSimpleName(), type);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return result;
    }
}
