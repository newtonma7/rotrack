export type ApiFieldErrors = Record<string, string>;

export class ApiRequestError extends Error {
  readonly code: string;
  readonly status: number;
  readonly fieldErrors: ApiFieldErrors;
  retryAfterMs: number | null;

  constructor(
    message: string,
    status: number,
    code: string,
    fieldErrors: ApiFieldErrors = {},
    retryAfterMs: number | null = null
  ) {
    super(message);
    this.name = "ApiRequestError";
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.retryAfterMs = retryAfterMs;
  }
}

type ErrorResponseBody = {
  error?: {
    code?: unknown;
    message?: unknown;
    fieldErrors?: unknown;
  } | string;
};

export function parseApiErrorResponse(
  body: unknown,
  status: number
): ApiRequestError {
  const error = (body as ErrorResponseBody | null)?.error;

  if (typeof error === "object" && error !== null) {
    const fieldErrors =
      typeof error.fieldErrors === "object" && error.fieldErrors !== null
        ? Object.fromEntries(
            Object.entries(error.fieldErrors).filter(
              ([, value]) => typeof value === "string"
            )
          )
        : {};

    return new ApiRequestError(
      typeof error.message === "string" ? error.message : `Request failed (${status})`,
      status,
      typeof error.code === "string" ? error.code : "REQUEST_FAILED",
      fieldErrors
    );
  }

  return new ApiRequestError(
    typeof error === "string" ? error : `Request failed (${status})`,
    status,
    "REQUEST_FAILED"
  );
}
