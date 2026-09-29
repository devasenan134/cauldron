import type { QueryClient } from '@tanstack/react-query'

/** Plan entries, batches and prep stock change together. */
export function refreshPlan(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: ['plan'] })
  qc.invalidateQueries({ queryKey: ['batches'] })
  qc.invalidateQueries({ queryKey: ['prep-stock'] })
}
