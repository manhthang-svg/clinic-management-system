import { useEffect, useState } from 'react';

const COMPACT_SIDEBAR_QUERY = '(max-width: 1023px)';

function compactViewport() {
  if (typeof window === 'undefined') return false;
  return typeof window.matchMedia === 'function'
    ? window.matchMedia(COMPACT_SIDEBAR_QUERY).matches
    : window.innerWidth < 1024;
}

export function useResponsiveSidebar() {
  const [collapsed, setCollapsed] = useState(compactViewport);

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return undefined;
    const media = window.matchMedia(COMPACT_SIDEBAR_QUERY);
    const syncWithViewport = (event) => setCollapsed(event.matches);
    media.addEventListener('change', syncWithViewport);
    return () => media.removeEventListener('change', syncWithViewport);
  }, []);

  return {
    collapsed,
    setCollapsed,
    toggleCollapsed: () => setCollapsed((current) => !current),
  };
}

export default useResponsiveSidebar;
