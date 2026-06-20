package org.acme.audit;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.reflect.Parameter;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Records an audit line for every {@link Audited} tool invocation.
 *
 * <p>Priority is set below the Quarkus {@code @RolesAllowed} interceptor (which
 * runs at 150), so this interceptor is the outermost wrapper. That lets it
 * observe a {@link ForbiddenException} thrown by the authorization check and
 * record it as a {@code DENY}, then rethrow it unchanged.
 *
 * <p>Arguments are logged with PII redacted: any parameter named {@code email}
 * or {@code name} is masked, so naive auditing does not re-leak the very data the
 * later steps protect. This relies on the {@code -parameters} compiler flag,
 * which is already enabled, to read real parameter names.
 */
@Audited
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE - 100)
public class AuditInterceptor {

    private static final Set<String> SENSITIVE_PARAMS = Set.of("email", "name");

    @Inject
    SecurityIdentity identity;

    @Inject
    AuditLog auditLog;

    @AroundInvoke
    Object audit(InvocationContext context) throws Exception {
        String subject = subject();
        String tool = context.getMethod().getName();
        String args = redactArgs(context);
        try {
            Object result = context.proceed();
            auditLog.record(subject, tool, args, "ALLOW", "ok");
            return result;
        } catch (ForbiddenException | UnauthorizedException e) {
            auditLog.record(subject, tool, args, "DENY", "forbidden");
            throw e;
        } catch (Exception e) {
            auditLog.record(subject, tool, args, "ALLOW", "error");
            throw e;
        }
    }

    private String subject() {
        if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
            return "anonymous";
        }
        return identity.getPrincipal().getName();
    }

    private String redactArgs(InvocationContext context) {
        Parameter[] params = context.getMethod().getParameters();
        Object[] values = context.getParameters();
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].getName();
            Object value = i < values.length ? values[i] : null;
            String shown = SENSITIVE_PARAMS.contains(paramName) ? mask(value) : String.valueOf(value);
            joiner.add(paramName + "=" + shown);
        }
        return joiner.toString();
    }

    private String mask(Object value) {
        if (value == null) {
            return "null";
        }
        String s = value.toString();
        if (s.isEmpty()) {
            return "***";
        }
        String prefix = s.substring(0, 1);
        return s.contains("@") ? prefix + "***@***" : prefix + "***";
    }
}
