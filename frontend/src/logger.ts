type LogContext = { code?: string; status?: number; path?: string; requestId?: string };

/** Small structured client logger. Never pass request bodies, tokens, cookies, or model output. */
export const logger = {
  info(event: string, context: LogContext = {}) {
    if (import.meta.env.DEV) console.info(`[StockPulse] ${event}`, context);
  },
  warn(event: string, context: LogContext = {}) {
    console.warn(`[StockPulse] ${event}`, context);
  },
  error(event: string, context: LogContext = {}) {
    console.error(`[StockPulse] ${event}`, context);
  },
};
