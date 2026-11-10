# HTTP API errors

Project-owned REST APIs use [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html), serialized as `application/problem+json`. This policy applies across the monorepo; independent repositories adopt it through their own changes. Identity management and Ecommerce resource-server errors implement the policy. Other service skeletons adopt it when their APIs are introduced.

The machine-readable definitions are in [common/problem.yaml](../../contracts/openapi/common/problem.yaml). Service specifications reference those definitions rather than copying them. Sharing schemas does not require sharing Java exceptions or introducing a shared runtime library. OAuth2/OIDC protocol endpoints keep their protocol-defined errors and redirects; browser login pages and operational CLI output are outside this REST error format.

## Response fields

| Field | Project convention |
| --- | --- |
| `type` | Stable URI: `urn:ecommerce:problem:` followed by the lowercase, hyphenated `code`. |
| `title` | Short human-readable summary; not a client decision key. |
| `status` | Integer matching the actual HTTP response status. |
| `detail` | Safe human-readable string. Never change it to an object. |
| `code` | Stable uppercase identifier used by consumers for behavior and translation. |
| `instance` | Optional request path, without query parameters or fragment. |
| `errors` | Optional structured validation errors; do not force operation-wide conflicts into a field. |

The first five fields are required by this project's contract. Clients tolerate additional members and unknown codes, falling back to the status category and a generic message. Human-readable text may evolve; changing code meaning, field types or status semantics requires consumer compatibility review. Frontends must not parse `detail` or render server messages as HTML.

```json
{
  "type": "urn:ecommerce:problem:validation-failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "Some fields are invalid.",
  "code": "VALIDATION_FAILED",
  "errors": [
    {
      "location": "body",
      "pointer": "/items/0/quantity",
      "code": "MUST_BE_POSITIVE",
      "message": "Quantity must be greater than zero."
    }
  ]
}
```

This nested-body example illustrates the shared format, not an existing Identity endpoint. Body errors use JSON Pointer (`~` becomes `~0`, `/` becomes `~1`); indexes identify array items. Errors on an unordered collection may identify the collection itself. Query, path and header errors instead use `location` plus `name`, such as `{"location":"query","name":"page","code":"INVALID_VALUE","message":"The value is invalid."}`. Return multiple independently detected validation failures when practical; do not promise exhaustive validation after parsing or authorization fails. Malformed JSON or an unparseable parameter may return a generic `BAD_REQUEST` without `errors`.

## Status and code ownership

| Status | Shared meaning / codes |
| --- | --- |
| 400 | `VALIDATION_FAILED` for structured validation; `BAD_REQUEST` for malformed input; service-specific invalid business input codes where applicable. |
| 401 | `UNAUTHENTICATED`; include the Bearer `WWW-Authenticate` challenge. |
| 403 | `FORBIDDEN`; authenticated identity cannot perform the operation. |
| 404 | `NOT_FOUND` or the owning service's resource-specific code. |
| 409 | A uniqueness, invariant or state conflict, with an owning service code. |
| 405 / 406 / 415 | `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`; preserve applicable protocol headers such as `Allow`. |
| 500 | `INTERNAL_ERROR`; return a generic description. |

Other framework failures keep their appropriate HTTP status with `REQUEST_REJECTED` for other 4xx statuses or `INTERNAL_ERROR` for unexpected 5xx statuses. Do not turn all exceptions into 400 or return HTTP 200 for errors.

Identity's current business codes are `INVALID_ADMINISTRATOR` (400), `ADMINISTRATOR_NOT_FOUND` (404), `DUPLICATE_ADMINISTRATOR` (409), and `LAST_PRIVILEGED_ADMINISTRATOR` (409). Actor authorization failures use `FORBIDDEN`. Bean validation currently uses `REQUIRED`, `INVALID_SIZE` and `INVALID_VALUE` for field codes. Business validation performed by a use case can still produce an operation-level error without field details.

## Implementation and security boundaries

The approved [backend organization](../06-development/code-organization.md) places service-wide HTTP advice/error handlers and Problem Details writers in each service's `shared/error`; feature-specific transport handling stays in its adapter. `shared/error` is a service-local HTTP boundary, not a cross-service runtime library or a home for business exceptions. This placement changes no response schema, status, security-filter behavior or OAuth2 protocol errors.

Use `@RestControllerAdvice` in HTTP adapters to translate application/domain errors and MVC failures. Exception types remain independent of HTTP status and response models. Controllers should not repeat catches just to perform status mapping; see [engineering conventions](../06-development/conventions.md).

Security-filter authentication/authorization failures use their corresponding entry point or access-denied handler, producing the same REST envelope. OAuth2/OIDC errors remain with the protocol implementation. Keep REST advice scoped so a generic handler cannot intercept and rewrite OAuth responses. Preserve error status and required headers across a future BFF; browser integration must be verified when that BFF is implemented.

Never expose stack traces, Java exception names, SQL, credentials, tokens, rejected field values or parser diagnostics. Use static safe descriptions for framework failures. Application exception messages exposed to clients must be intentionally safe and must not interpolate sensitive or untrusted values. Keep operational diagnostics server-side with appropriate redaction; the current envelope does not promise a trace ID or distributed tracing support.

Credential DTOs must redact their string representation. Identity and Ecommerce keep MVC request-body and exception-resolver diagnostics at INFO, since DEBUG/TRACE can print raw bodies or rejected values. Do not enable those diagnostics on real sensitive traffic; a safe response body alone does not redact framework logs.

Use `Cache-Control: no-store` on these error responses. Do not include query strings in `instance`, since they may contain sensitive input. Verify both response shape and lack of sensitive data, including malformed requests and unexpected exceptions.

## Compatibility and rollout

Consumers and tests must use `application/problem+json`, `status` and `code`; field rendering uses `errors` when present. This introduces a defined REST error body where Identity previously relied on framework defaults, and corrects its update-not-found contract to 404. Successful payloads, token claims, transaction rules and OAuth errors are unchanged. Backoffice has no integrated API client yet; verify schema references/client generation when that integration is introduced.
