enum AppShellState: Equatable {
    case loading
    case content
    case offline
    case serverError
    case tlsError
    case maintenance
    case requiredUpdate
    case rendererUnavailable(loopBlocked: Bool)
}
