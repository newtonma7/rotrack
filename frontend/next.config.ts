import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Keep browser source maps out of production artifacts; local debugging and
  // server-side error tooling do not require shipping application source.
  productionBrowserSourceMaps: false,
};

export default nextConfig;
