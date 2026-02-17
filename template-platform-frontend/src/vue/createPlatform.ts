/**
 * 创建 platform 实例，供应用入口调用
 */

import { EntityClient } from '../core/index.js';
import { MetaService } from '../core/index.js';
import type { EntityClientConfig } from '../core/index.js';
import type { MetaServiceConfig } from '../core/index.js';

export interface PlatformOptions {
  client?: EntityClientConfig;
  meta?: MetaServiceConfig;
}

let defaultClient: EntityClient | null = null;
let defaultMeta: MetaService | null = null;

export function createPlatform(options: PlatformOptions = {}) {
  defaultClient = new EntityClient(options.client);
  defaultMeta = new MetaService(defaultClient, options.meta);
  return { client: defaultClient, meta: defaultMeta };
}

export function getPlatform() {
  if (!defaultClient || !defaultMeta) {
    throw new Error('Platform 未初始化，请先在应用入口调用 createPlatform()');
  }
  return { client: defaultClient, meta: defaultMeta };
}
