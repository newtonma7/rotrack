export const BEFORE_APP_NAVIGATION = "rotrack:before-app-navigation";

type GuardedNavigationDetail = { proceed: () => void };

export function requestAppNavigation(proceed: () => void): void {
  const event = new CustomEvent<GuardedNavigationDetail>(BEFORE_APP_NAVIGATION, {
    cancelable: true,
    detail: { proceed },
  });
  if (window.dispatchEvent(event)) proceed();
}

export function guardedNavigationProceed(event: Event): (() => void) | null {
  return (event as CustomEvent<GuardedNavigationDetail>).detail?.proceed ?? null;
}
