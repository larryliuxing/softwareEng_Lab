import { useStore } from '../store'

export function Toast() {
  const { toast, dismissToast } = useStore()
  if (!toast) return null
  return (
    <button type="button" className="toast" onClick={dismissToast}>
      {toast.message}
    </button>
  )
}
