import { afterEach, describe, expect, it, vi } from 'vitest';
import { request } from './api';

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('request', () => {
  it('returns the API envelope data and preserves request headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"data":{"id":"p-1"}}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(request<{ id: string }>('/products/p-1', {
      headers: { Authorization: 'Bearer test-token' },
    })).resolves.toEqual({ id: 'p-1' });

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/products/p-1'), expect.objectContaining({
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer test-token' },
    }));
  });

  it('surfaces the friendly error and logs stable code without sensitive payloads', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'PRODUCT_NOT_FOUND', message: 'We could not find that product.', requestId: 'req-123',
    }), { status: 404 })));
    await expect(request('/products/missing')).rejects.toMatchObject({
      message: 'We could not find that product.', code: 'PRODUCT_NOT_FOUND', status: 404, requestId: 'req-123',
    });
    expect(warn).toHaveBeenCalledWith('[StockPulse] api_request_failed', expect.objectContaining({ code: 'PRODUCT_NOT_FOUND', status: 404, requestId: 'req-123' }));
    expect(warn.mock.calls.flat().join(' ')).not.toContain('Authorization');
  });

  it('shows a friendly message when the API cannot be reached', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(request('/products')).rejects.toMatchObject({ code: 'NETWORK_ERROR', message: expect.stringContaining('could not reach') });
  });
});
