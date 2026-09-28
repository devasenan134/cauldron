import type { QueryClient } from '@tanstack/react-query'

/** Plan entries and batches change together. */
export function refreshPlan(qc: QueryClient) {
  qc.invalidateQueries({ queryKey: ['plan'] })
  qc.invalidateQueries({ queryKey: ['batches'] })
}
