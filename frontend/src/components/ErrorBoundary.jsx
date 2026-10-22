import { Component } from 'react'
import { AlertTriangle, RotateCcw } from 'lucide-react'

/**
 * Локальный Error Boundary: падение встроенного блока (Mindmap/To-do)
 * не должно валить всю заметку. Показываем восстановимый элемент.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('Ошибка блока:', this.props.name, error, info)
  }

  handleReset = () => {
    this.setState({ hasError: false })
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="p-3 rounded border border-vault-danger/40 bg-vault-danger/5 text-sm">
          <div className="flex items-center gap-2 text-vault-danger mb-1">
            <AlertTriangle size={14} />
            <span className="font-medium">Блок «{this.props.name}» недоступен</span>
          </div>
          <p className="text-vault-muted mb-2">Остальная часть заметки работает как обычно.</p>
          <button
            onClick={this.handleReset}
            className="px-2.5 py-1 rounded border border-vault-border text-vault-text text-xs hover:bg-vault-surface-2 flex items-center gap-1"
          >
            <RotateCcw size={12} /> Повторить
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
