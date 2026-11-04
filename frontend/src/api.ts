import { logger } from './logger';
import { accessToken, oidcConfigured } from './auth';

type Envelope<T> = { data: T };
type ErrorResponse = { code?: string; message?: string; requestId?: string; error?: string };
export class ApiError extends Error {
  constructor(message: string, readonly code: string, readonly status?: number, readonly requestId?: string) {
    super(message);
    this.name = 'ApiError';
  }
}

const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  const token = await accessToken();
  if (oidcConfigured && !token) {
    throw new ApiError('Your session has ended. Sign in again to continue.', 'AUTHENTICATION_REQUIRED', 401);
  }
  try {
    response = await fetch(API + path, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options?.headers },
    });
  } catch {
    logger.error('api_network_error', { path });
    throw new ApiError('We could not reach StockPulse. Check your connection and try again.', 'NETWORK_ERROR');
  }
  if (!response.ok) {
    const body = await response.text();
    let parsed: ErrorResponse = {};
    try { parsed = JSON.parse(body) as ErrorResponse; } catch { /* Use the friendly status fallback below. */ }
    const fallback = response.status >= 500
      ? 'StockPulse could not complete that request. Please try again.'
      : 'Please check the information and try again.';
    const error = new ApiError(parsed.message || parsed.error || fallback, parsed.code || 'REQUEST_FAILED', response.status, parsed.requestId);
    logger.warn('api_request_failed', { code: error.code, status: error.status, path, requestId: error.requestId });
    throw error;
  }
  const envelope = await response.json() as Envelope<T>;
  return envelope.data;
}
