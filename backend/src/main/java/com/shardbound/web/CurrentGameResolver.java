package com.shardbound.web;

import com.shardbound.config.OpenApiConfig;
import com.shardbound.engine.GameState;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CurrentGame} parameters from the {@code X-Session} header. */
@Component
public class CurrentGameResolver implements HandlerMethodArgumentResolver {

    private final GameSessions sessions;

    public CurrentGameResolver(GameSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentGame.class)
                && GameState.class.equals(parameter.getParameterType());
    }

    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mav,
                                            NativeWebRequest request, @Nullable WebDataBinderFactory binders) {
        String token = request.getHeader(OpenApiConfig.SESSION_HEADER);
        CurrentGame annotation = parameter.getParameterAnnotation(CurrentGame.class);
        if (annotation != null && !annotation.required()) {
            return sessions.find(token).orElse(null);
        }
        return sessions.require(token);
    }
}
