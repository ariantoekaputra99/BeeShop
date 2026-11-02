/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  /** local | sit | uat | prod */
  readonly VITE_APP_ENV?: string;
}
interface ImportMeta { readonly env: ImportMetaEnv }
