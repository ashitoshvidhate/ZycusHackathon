import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';

const authority = import.meta.env.VITE_OIDC_AUTHORITY as string | undefined;
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID as string | undefined;
export const oidcConfigured = Boolean(authority && clientId);

// This is a public browser client: no client secret is accepted or embedded in the bundle.
export const userManager = oidcConfigured ? new UserManager({
  authority: authority!,
  client_id: clientId!,
  redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URI || `${window.location.origin}/auth/callback`,
  post_logout_redirect_uri: import.meta.env.VITE_OIDC_LOGOUT_REDIRECT_URI || window.location.origin,
  response_type: 'code', // oidc-client-ts uses Authorization Code + PKCE for browser clients.
  scope: import.meta.env.VITE_OIDC_SCOPE || 'openid profile stockpulse.read inventory.write suggestions.write suggestions.review strategy.admin',
  automaticSilentRenew: true,
  monitorSession: false,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
}) : null;

let initialization: Promise<User | null> | undefined;
export function initializeOidc(): Promise<User | null> {
  if (!userManager) return Promise.resolve(null);
  initialization ??= (async () => {
    if (window.location.pathname === '/auth/callback') {
      await userManager.signinRedirectCallback();
      window.history.replaceState({}, document.title, '/');
    }
    return userManager.getUser();
  })();
  return initialization;
}

export async function accessToken(): Promise<string | null> {
  if (!userManager) return null;
  const user = await userManager.getUser();
  return user && !user.expired ? user.access_token : null;
}
