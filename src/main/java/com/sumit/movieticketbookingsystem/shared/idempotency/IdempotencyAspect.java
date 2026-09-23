package com.sumit.movieticketbookingsystem.shared.idempotency;

import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import com.sumit.movieticketbookingsystem.shared.idempotency.IdempotencyStore.StoredRequest;
import com.sumit.movieticketbookingsystem.shared.idempotency.ReplayedErrorException.SavedError;
import com.sumit.movieticketbookingsystem.shared.user.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Makes {@link Idempotent} endpoints safe to retry. The first request with a key runs and its answer is saved
 * (business errors included); a retry with the same key and body gets that answer back without running again.
 * An unexpected failure forgets the key so the client can simply try again.
 */
@Aspect
@Component
class IdempotencyAspect {

    static final String HEADER = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 80;

    private final IdempotencyStore store;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration retention;

    IdempotencyAspect(IdempotencyStore store, JsonMapper json, Clock clock, BookingProperties properties) {
        this.store = store;
        this.json = json;
        this.clock = clock;
        this.retention = properties.idempotencyRetention();
    }

    @Around("@annotation(com.sumit.movieticketbookingsystem.shared.idempotency.Idempotent)")
    Object handle(ProceedingJoinPoint call) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new ValidationException("An " + HEADER + " header of up to " + MAX_KEY_LENGTH + " characters is required");
        }
        UUID userId = CurrentUser.fromCurrentRequest()
                .orElseThrow(() -> new IllegalStateException("@Idempotent needs a request with a current user"))
                .id();
        Method method = ((MethodSignature) call.getSignature()).getMethod();
        String requestHash = hash(request, method, call.getArgs());
        Instant now = Instant.now(clock);

        if (!store.claim(userId, key, requestHash, now, now.plus(retention))) {
            return replay(store.find(userId, key).orElseThrow(IdempotencyAspect::inProgress), requestHash, method);
        }
        try {
            Object result = call.proceed();
            store.complete(userId, key, successStatus(method), json.writeValueAsString(result));
            return result;
        } catch (DomainException e) {
            store.complete(userId, key, e.code().status().value(), json.writeValueAsString(SavedError.of(e)));
            throw e;
        } catch (Throwable e) {
            store.delete(userId, key);
            throw e;
        }
    }

    private Object replay(StoredRequest stored, String requestHash, Method method) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This " + HEADER + " was already used for a different request");
        }
        if (!stored.completed()) {
            throw inProgress();
        }
        if (stored.responseStatus() >= 400) {
            throw new ReplayedErrorException(json.readValue(stored.responseBody(), SavedError.class));
        }
        return json.readValue(stored.responseBody(), method.getReturnType());
    }

    private static IdempotencyConflictException inProgress() {
        return new IdempotencyConflictException(ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                "A request with this " + HEADER + " is still being processed; try again shortly");
    }

    private String hash(HttpServletRequest request, Method method, Object[] args) {
        StringBuilder fingerprint = new StringBuilder(request.getMethod()).append(' ').append(request.getRequestURI());
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < args.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof RequestBody) {
                    fingerprint.append('\n').append(json.writeValueAsString(args[i]));
                }
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static int successStatus(Method method) {
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
        return status == null ? HttpStatus.OK.value() : status.code().value();
    }
}
