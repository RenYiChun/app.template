/**
 * useEntityMeta：获取实体元数据
 */

import { ref, type Ref } from 'vue';
import type { EntityMeta } from '../../core';
import type { MetaService } from '../../core';

export function useEntityMeta(
  metaService: MetaService,
  pathSegment: string
): {
  meta: Ref<EntityMeta | null>;
  loading: Ref<boolean>;
  error: Ref<Error | null>;
  refresh: () => Promise<void>;
} {
  const meta = ref<EntityMeta | null>(null);
  const loading = ref(false);
  const error = ref<Error | null>(null);

  const refresh = async () => {
    loading.value = true;
    error.value = null;
    try {
      meta.value = await metaService.getEntity(pathSegment);
    } catch (e) {
      console.error('[useEntityMeta] error fetching meta', e);
      error.value = e instanceof Error ? e : new Error(String(e));
      meta.value = null;
    } finally {
      loading.value = false;
    }
  };

  return { meta, loading, error, refresh };
}
