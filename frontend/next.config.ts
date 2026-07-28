import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,

  /* Fail the production build on type or lint errors rather than shipping
     them. Both default to false in Next, which silently allows broken types
     into a build. */
  typescript: { ignoreBuildErrors: false },
  eslint: { ignoreDuringBuilds: false },
};

export default nextConfig;
