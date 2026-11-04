/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "export",
  assetPrefix: "./",
  images: { unoptimized: true },
  poweredByHeader: false,
};

export default nextConfig;
