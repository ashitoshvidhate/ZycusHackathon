import React from 'react';
import { createRoot } from 'react-dom/client';
import { request } from './api';
import { ApiError } from './api';
import { logger } from './logger';
import { initializeOidc, oidcConfigured, userManager } from './auth';
import type { User } from 'oidc-client-ts';
import './style.css';

type Product = {
  id: string;
  sku: string;
  name: string;
  category: string;
  currentPrice: number;
  stockLevel: number;
  reorderThreshold: number;
  demandVelocity: number;
  status: string;
};

type Suggestion = {
  id: string;
  productId: string;
  type: string;
  triggerReason: string;
  status: string;
  recommendedPrice: number | null;
  recommendedQuantity: number | null;
  confidence: number;
  reasoning: string;
  strategyName: string;
  createdAt: string;
  reviewedBy: string | null;
};

function App() {
  const [products, setProducts] = React.useState<Product[]>([]);
  const [suggestions, setSuggestions] = React.useState<Suggestion[]>([]);
  const [busy, setBusy] = React.useState(false);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState('');
  const [user, setUser] = React.useState<User | null>(null);
  const [authReady, setAuthReady] = React.useState(false);
  const [authError, setAuthError] = React.useState('');

  React.useEffect(() => {
    let mounted = true;
    const onUserLoaded = (loadedUser: User) => { if (mounted) setUser(loadedUser); };
    const onUserUnloaded = () => { if (mounted) setUser(null); };
    userManager?.events.addUserLoaded(onUserLoaded);
    userManager?.events.addUserUnloaded(onUserUnloaded);
    userManager?.events.addAccessTokenExpired(onUserUnloaded);
    void initializeOidc().then((loadedUser) => {
      if (mounted) { setUser(loadedUser); setAuthReady(true); }
    }).catch(() => {
      if (mounted) { setAuthError('Sign-in could not be completed. Please try again.'); setAuthReady(true); }
    });
    return () => {
      mounted = false;
      userManager?.events.removeUserLoaded(onUserLoaded);
      userManager?.events.removeUserUnloaded(onUserUnloaded);
      userManager?.events.removeAccessTokenExpired(onUserUnloaded);
    };
  }, []);

  const load = React.useCallback(async () => {
    try {
      const items = await request<Product[]>('/products');
      const suggestionLists = await Promise.all(
        items.map((item) => request<Suggestion[]>(`/products/${item.id}/suggestions`)),
      );
      setProducts(items);
      setSuggestions(suggestionLists.flat());
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'We could not load your inventory. Try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    if (!user) return undefined;
    void load();
    const timer = window.setInterval(() => void load(), 5000);
    return () => window.clearInterval(timer);
  }, [load, user]);

  async function perform(action: () => Promise<unknown>) {
    setBusy(true);
    try {
      await action();
      logger.info('user_action_succeeded');
      await load();
    } catch (cause) {
      logger.warn('user_action_failed', cause instanceof ApiError ? { code: cause.code, status: cause.status, requestId: cause.requestId } : {});
      setError(cause instanceof Error ? cause.message : 'We could not complete that action. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  async function sale(productId: string) {
    await perform(() => request(`/products/${productId}/orders`, {
      method: 'POST',
      body: JSON.stringify({ quantity: 1 }),
    }));
  }

  async function suggest(productId: string) {
    await perform(async () => {
      await Promise.all([
        request(`/products/${productId}/suggest-pricing`, { method: 'POST' }),
        request(`/products/${productId}/suggest-reorder`, { method: 'POST' }),
      ]);
    });
  }

  async function review(suggestion: Suggestion, decision: 'ACCEPT' | 'REJECT') {
    const endpoint = suggestion.type === 'PRICING' ? 'pricing-suggestions' : 'reorder-suggestions';
    await perform(() => request(`/${endpoint}/${suggestion.id}`, { method: 'PATCH', body: JSON.stringify({ decision }) }));
  }

  if (!oidcConfigured) return <main><section className="auth-panel"><p className="eyebrow">SECURE ACCESS REQUIRED</p><h1>Stock<span>Pulse</span></h1><p>Configure your organization's OIDC issuer and public client ID to sign in.</p><small>Set VITE_OIDC_AUTHORITY, VITE_OIDC_CLIENT_ID, and register this app's callback URL with your identity provider.</small></section></main>;
  if (!authReady) return <main><p role="status">Checking your secure session…</p></main>;
  if (authError) return <main><div className="error" role="alert">{authError} <button onClick={() => void userManager?.signinRedirect()}>Try sign in</button></div></main>;
  if (!user) return <main><section className="auth-panel"><p className="eyebrow">SECURE ACCESS</p><h1>Stock<span>Pulse</span></h1><p>Sign in with your organization's identity provider to access inventory and recommendations.</p><button onClick={() => void userManager?.signinRedirect()}>Sign in</button></section></main>;

  return (
    <main>
      <header>
        <p className="eyebrow">SHOPSTREAM / MERCHANDISING CONSOLE</p>
        <h1>Stock<span>Pulse</span></h1>
        <p className="subtitle">Inventory signals, recommendations, human approval.</p>
        <div className="session-bar"><span>Signed in as {String(user.profile.preferred_username || user.profile.email || user.profile.sub)}</span><button onClick={() => void userManager?.signoutRedirect()}>Sign out</button></div>
      </header>
      <section className="summary" aria-label="Inventory summary">
        <div><b>{products.length}</b><small>CATALOG ITEMS</small></div>
        <div><b>{products.filter((p) => p.stockLevel < p.reorderThreshold).length}</b><small>LOW INVENTORY</small></div>
        <div><b>{suggestions.filter((s) => s.status === 'PENDING').length}</b><small>PENDING REVIEWS</small></div>
      </section>
      {error && <div className="error" role="alert"><span>{error}</span> <button onClick={() => void load()}>Retry</button></div>}
      <div className="toolbar">
        <h2>Product signals</h2>
        <button onClick={() => void load()} disabled={busy}>Refresh</button>
      </div>
      {loading && <p role="status">Loading inventory…</p>}
      {!loading && products.length === 0 && !error && <p>No products are available yet.</p>}
      <div className="grid">
        {products.map((product) => {
          const productSuggestions = suggestions.filter((item) => item.productId === product.id);
          const lowStock = product.stockLevel < product.reorderThreshold;
          return (
            <article key={product.id} className={lowStock ? 'alert' : ''}>
              <div className="cardtop">
                <span className="sku">{product.sku}</span>
                <span className={`badge ${lowStock ? 'orange' : 'green'}`}>
                  {lowStock ? 'INVENTORY LOW' : product.status}
                </span>
              </div>
              <h3>{product.name}</h3>
              <p className="category">{product.category}</p>
              <div className="metrics">
                <div><small>PRICE</small><strong>${product.currentPrice.toFixed(2)}</strong></div>
                <div><small>STOCK</small><strong>{product.stockLevel} <em>/ {product.reorderThreshold}</em></strong></div>
                <div><small>VELOCITY</small><strong>{product.demandVelocity}</strong></div>
              </div>
              <button className="sale" disabled={busy || product.stockLevel === 0} onClick={() => void sale(product.id)}>
                Simulate sale
              </button>
              <button className="secondary-action" disabled={busy} onClick={() => void suggest(product.id)}>
                Request pricing + reorder suggestions
              </button>
              {productSuggestions.length > 0 && (
                <section className="suggestions" aria-label={`Recommendations for ${product.name}`}>
                  <h4>Recommendations</h4>
                  {productSuggestions.map((item) => (
                    <div className="suggestion" key={item.id}>
                      <div className="cardtop">
                        <span className={`badge ${item.type === 'PRICING' ? 'blue' : 'green'}`}>{item.type === 'PRICING' ? 'PRICING REVIEW' : 'REORDER REVIEW'}</span>
                        <span className="badge orange">{item.triggerReason.replace(/_/g, ' ')}</span>
                        <span className="sku">{item.status}</span>
                      </div>
                      <p>{item.type === 'PRICING' ? `Price: $${item.recommendedPrice?.toFixed(2)}` : `Reorder quantity: ${item.recommendedQuantity}`}</p>
                      <small>{item.reasoning} ({item.strategyName}, confidence {(item.confidence * 100).toFixed(0)}%)</small>
                      {item.status === 'PENDING' && (
                        <div className="review-actions">
                          <button disabled={busy} onClick={() => void review(item, 'ACCEPT')}>Accept</button>
                          <button disabled={busy} onClick={() => void review(item, 'REJECT')}>Reject</button>
                        </div>
                      )}
                    </div>
                  ))}
                </section>
              )}
            </article>
          );
        })}
      </div>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
